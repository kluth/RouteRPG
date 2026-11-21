package app.kiloquest.nav

import java.util.Locale
import kotlin.math.roundToLong
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject

/** Ein Treffer der GBIF-Occurrence-Suche. */
data class GbifOccurrence(val species: String, val vernacularName: String?, val license: String)

/** GBIF Occurrence-Suche (frei, ohne Schluessel): URL bauen und Antwort parsen. Kein Netzwerkcode. */
object GbifParser {
    private const val BASE = "https://api.gbif.org/v1/occurrence/search"
    private val json = Json { ignoreUnknownKeys = true }

    /** Datenschutz: Position auf 0,1 Grad runden. */
    fun roundDeg(deg: Double): Double = (deg * 10.0).roundToLong() / 10.0

    /**
     * Suche in der Box gerundete Position +/- [halfBoxDeg]; nur CC0 und CC BY; nur Tiere (taxonKey=1).
     */
    fun buildUrl(lat: Double, lon: Double, limit: Int = 30, halfBoxDeg: Double = 0.1): String {
        val rl = roundDeg(lat)
        val ro = roundDeg(lon)
        fun f(v: Double) = String.format(Locale.ROOT, "%.1f", v)
        return "$BASE?decimalLatitude=${f(rl - halfBoxDeg)},${f(rl + halfBoxDeg)}" +
            "&decimalLongitude=${f(ro - halfBoxDeg)},${f(ro + halfBoxDeg)}" +
            "&license=CC0_1_0&license=CC_BY_4_0&taxonKey=1&occurrenceStatus=PRESENT&limit=$limit"
    }

    /** Parst `results[]`; Eintraege ohne Art oder Lizenz werden uebersprungen. Defekte Antwort = leere Liste. */
    fun parse(body: String): List<GbifOccurrence> = runCatching {
        val results = json.parseToJsonElement(body).jsonObject["results"] as? JsonArray ?: return emptyList()
        results.mapNotNull { e ->
            val o = e.jsonObject
            fun str(k: String) = (o[k] as? JsonPrimitive)?.contentOrNull?.trim()?.takeIf { it.isNotEmpty() }
            val species = str("species") ?: return@mapNotNull null
            val license = str("license") ?: return@mapNotNull null
            GbifOccurrence(species, str("vernacularName"), license)
        }
    }.getOrDefault(emptyList())
}
