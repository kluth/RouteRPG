package app.kiloquest.nav

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

enum class IncidentType { JAM, ACCIDENT, CLOSURE, HAZARD }

data class TrafficIncident(
    val id: String,
    val type: IncidentType,
    val road: String,
    val title: String,
    /** Originaltext der Quelle (Landessprache). */
    val text: String,
    val delayMinutes: Int?,
    val blocked: Boolean,
    /** Verlauf in Fahrtrichtung der Meldung. */
    val geometry: List<LatLon>,
    val inFuture: Boolean,
) {
    val position: LatLon get() = geometry.first()
}

/** Meldung, die auf der geplanten Route liegt. */
data class RouteIncident(
    val incident: TrafficIncident,
    /** Beginn der Meldung als Streckenmeter ab Routenstart. */
    val alongMeters: Double,
    val lengthMeters: Double,
    /** Abstand vom aktuellen Fortschritt bis zum Beginn (negativ = schon mittendrin). */
    val aheadMeters: Double,
)

/** Autobahn GmbH Verkehrs-API (Deutschland, frei, ohne Schluessel): https://verkehr.autobahn.de */
object AutobahnApi {
    const val WARNING = "warning"
    const val CLOSURE = "closure"

    fun url(road: String, service: String) = "https://verkehr.autobahn.de/o/autobahn/$road/services/$service"

    private val roadRegex = Regex("""(?:^|[\s/,])(?:Bundesautobahn\s+|A\s?)(\d{1,3})(?=$|[\s/,])""")

    /** Autobahn-Kennungen ("A1", "A40") aus Strassennamen der Route, in Reihenfolge, ohne Duplikate. */
    fun roadsFromNames(names: Iterable<String>): List<String> =
        names.flatMap { n -> roadRegex.findAll(n).map { "A" + it.groupValues[1] }.toList() }.distinct()

    private val json = Json { ignoreUnknownKeys = true }

    fun parse(body: String, service: String): List<TrafficIncident> {
        val items = (json.parseToJsonElement(body).jsonObject[service] as? JsonArray) ?: return emptyList()
        return items.mapNotNull { runCatching { toIncident(it.jsonObject, service) }.getOrNull() }
    }

    private fun JsonObject.str(key: String) = (this[key] as? JsonPrimitive)?.contentOrNull

    private fun toIncident(o: JsonObject, service: String): TrafficIncident? {
        val id = o.str("identifier") ?: return null
        val title = o.str("title").orEmpty()
        val text = (o["description"] as? JsonArray).orEmpty().joinToString("\n") { it.jsonPrimitive.content }
        val coords = (o["geometry"]?.jsonObject?.get("coordinates") as? JsonArray).orEmpty().mapNotNull { c ->
            val a = c.jsonArray
            val lon = a.getOrNull(0)?.jsonPrimitive?.doubleOrNull
            val lat = a.getOrNull(1)?.jsonPrimitive?.doubleOrNull
            if (lat != null && lon != null) LatLon(lat, lon) else null
        }
        val geometry = coords.ifEmpty {
            val c = o["coordinate"]?.jsonObject
            val lat = c?.get("lat")?.jsonPrimitive?.doubleOrNull
            val lon = c?.get("long")?.jsonPrimitive?.doubleOrNull
            if (lat != null && lon != null) listOf(LatLon(lat, lon)) else return null
        }
        val blocked = (o["isBlocked"] as? JsonPrimitive)?.let { it.booleanOrNull ?: (it.content == "true") } ?: false
        val future = (o["future"] as? JsonPrimitive)?.booleanOrNull ?: false
        val delay = o.str("delayTimeValue")?.toIntOrNull()
            ?: Regex("""Reisezeitverlust:\s*(\d+)\s*Minuten""").find(text)?.groupValues?.get(1)?.toIntOrNull()
        val lower = text.lowercase()
        val type = when {
            service == CLOSURE || blocked -> IncidentType.CLOSURE
            "unfall" in lower -> IncidentType.ACCIDENT
            "stau" in lower || "zähfließend" in lower || (delay ?: 0) > 0 -> IncidentType.JAM
            else -> IncidentType.HAZARD
        }
        return TrafficIncident(
            id = id, type = type, road = title.substringBefore('|').trim(), title = title, text = text,
            delayMinutes = delay, blocked = blocked, geometry = geometry, inFuture = future,
        )
    }
}

