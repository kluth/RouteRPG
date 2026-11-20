package app.kiloquest.nav

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/** Langes Stueck ohne Ortschaften: dort kann das Mobilfunknetz fehlen und es gibt wenig Versorgung (Tanken, Toiletten). */
data class RemoteStretch(val startMeters: Double, val endMeters: Double) {
    val lengthMeters: Double get() = endMeters - startMeters
}

/**
 * Funkloch-Vorwarnung ohne Mobilfunk-Abdeckungsdaten: Orte (OpenStreetMap `place`) entlang der Route; wo lange keine Ortschaft liegt, ist die Gegend
 * duenn besiedelt. Das ist nur ein **Hinweis** (kein Versprechen, dass das Netz fehlt). Kein Netzwerkcode hier.
 */
object RemoteStretches {
    private val json = Json { ignoreUnknownKeys = true }

    /** Overpass-Anfrage: Ortschaften im Umkreis der Route zwischen [fromMeters] und [toMeters]. */
    fun buildQuery(route: Route, fromMeters: Double, toMeters: Double, stepMeters: Double = 4000.0, radiusMeters: Int = 6000): String {
        val end = minOf(toMeters, route.line.lengthMeters)
        val pts = ArrayList<LatLon>()
        var m = fromMeters.coerceAtLeast(0.0)
        while (m < end) { pts += route.line.points[route.line.indexAtMeters(m)]; m += stepMeters }
        pts += route.line.points[route.line.indexAtMeters(end)]
        val line = pts.distinct().joinToString(",") { "${"%.4f".format(java.util.Locale.ROOT, it.lat)},${"%.4f".format(java.util.Locale.ROOT, it.lon)}" }
        return "[out:json][timeout:25];node(around:$radiusMeters,$line)[place~\"^(city|town|village|hamlet)$\"];out 1500;"
    }

    /** Streckenmeter der gefundenen Orte (auf die Route projiziert), sortiert. */
    fun parsePlaces(body: String, route: Route, maxOffsetMeters: Double = 7000.0): List<Double> {
        val elements = (json.parseToJsonElement(body).jsonObject["elements"] as? JsonArray).orEmpty()
        return elements.mapNotNull { e ->
            val o = e.jsonObject
            val lat = o["lat"]?.jsonPrimitive?.doubleOrNull ?: return@mapNotNull null
            val lon = o["lon"]?.jsonPrimitive?.doubleOrNull ?: return@mapNotNull null
            val p = route.line.project(LatLon(lat, lon))
            if (p.offsetMeters <= maxOffsetMeters) p.alongMeters else null
        }.sorted()
    }

    /** Luecken zwischen Orten (inkl. Start und Ziel der Route), die mindestens [minGapMeters] lang sind. */
    fun find(totalMeters: Double, placesAlong: List<Double>, minGapMeters: Double = 60_000.0): List<RemoteStretch> {
        val marks = (listOf(0.0) + placesAlong.filter { it in 0.0..totalMeters } + listOf(totalMeters)).sorted()
        return marks.zipWithNext().filter { (a, b) -> b - a >= minGapMeters }.map { (a, b) -> RemoteStretch(a, b) }
    }
}

/** Warnt genau einmal je Stueck, wenn es naeher als [warnMeters] kommt (oder man mitten drin startet). */
class RemoteWarner(private val stretches: List<RemoteStretch>, private val warnMeters: Double = 25_000.0) {
    private val warned = HashSet<Double>()

    /** Faellige Warnung: das Stueck und die Entfernung bis zu seinem Beginn (0, wenn schon mittendrin). */
    fun next(alongMeters: Double): Pair<RemoteStretch, Double>? {
        val s = stretches.firstOrNull { it.startMeters !in warned && alongMeters < it.endMeters - 5_000.0 && it.startMeters - alongMeters <= warnMeters } ?: return null
        warned += s.startMeters
        return s to (s.startMeters - alongMeters).coerceAtLeast(0.0)
    }
}
