package app.kiloquest.game

import java.util.Locale
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.sin
import kotlin.math.tan
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.random.Random

/** Sonnenstand (NOAA-Naeherung, Genauigkeit ca. 0,5 Grad) fuer "ist es dunkel?". */
object Sun {
    private fun rad(d: Double) = d * PI / 180.0
    private fun deg(r: Double) = r * 180.0 / PI

    /** Hoehe der Sonne ueber dem Horizont in Grad (negativ = darunter). */
    fun elevationDegrees(lat: Double, lon: Double, epochSec: Long): Double {
        val jd = epochSec / 86400.0 + 2440587.5
        val t = (jd - 2451545.0) / 36525.0
        val l0 = (280.46646 + t * (36000.76983 + t * 0.0003032)).mod(360.0)
        val m = 357.52911 + t * (35999.05029 - 0.0001537 * t)
        val e = 0.016708634 - t * (0.000042037 + 0.0000001267 * t)
        val c = sin(rad(m)) * (1.914602 - t * (0.004817 + 0.000014 * t)) +
            sin(rad(2 * m)) * (0.019993 - 0.000101 * t) + sin(rad(3 * m)) * 0.000289
        val trueLong = l0 + c
        val omega = 125.04 - 1934.136 * t
        val lambda = trueLong - 0.00569 - 0.00478 * sin(rad(omega))
        val eps0 = 23.0 + (26.0 + (21.448 - t * (46.815 + t * (0.00059 - t * 0.001813))) / 60.0) / 60.0
        val eps = eps0 + 0.00256 * cos(rad(omega))
        val decl = asin(sin(rad(eps)) * sin(rad(lambda)))
        val y = tan(rad(eps) / 2).let { it * it }
        val eqTime = 4.0 * deg(
            y * sin(2 * rad(l0)) - 2 * e * sin(rad(m)) + 4 * e * y * sin(rad(m)) * cos(2 * rad(l0)) -
                0.5 * y * y * sin(4 * rad(l0)) - 1.25 * e * e * sin(2 * rad(m)),
        )
        val minutesUtc = (epochSec % 86400) / 60.0
        val trueSolarMin = (minutesUtc + eqTime + 4.0 * lon).mod(1440.0)
        val hourAngle = trueSolarMin / 4.0 - 180.0
        val cosZenith = sin(rad(lat)) * sin(decl) + cos(rad(lat)) * cos(decl) * cos(rad(hourAngle))
        return deg(asin(cosZenith.coerceIn(-1.0, 1.0)))
    }

    /** Buergerliche Daemmerung vorbei (Sonne mehr als 6 Grad unter dem Horizont): Sterne/Mond/Polarlicht sichtbar. */
    fun isDark(lat: Double, lon: Double, epochSec: Long) = elevationDegrees(lat, lon, epochSec) < -6.0
}

enum class MoonPhaseName { NEW, WAXING_CRESCENT, FIRST_QUARTER, WAXING_GIBBOUS, FULL, WANING_GIBBOUS, LAST_QUARTER, WANING_CRESCENT }

object Moon {
    private const val SYNODIC_DAYS = 29.530588853
    private const val NEW_MOON_EPOCH = 947182440L // 2000-01-06 18:14 UTC

    /** 0.0 = Neumond, 0.5 = Vollmond. */
    fun cycle(epochSec: Long): Double {
        val days = (epochSec - NEW_MOON_EPOCH) / 86400.0
        return (days / SYNODIC_DAYS).mod(1.0)
    }

    fun phase(epochSec: Long): MoonPhaseName = when (floor(cycle(epochSec) * 8 + 0.5).toInt() % 8) {
        0 -> MoonPhaseName.NEW
        1 -> MoonPhaseName.WAXING_CRESCENT
        2 -> MoonPhaseName.FIRST_QUARTER
        3 -> MoonPhaseName.WAXING_GIBBOUS
        4 -> MoonPhaseName.FULL
        5 -> MoonPhaseName.WANING_GIBBOUS
        6 -> MoonPhaseName.LAST_QUARTER
        else -> MoonPhaseName.WANING_CRESCENT
    }
}

enum class AuroraChance { NONE, POSSIBLE, GOOD }

/** Polarlicht-Chance aus Kp-Index (NOAA SWPC), Breitengrad und Dunkelheit. Grobe Faustregel, keine Garantie. */
object Aurora {
    private val json = Json { ignoreUnknownKeys = true }

    /** Letzter Kp-Wert aus `noaa-planetary-k-index.json` (Tabelle mit Kopfzeile oder Objekte). */
    fun latestKp(body: String): Double? {
        val root = json.parseToJsonElement(body) as? JsonArray ?: return null
        val last = root.lastOrNull() ?: return null
        return runCatching {
            when (last) {
                is JsonArray -> last.getOrNull(1)?.jsonPrimitive?.doubleOrNull
                else -> last.jsonObject["Kp"]?.jsonPrimitive?.doubleOrNull
            }
        }.getOrNull()
    }

    /** Aeusserer Rand des Polarlichtovals ca. bei 67 Grad - 2 Grad je Kp-Stufe. */
    fun chance(lat: Double, kp: Double?, dark: Boolean): AuroraChance {
        if (!dark || kp == null) return AuroraChance.NONE
        val edge = 67.0 - 2.0 * kp
        return when {
            lat >= edge + 3.0 -> AuroraChance.GOOD
            lat >= edge -> AuroraChance.POSSIBLE
            else -> AuroraChance.NONE
        }
    }
}

