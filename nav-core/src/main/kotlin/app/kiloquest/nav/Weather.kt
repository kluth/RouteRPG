package app.kiloquest.nav

import java.time.LocalDateTime
import java.time.ZoneOffset
import java.util.Locale
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject

enum class WeatherCondition { CLEAR, CLOUDY, FOG, DRIZZLE, RAIN, SNOW, SHOWERS, STORM, UNKNOWN }

enum class WeatherHazard { HEAVY_RAIN, SNOW, ICE, STORM, FOG, WIND, FROST }

/** Wetter an einem Punkt der Route zur geschaetzten Ankunftszeit dort. */
data class WeatherPoint(
    val alongMeters: Double,
    val position: LatLon,
    val etaEpochSec: Long,
    val tempC: Double?,
    val precipMm: Double?,
    val code: Int?,
    val gustKmh: Double?,
    val condition: WeatherCondition,
    val hazards: Set<WeatherHazard>,
)

/** Zusammenfassung fuer die Anzeige. [arrivesInDark] ist null, wenn Sonnenzeiten fehlen (z. B. Polarnacht). */
data class WeatherSummary(
    val points: List<WeatherPoint>,
    val destination: WeatherPoint?,
    /** Gefahren voraus (nach Streckenposition), je Gefahr nur der naechste Punkt. */
    val hazardsAhead: List<Pair<WeatherHazard, Double>>,
    val arrivesInDark: Boolean?,
    val sunsetEpochSec: Long?,
)

data class WeatherSample(val alongMeters: Double, val position: LatLon, val etaEpochSec: Long)

/** Open-Meteo (frei, ohne Schluessel, nicht kommerziell): Vorhersage entlang der Route. Kein Netzwerkcode. */
object WeatherApi {
    private val json = Json { ignoreUnknownKeys = true }

    /** Stuetzpunkte etwa alle [stepMeters] (mind. 2, hoechstens [maxPoints]) inklusive Ziel. */
    fun samples(
        route: Route,
        fromMeters: Double,
        nowEpochSec: Long,
        stepMeters: Double = 75_000.0,
        maxPoints: Int = 12,
    ): List<WeatherSample> {
        val total = route.line.lengthMeters
        val remaining = (total - fromMeters).coerceAtLeast(0.0)
        val count = (remaining / stepMeters).toInt().coerceIn(1, maxPoints - 1)
        val step = if (count > 0) remaining / count else 0.0
        return (0..count).map { i ->
            val along = (fromMeters + step * i).coerceAtMost(total)
            val frac = if (remaining == 0.0) 0.0 else (along - fromMeters) / remaining
            val remainingSeconds = route.durationSeconds * (remaining / total.coerceAtLeast(1.0))
            WeatherSample(along, route.line.points[route.line.indexAtMeters(along)], nowEpochSec + (remainingSeconds * frac).toLong())
        }
    }

    fun buildUrl(samples: List<WeatherSample>): String {
        fun f(d: Double) = String.format(Locale.ROOT, "%.3f", d)
        val lat = samples.joinToString(",") { f(it.position.lat) }
        val lon = samples.joinToString(",") { f(it.position.lon) }
        return "https://api.open-meteo.com/v1/forecast?latitude=$lat&longitude=$lon" +
            "&hourly=temperature_2m,precipitation,weather_code,wind_gusts_10m&daily=sunrise,sunset" +
            "&timezone=GMT&forecast_days=7&wind_speed_unit=kmh"
    }

    /** Antwort ist bei mehreren Orten ein Array, bei einem Ort ein Objekt. Zeiten in GMT. */
    fun parse(body: String, samples: List<WeatherSample>): WeatherSummary {
        val root = json.parseToJsonElement(body)
        val objs: List<JsonObject> = when (root) {
            is JsonArray -> root.map { it.jsonObject }
            is JsonObject -> listOf(root)
            else -> emptyList()
        }
        val points = samples.zip(objs).map { (s, o) -> toPoint(s, o) }
        val dest = points.lastOrNull()
        val destObj = objs.getOrNull(samples.size - 1)
        val sun = dest?.let { sunTimes(destObj, it.etaEpochSec) }
        val dark = if (dest != null && sun != null && sun.first != null && sun.second != null) {
            dest.etaEpochSec > sun.second!! || dest.etaEpochSec < sun.first!!
        } else null
        return WeatherSummary(points, dest, hazardsAhead(points), dark, sun?.second)
    }

