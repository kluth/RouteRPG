package app.kiloquest.nav

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/** Laenge und Dauer eines Teilstuecks (Etappe) einer Reise. */
data class LegSummary(val lengthMeters: Double, val durationSeconds: Double)

/** Mehrtagesreise: Start, vom Nutzer gesetzte Etappenziele, Berechnung der Etappen ueber Valhalla. */
object TripApi {
    private val json = Json { ignoreUnknownKeys = true }

    /** Eine Anfrage fuer die ganze Reise (Start + alle Etappenziele); nur die Summen je Etappe werden gelesen. */
    fun buildRequest(start: LatLon, stops: List<LatLon>, languageTag: String): String =
        ValhallaApi.buildRequest(listOf(start) + stops, languageTag)

    fun parseLegs(body: String): List<LegSummary> {
        val trip = json.parseToJsonElement(body).jsonObject["trip"]?.jsonObject ?: return emptyList()
        val legs = trip["legs"] as? JsonArray ?: return emptyList()
        return legs.mapNotNull { l ->
            val sum = l.jsonObject["summary"]?.jsonObject ?: return@mapNotNull null
            val km = sum["length"]?.jsonPrimitive?.doubleOrNull ?: return@mapNotNull null
            val sec = sum["time"]?.jsonPrimitive?.doubleOrNull ?: return@mapNotNull null
            LegSummary(km * 1000.0, sec)
        }
    }

    /** Etappen ueber diese Dauer gelten als sehr lang fuer Kinder (Hinweis in der Planung). */
    const val LONG_STAGE_SECONDS = 8 * 3600.0

    fun isLongStage(l: LegSummary) = l.durationSeconds > LONG_STAGE_SECONDS

    fun totals(legs: List<LegSummary>): LegSummary =
        LegSummary(legs.sumOf { it.lengthMeters }, legs.sumOf { it.durationSeconds })
}