/** Ordnet Verkehrsmeldungen der Route zu (Naehe zur Linie UND gleiche Fahrtrichtung). */
object IncidentMatcher {
    fun match(
        route: Route,
        incidents: List<TrafficIncident>,
        progressMeters: Double,
        maxOffsetMeters: Double = 40.0,
        maxAheadMeters: Double = 300_000.0,
    ): List<RouteIncident> = incidents.mapNotNull { inc ->
        if (inc.inFuture) return@mapNotNull null
        val start = inc.geometry.first()
        val end = inc.geometry.last()
        val ps = route.line.project(start)
        val pe = route.line.project(end)
        if (ps.offsetMeters > maxOffsetMeters || pe.offsetMeters > maxOffsetMeters) return@mapNotNull null
        // Fahrtrichtung: Meldung (Anfang -> Ende) muss in Routenrichtung zeigen. Bei Punktmeldung egal.
        if (inc.geometry.size >= 2 && haversine(start, end) > 30.0 && pe.alongMeters < ps.alongMeters) return@mapNotNull null
        val len = (pe.alongMeters - ps.alongMeters).coerceAtLeast(0.0)
        val ahead = ps.alongMeters - progressMeters
        if (ahead + len < 0 || ahead > maxAheadMeters) return@mapNotNull null
        RouteIncident(inc, ps.alongMeters, len, ahead)
    }.sortedBy { it.alongMeters }
}

/** Entscheidet, ob eine Meldung eine Umfahrungs-Suche rechtfertigt. */
class RerouteAdvisor(
    private val minDelayMinutes: Int = 10,
    private val lookAheadMeters: Double = 150_000.0,
) {
    private val considered = HashSet<String>()

    fun isSignificant(ri: RouteIncident): Boolean =
        ri.incident.blocked || (ri.incident.delayMinutes ?: 0) >= minDelayMinutes

    /** Die naechste noch nicht geprueft Meldung, die relevant ist und nicht zu weit voraus liegt. */
    fun next(onRoute: List<RouteIncident>): RouteIncident? =
        onRoute.firstOrNull { it.incident.id !in considered && it.aheadMeters in -1.0..lookAheadMeters && isSignificant(it) }

    fun markConsidered(id: String) { considered += id }
}

/** Sperrzonen fuer Valhalla (`exclude_polygons`): kleine Boxen entlang der Meldung. */
object ExclusionZones {
    fun forIncident(
        inc: TrafficIncident,
        maxPolygons: Int = 8,
        halfSizeMeters: Double = 130.0,
        spacingMeters: Double = 500.0,
    ): List<List<LatLon>> {
        val samples = ArrayList<LatLon>()
        var last: LatLon? = null
        for (p in inc.geometry) {
            if (last == null || haversine(last, p) >= spacingMeters) { samples += p; last = p }
        }
        if (samples.isEmpty()) samples += inc.geometry.first()
        return samples.take(maxPolygons).map { c ->
            val dLat = halfSizeMeters / 111_195.0
            val dLon = halfSizeMeters / (111_195.0 * cos(c.lat * PI / 180.0)).coerceAtLeast(1.0)
            listOf(
                LatLon(c.lat - dLat, c.lon - dLon), LatLon(c.lat - dLat, c.lon + dLon),
                LatLon(c.lat + dLat, c.lon + dLon), LatLon(c.lat + dLat, c.lon - dLon),
                LatLon(c.lat - dLat, c.lon - dLon),
            )
        }
    }
}