enum class Compass8 { N, NE, E, SE, S, SW, W, NW }

data class Plane(
    val callsign: String,
    val typeCode: String?,
    val altitudeMeters: Double,
    val distanceKm: Double,
    val bearingDegrees: Double,
)

/** Flugzeuge in der Naehe per adsb.lol (frei, ohne Schluessel, Community-ADS-B-Daten). Kein Netzwerkcode hier. */
object PlaneSpotting {
    const val PENDING_KEY = "sky_pending"
    const val PLANE_KEY = "sky_plane"
    const val MOON_KEY = "sky_moon"
    const val AURORA_KEY = "sky_aurora"
    const val SOURCE_KEY = "sky_source"
    val textKeys: List<String> = listOf(PENDING_KEY, PLANE_KEY, MOON_KEY, AURORA_KEY, SOURCE_KEY) +
        MoonPhaseName.entries.map { "moon_${it.name.lowercase()}" } + Compass8.entries.map { "dir_${it.name.lowercase()}" }

    private val json = Json { ignoreUnknownKeys = true }
    private const val FEET_TO_M = 0.3048

    fun buildUrl(lat: Double, lon: Double, radiusNm: Int = 25) =
        "https://api.adsb.lol/v2/point/${String.format(Locale.ROOT, "%.4f", lat)}/${String.format(Locale.ROOT, "%.4f", lon)}/$radiusNm"

    fun parse(body: String, lat: Double, lon: Double): List<Plane> {
        val ac = (json.parseToJsonElement(body).jsonObject["ac"] as? JsonArray).orEmpty()
        return ac.mapNotNull { e ->
            val o = e.jsonObject
            val pLat = o["lat"]?.jsonPrimitive?.doubleOrNull ?: return@mapNotNull null
            val pLon = o["lon"]?.jsonPrimitive?.doubleOrNull ?: return@mapNotNull null
            // alt_baro ist Fuss oder die Zeichenkette "ground"
            val alt = (o["alt_baro"] as? JsonPrimitive)?.doubleOrNull ?: return@mapNotNull null
            val flight = (o["flight"] as? JsonPrimitive)?.contentOrNull?.trim().orEmpty()
            Plane(
                callsign = flight, typeCode = (o["t"] as? JsonPrimitive)?.contentOrNull,
                altitudeMeters = alt * FEET_TO_M,
                distanceKm = distanceKm(lat, lon, pLat, pLon),
                bearingDegrees = bearing(lat, lon, pLat, pLon),
            )
        }
    }

    /** Zufaellig eines der naechsten Flugzeuge, das man sehen kann: hoeher als 1000 m, nicht weiter als 25 km. */
    fun pick(planes: List<Plane>, random: Random): Plane? {
        val visible = planes.filter { it.altitudeMeters in 1_000.0..13_000.0 && it.distanceKm <= 25.0 && it.typeCode != null }
            .sortedBy { it.distanceKm }.take(5)
        return visible.takeIf { it.isNotEmpty() }?.let { it[random.nextInt(it.size)] }
    }

    fun compass(bearingDegrees: Double): Compass8 = Compass8.entries[((bearingDegrees + 22.5).mod(360.0) / 45.0).toInt() % 8]

    /** Lesbarer Typ aus dem ICAO-Code (Rueckfall: Code selbst). */
    fun typeName(code: String?): String = when (code) {
        null -> ""
        "A318", "A319", "A320", "A321" -> "Airbus $code"
        "A19N" -> "Airbus A319neo"
        "A20N" -> "Airbus A320neo"
        "A21N" -> "Airbus A321neo"
        "A332", "A333" -> "Airbus A${code.substring(1)}"
        "A359" -> "Airbus A350"
        "A388" -> "Airbus A380"
        "B737" -> "Boeing 737-700"
        "B738" -> "Boeing 737-800"
        "B739" -> "Boeing 737-900"
        "B38M" -> "Boeing 737 MAX"
        "B744" -> "Boeing 747-400"
        "B748" -> "Boeing 747-8"
        "B772", "B77W", "B77L" -> "Boeing 777"
        "B788", "B789", "B78X" -> "Boeing 787"
        "E190", "E195" -> "Embraer ${code.substring(1)}"
        "AT72", "AT76" -> "ATR ${code.substring(2)}"
        "DH8D" -> "Dash 8"
        "CRJ9", "CRJ7" -> "Canadair Jet"
        else -> code
    }

    fun distanceKm(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val r = 6371.0088
        val dLat = (lat2 - lat1) * PI / 180
        val dLon = (lon2 - lon1) * PI / 180
        val a = sin(dLat / 2).let { it * it } + cos(lat1 * PI / 180) * cos(lat2 * PI / 180) * sin(dLon / 2).let { it * it }
        return 2 * r * asin(kotlin.math.sqrt(a))
    }

    fun bearing(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val y = sin((lon2 - lon1) * PI / 180) * cos(lat2 * PI / 180)
        val x = cos(lat1 * PI / 180) * sin(lat2 * PI / 180) - sin(lat1 * PI / 180) * cos(lat2 * PI / 180) * cos((lon2 - lon1) * PI / 180)
        return (atan2(y, x) * 180 / PI + 360).mod(360.0)
    }
}
