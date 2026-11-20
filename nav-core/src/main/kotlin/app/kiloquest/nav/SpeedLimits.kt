package app.kiloquest.nav

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

/** Tempolimit-Abschnitt auf der Route; [limitKmh] null = unbekannt. */
data class SpeedSegment(val fromMeters: Double, val toMeters: Double, val limitKmh: Int?)

/** Tempolimits entlang der Route, fensterweise nachgeladen und zusammengefuehrt. */
class SpeedProfile {
    private val segments = ArrayList<SpeedSegment>()
    var coveredUntilMeters: Double = -1.0
        private set

    /** Fuegt ein Fenster hinzu; bestehende Abschnitte ab dessen Beginn werden ersetzt. */
    fun addWindow(windowStartMeters: Double, windowEndMeters: Double, newSegments: List<SpeedSegment>) {
        segments.removeAll { it.toMeters > windowStartMeters }
        segments += newSegments.sortedBy { it.fromMeters }
        coveredUntilMeters = maxOf(coveredUntilMeters, windowEndMeters)
    }

    /** Alle Abschnitte (fuer das Offline-Paket). */
    fun snapshot(): List<SpeedSegment> = segments.toList()

    /** Limit an der Streckenposition: Zahl in km/h, [UNLIMITED] oder null (unbekannt). */
    fun limitAt(alongMeters: Double): Int? {
        // Segmente sind nach Start sortiert; letztes mit from <= along
        var lo = 0
        var hi = segments.size - 1
        var found = -1
        while (lo <= hi) {
            val mid = (lo + hi) ushr 1
            if (segments[mid].fromMeters <= alongMeters) { found = mid; lo = mid + 1 } else hi = mid - 1
        }
        if (found < 0) return null
        val s = segments[found]
        return if (alongMeters <= s.toMeters + 1.0) s.limitKmh else null
    }

    companion object {
        /** Profil aus gespeicherten Abschnitten; [coveredUntilMeters] = bis wohin die Daten reichen. */
        fun restore(segments: List<SpeedSegment>, coveredUntilMeters: Double): SpeedProfile =
            SpeedProfile().also { if (segments.isNotEmpty()) it.addWindow(0.0, coveredUntilMeters, segments) }

        /** Valhalla/OSM: 255 = kein Tempolimit. */
        const val UNLIMITED = 255

        fun isOverspeed(speedKmh: Double, limit: Int?, toleranceKmh: Int = 3): Boolean =
            limit != null && limit < UNLIMITED && speedKmh > limit + toleranceKmh
    }
}

/** Valhalla `/trace_attributes`: Tempolimits der Kanten entlang eines Routenfensters. */
object TraceAttributes {
    private val json = Json { ignoreUnknownKeys = true }

    /** Fenster der Route (Indexbereich der Punkte) als Anfrage. */
    fun buildRequest(route: Route, fromIndex: Int, toIndex: Int, costing: String = "auto"): String {
        val pts = route.line.points.subList(fromIndex, toIndex + 1)
        return buildJsonObject {
            put("encoded_polyline", PolylineCodec.encode(pts))
            put("costing", costing)
            put("shape_match", "edge_walk")
            put("filters", buildJsonObject {
                put("action", "include")
                put("attributes", buildJsonArray {
                    for (a in listOf("edge.speed_limit", "edge.begin_shape_index", "edge.end_shape_index", "edge.length")) {
                        add(kotlinx.serialization.json.JsonPrimitive(a))
                    }
                })
            })
        }.toString()
    }

    /** [startIndex] = Index des ersten Fensterpunkts in der Route (Edge-Indizes sind fensterrelativ). */
    fun parse(body: String, route: Route, startIndex: Int): List<SpeedSegment> {
        val edges = (json.parseToJsonElement(body).jsonObject["edges"] as? JsonArray) ?: return emptyList()
        val last = route.line.points.size - 1
        return edges.mapNotNull { e ->
            val o = e.jsonObject
            val b = o["begin_shape_index"]?.jsonPrimitive?.intOrNull ?: return@mapNotNull null
            val en = o["end_shape_index"]?.jsonPrimitive?.intOrNull ?: return@mapNotNull null
            val sl = o["speed_limit"]?.jsonPrimitive
            // Valhalla liefert auf Strecken ohne Limit den Text "unlimited"
            val limit = if (sl?.content == "unlimited") SpeedProfile.UNLIMITED else sl?.intOrNull?.takeIf { it > 0 }
            val from = route.line.cumulative[(startIndex + b).coerceIn(0, last)]
            val to = route.line.cumulative[(startIndex + en).coerceIn(0, last)]
            if (to < from) null else SpeedSegment(from, to, limit)
        }
    }
}
