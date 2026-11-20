package app.kiloquest.nav

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/** Baut Anfragen an und parst Antworten von Valhalla (/route). Kein Netzwerkcode. */
object ValhallaApi {
    private val json = Json { ignoreUnknownKeys = true }

    /**
     * @param languageTag IETF-Sprache, z. B. "de-DE" (Anweisungen kommen sprachlich von Valhalla -> i18n)
     * @param costing "auto", "motor_scooter" ...
     */
    fun buildRouteRequest(
        from: LatLon,
        to: LatLon,
        languageTag: String,
        costing: String = "auto",
        /** Sperrzonen (Ringe aus Punkten), z. B. um einen Stau, siehe [ExclusionZones]. */
        excludePolygons: List<List<LatLon>> = emptyList(),
        /** OSRM-kompatible Antwort (enthaelt Spurinformationen), siehe [LaneParser]. */
        osrm: Boolean = false,
    ): String = buildJsonObject {
        put("costing", costing)
        if (osrm) {
            put("format", "osrm")
            put("banner_instructions", true)
        }
        put("locations", buildJsonArray {
            add(buildJsonObject { put("lat", from.lat); put("lon", from.lon) })
            add(buildJsonObject { put("lat", to.lat); put("lon", to.lon) })
        })
        if (excludePolygons.isNotEmpty()) {
            put("exclude_polygons", buildJsonArray {
                for (ring in excludePolygons) add(buildJsonArray {
                    for (p in ring) add(buildJsonArray { add(JsonPrimitive(p.lon)); add(JsonPrimitive(p.lat)) })
                })
            })
        }
        put("directions_options", buildJsonObject {
            put("language", languageTag)
            put("units", "kilometers")
        })
    }.toString()

    fun parseRoute(body: String): Route {
        val resp = json.decodeFromString<Response>(body)
        val trip = resp.trip
        require(trip.status == 0) { "Valhalla status ${trip.status}: ${trip.statusMessage}" }
        val leg = trip.legs.firstOrNull() ?: error("no legs")
        val points = PolylineCodec.decode(leg.shape)
        val line = Polyline(points)
        val maneuvers = leg.maneuvers.map { m ->
            val idx = m.beginShapeIndex.coerceIn(0, points.size - 1)
            Maneuver(
                type = m.type,
                instruction = m.instruction,
                verbalPre = m.verbalPreTransitionInstruction,
                verbalPost = m.verbalPostTransitionInstruction,
                streetNames = m.streetNames,
                atMeters = line.cumulative[idx],
                lengthMeters = m.length * 1000.0,
            )
        }
        return Route(line, maneuvers, trip.summary.length * 1000.0, trip.summary.time)
    }

    @Serializable private data class Response(val trip: Trip)
    @Serializable private data class Trip(
        val status: Int = 0,
        @kotlinx.serialization.SerialName("status_message") val statusMessage: String = "",
        val legs: List<Leg> = emptyList(),
        val summary: Summary = Summary(),
    )
    @Serializable private data class Summary(val length: Double = 0.0, val time: Double = 0.0)
    @Serializable private data class Leg(val shape: String, val maneuvers: List<RawManeuver> = emptyList())
    @Serializable private data class RawManeuver(
        val type: Int = 0,
        val instruction: String = "",
        @kotlinx.serialization.SerialName("verbal_pre_transition_instruction") val verbalPreTransitionInstruction: String? = null,
        @kotlinx.serialization.SerialName("verbal_post_transition_instruction") val verbalPostTransitionInstruction: String? = null,
        @kotlinx.serialization.SerialName("street_names") val streetNames: List<String> = emptyList(),
        @kotlinx.serialization.SerialName("begin_shape_index") val beginShapeIndex: Int = 0,
        val length: Double = 0.0,
    )
}
