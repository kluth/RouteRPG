package app.kiloquest.nav

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

enum class StopKind { REST_AREA, SERVICES, PLAYGROUND, TOILETS }

data class RestStop(
    val kind: StopKind,
    val name: String?,
    val position: LatLon,
    /** Streckenmeter ab Routenstart (Projektion auf die Route). */
    val alongMeters: Double,
    /** Abstand zur Route in Metern (Umweg). */
    val offsetMeters: Double,
    val playgroundNearby: Boolean,
    val toiletsNearby: Boolean,
    /** Ausstattung vom Betreiber bestaetigt (Autobahn GmbH) statt aus OpenStreetMap geschlossen. */
    val verified: Boolean = false,
)

/** Rast- und Spielplaetze entlang der Route aus OpenStreetMap (Overpass). Kein Netzwerkcode hier. */
object RestStops {
    private val json = Json { ignoreUnknownKeys = true }

    /** Overpass-Anfrage entlang der Route zwischen [fromMeters] und [toMeters] (Linie aus Stuetzpunkten). */
    fun buildQuery(route: Route, fromMeters: Double, toMeters: Double, stepMeters: Double = 1000.0): String {
        val end = minOf(toMeters, route.line.lengthMeters)
        val pts = ArrayList<LatLon>()
        var m = fromMeters.coerceAtLeast(0.0)
        while (m < end) {
            pts += route.line.points[route.line.indexAtMeters(m)]
            m += stepMeters
        }
        pts += route.line.points[route.line.indexAtMeters(end)]
        val line = pts.distinct().joinToString(",") { "${"%.5f".format(java.util.Locale.ROOT, it.lat)},${"%.5f".format(java.util.Locale.ROOT, it.lon)}" }
        fun q(radius: Int, filter: String) = "nwr(around:$radius,$line)$filter;"
        return "[out:json][timeout:20];(" +
            q(500, "[highway=rest_area]") + q(500, "[highway=services]") +
            q(500, "[leisure=playground]") + q(400, "[amenity=toilets]") +
            ");out center tags 80;"
    }

    private data class Item(val kind: StopKind, val name: String?, val pos: LatLon)

    fun parse(
        body: String,
        route: Route,
        fromMeters: Double,
        toMeters: Double,
        maxOffsetMeters: Double = 700.0,
        nearbyMeters: Double = 300.0,
    ): List<RestStop> {
        val elements = (json.parseToJsonElement(body).jsonObject["elements"] as? JsonArray).orEmpty()
        val items = elements.mapNotNull { toItem(it.jsonObject) }
        val playgrounds = items.filter { it.kind == StopKind.PLAYGROUND }
        val toilets = items.filter { it.kind == StopKind.TOILETS }
        val result = ArrayList<RestStop>()
        for (it in items) {
            val proj = route.line.project(it.pos)
            if (proj.offsetMeters > maxOffsetMeters || proj.alongMeters < fromMeters || proj.alongMeters > toMeters) continue
            val pg = it.kind == StopKind.PLAYGROUND || playgrounds.any { p -> haversine(p.pos, it.pos) <= nearbyMeters }
            val wc = it.kind == StopKind.TOILETS || toilets.any { t -> haversine(t.pos, it.pos) <= nearbyMeters }
            result += RestStop(it.kind, it.name, it.pos, proj.alongMeters, proj.offsetMeters, pg, wc)
        }
        // Dubletten (gleiche Art, < 150 m) entfernen
        val dedup = ArrayList<RestStop>()
        for (s in result.sortedBy { it.alongMeters }) {
            if (dedup.none { d -> d.kind == s.kind && haversine(d.position, s.position) < 150.0 }) dedup += s
        }
        return dedup
    }

    private fun toItem(o: JsonObject): Item? {
        val tags = o["tags"]?.jsonObject ?: return null
        fun tag(k: String) = tags[k]?.jsonPrimitive?.content
        val kind = when {
            tag("highway") == "rest_area" -> StopKind.REST_AREA
            tag("highway") == "services" -> StopKind.SERVICES
            tag("leisure") == "playground" -> StopKind.PLAYGROUND
            tag("amenity") == "toilets" -> StopKind.TOILETS
            else -> return null
        }
        val lat = o["lat"]?.jsonPrimitive?.doubleOrNull ?: o["center"]?.jsonObject?.get("lat")?.jsonPrimitive?.doubleOrNull ?: return null
        val lon = o["lon"]?.jsonPrimitive?.doubleOrNull ?: o["center"]?.jsonObject?.get("lon")?.jsonPrimitive?.doubleOrNull ?: return null
        return Item(kind, tag("name"), LatLon(lat, lon))
    }

    /** Betreiberdaten und OpenStreetMap-Treffer zusammen; OSM-Treffer unter [mergeMeters] neben einem Betreiber-Eintrag entfallen (derselbe Ort). */
    fun merge(osm: List<RestStop>, official: List<RestStop>, mergeMeters: Double = 250.0): List<RestStop> =
        (official + osm.filter { o -> official.none { haversine(it.position, o.position) < mergeMeters } }).sortedBy { it.alongMeters }

    /** Beste Pause nahe [targetAlongMeters]: Spielplatz und WC zaehlen, Umweg und Abstand zum Ziel kosten. */
    fun pick(stops: List<RestStop>, targetAlongMeters: Double): RestStop? = stops.maxByOrNull { score(it, targetAlongMeters) }

    fun score(s: RestStop, targetAlongMeters: Double): Double {
        val base = when (s.kind) {
            StopKind.SERVICES, StopKind.REST_AREA -> 3.0
            StopKind.PLAYGROUND -> 2.0
            StopKind.TOILETS -> 1.0
        }
        return base + (if (s.playgroundNearby) 2.0 else 0.0) + (if (s.toiletsNearby) 1.0 else 0.0) + (if (s.verified) 1.5 else 0.0) -
            s.offsetMeters / 300.0 - abs(s.alongMeters - targetAlongMeters) / 20_000.0
    }

    private fun abs(d: Double) = if (d < 0) -d else d
}

/** Zaehlt Fahrzeit seit der letzten Pause; "faellig", sobald das Intervall erreicht ist. */
class BreakPlanner(private val intervalMs: Long, private val restResetMs: Long = 5 * 60_000L, private val movingKmh: Double = 5.0) {
    var drivingMs: Long = 0
        private set
    private var lastTick: Long? = null
    private var stoppedSince: Long? = null
    private var dueSent = false

    /** Mit jeder Positionsmeldung aufrufen. Gibt true zurueck, genau einmal, wenn eine Pause faellig wird. */
    fun onTick(nowMs: Long, speedKmh: Double): Boolean {
        val last = lastTick
        lastTick = nowMs
        val delta = if (last == null) 0L else (nowMs - last).coerceIn(0L, 60_000L)
        if (speedKmh >= movingKmh) {
            stoppedSince = null
            drivingMs += delta
        } else {
            val since = stoppedSince ?: nowMs.also { stoppedSince = it }
            if (nowMs - since >= restResetMs) reset()
        }
        if (!dueSent && drivingMs >= intervalMs) {
            dueSent = true
            return true
        }
        return false
    }

    /** Pause gemacht oder erkannt. */
    fun reset() { drivingMs = 0; dueSent = false }

    /** "Spaeter": Erinnerung nach [laterMs] erneut. */
    fun snooze(laterMs: Long = 10 * 60_000L) {
        drivingMs = (intervalMs - laterMs).coerceAtLeast(0)
        dueSent = false
    }
}