    private fun hazardsAhead(points: List<WeatherPoint>): List<Pair<WeatherHazard, Double>> {
        val seen = LinkedHashMap<WeatherHazard, Double>()
        for (p in points) for (h in p.hazards) seen.putIfAbsent(h, p.alongMeters)
        return seen.entries.sortedBy { it.value }.map { it.key to it.value }
    }

    private fun toPoint(s: WeatherSample, o: JsonObject): WeatherPoint {
        val hourly = o["hourly"] as? JsonObject
        val times = (hourly?.get("time") as? JsonArray).orEmpty().map { parseTime(it.primitiveContent()) }
        val idx = times.indices.minByOrNull { Math.abs((times[it] ?: Long.MAX_VALUE / 2) - s.etaEpochSec) }
        fun num(key: String): Double? = idx?.let { (hourly?.get(key) as? JsonArray)?.getOrNull(it)?.doubleOrNullSafe() }
        val temp = num("temperature_2m")
        val precip = num("precipitation")
        val gust = num("wind_gusts_10m")
        val code = num("weather_code")?.toInt()
        return WeatherPoint(
            s.alongMeters, s.position, s.etaEpochSec, temp, precip, code, gust,
            condition(code), hazards(temp, precip, code, gust),
        )
    }

    private fun sunTimes(o: JsonObject?, etaSec: Long): Pair<Long?, Long?>? {
        val daily = o?.get("daily") as? JsonObject ?: return null
        val rises = (daily["sunrise"] as? JsonArray).orEmpty().map { parseTime(it.primitiveContent()) }
        val sets = (daily["sunset"] as? JsonArray).orEmpty().map { parseTime(it.primitiveContent()) }
        val day = etaSec / 86_400
        val i = rises.indices.firstOrNull { (rises[it] ?: sets.getOrNull(it))?.let { t -> t / 86_400 == day } == true } ?: return null
        return rises[i] to sets.getOrNull(i)
    }

    private fun JsonElement.primitiveContent(): String? = (this as? JsonPrimitive)?.takeIf { it !is JsonNull }?.contentOrNull
    private fun JsonElement.doubleOrNullSafe(): Double? = (this as? JsonPrimitive)?.takeIf { it !is JsonNull }?.doubleOrNull

    /** "2026-11-20T14:00" (GMT) -> Epoch-Sekunden; leer/ungueltig -> null. */
    private fun parseTime(s: String?): Long? =
        s?.takeIf { it.isNotBlank() }?.let { runCatching { LocalDateTime.parse(it).toEpochSecond(ZoneOffset.UTC) }.getOrNull() }

    /** WMO-Wettercodes (Open-Meteo). */
    fun condition(code: Int?): WeatherCondition = when (code) {
        null -> WeatherCondition.UNKNOWN
        0, 1 -> WeatherCondition.CLEAR
        2, 3 -> WeatherCondition.CLOUDY
        45, 48 -> WeatherCondition.FOG
        in 51..57 -> WeatherCondition.DRIZZLE
        in 61..67 -> WeatherCondition.RAIN
        in 71..77, 85, 86 -> WeatherCondition.SNOW
        in 80..82 -> WeatherCondition.SHOWERS
        in 95..99 -> WeatherCondition.STORM
        else -> WeatherCondition.UNKNOWN
    }

    fun hazards(tempC: Double?, precipMm: Double?, code: Int?, gustKmh: Double?): Set<WeatherHazard> = buildSet {
        if ((precipMm ?: 0.0) >= 3.0 || code in listOf(65, 82)) add(WeatherHazard.HEAVY_RAIN)
        if (code in 71..77 || code == 85 || code == 86) add(WeatherHazard.SNOW)
        if (code in listOf(56, 57, 66, 67) || (tempC != null && tempC <= 1.0 && (precipMm ?: 0.0) > 0.0)) add(WeatherHazard.ICE)
        if (code in 95..99) add(WeatherHazard.STORM)
        if (code == 45 || code == 48) add(WeatherHazard.FOG)
        if ((gustKmh ?: 0.0) >= 70.0) add(WeatherHazard.WIND)
        if (tempC != null && tempC <= -15.0) add(WeatherHazard.FROST)
    }
}
