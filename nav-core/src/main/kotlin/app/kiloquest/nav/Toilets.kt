package app.kiloquest.nav

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

enum class ToiletKind { TOILETS, REST_AREA, SERVICES, FUEL }

data class ToiletStop(val kind: ToiletKind, val name: String?, val position: LatLon)

/** Ergebnis der Auswahl: Ziel und echte Fahrzeit (Sekunden) dorthin, null wenn nur geschaetzt. */
data class ToiletChoice(val stop: ToiletStop, val seconds: Double?, val straightMeters: Double, val aheadMeters: Double? = null)

/** Kandidat auf dem weiteren Reiseverlauf: [aheadMeters] bis zur Stelle auf der Route, [offsetMeters] Abstand von der Route (Umweg). */
data class AheadStop(val stop: ToiletStop, val aheadMeters: Double, val offsetMeters: Double) {
    /** Je kleiner, desto besser: Strecke bis dorthin plus doppelter Umweg (hin und zurueck). */
    val score: Double get() = aheadMeters + 2 * offsetMeters
}

/**
 * Klo-Alarm: naechste Toilette in Fahrzeit (nicht Luftlinie) finden. Overpass liefert Kandidaten rund um die Position,
 * Valhalla `sources_to_targets` die echten Fahrzeiten (beachtet Einbahn/Autobahn: eine Ausfahrt hinter uns zaehlt nicht).
 * Kein Netzwerkcode hier.
 */
object ToiletFinder {
    private val json = Json { ignoreUnknownKeys = true }

    /** Aufschlag in Sekunden: Tankstellen haben meist, aber nicht sicher eine Toilette. */
    private fun penaltySeconds(k: ToiletKind) = if (k == ToiletKind.FUEL) 90.0 else 0.0

    fun buildQuery(pos: LatLon, radiusMeters: Int): String {
        val at = "${"%.5f".format(java.util.Locale.ROOT, pos.lat)},${"%.5f".format(java.util.Locale.ROOT, pos.lon)}"
        fun q(filter: String) = "nwr(around:$radiusMeters,$at)$filter;"
        return "[out:json][timeout:20];(" + q("[amenity=toilets]") + q("[highway=rest_area]") + q("[highway=services]") + q("[amenity=fuel]") + ");out center tags 120;"
    }

    /**
     * Overpass-Anfrage entlang des weiteren Reiseverlaufs: von [fromMeters] bis [toMeters] auf der Route, jeweils im Umkreis
     * [corridorMeters] um Stuetzpunkte im Abstand von hoechstens der Haelfte des Korridors (so entsteht ein durchgehender Korridor).
     */
    fun buildRouteQuery(route: Route, fromMeters: Double, toMeters: Double, corridorMeters: Int): String {
        val end = minOf(toMeters, route.line.lengthMeters)
        val step = (corridorMeters * 1.6).coerceAtLeast(800.0)
        val pts = ArrayList<LatLon>()
        var m = fromMeters.coerceAtLeast(0.0)
        while (m < end) { pts += route.line.points[route.line.indexAtMeters(m)]; m += step }
        pts += route.line.points[route.line.indexAtMeters(end)]
        val line = pts.distinct().joinToString(",") { "${"%.5f".format(java.util.Locale.ROOT, it.lat)},${"%.5f".format(java.util.Locale.ROOT, it.lon)}" }
        fun q(filter: String) = "nwr(around:$corridorMeters,$line)$filter;"
        return "[out:json][timeout:25];(" + q("[amenity=toilets]") + q("[highway=rest_area]") + q("[highway=services]") + q("[amenity=fuel]") + ");out center tags 250;"
    }

    /**
     * Nur Kandidaten, die noch vor uns liegen ([minAheadMeters] bis [windowMeters] voraus) und hoechstens [corridorMeters] von der
     * Route entfernt sind, nach [AheadStop.score] sortiert (naechste zuerst, grosse Umwege hinten). Dubletten entfernt.
     */
    fun ahead(
        candidates: List<ToiletStop>,
        route: Route,
        alongMeters: Double,
        windowMeters: Double,
        corridorMeters: Double,
        minAheadMeters: Double = 150.0,
    ): List<AheadStop> {
        val out = ArrayList<AheadStop>()
        for (c in candidates) {
            val pr = route.line.project(c.position)
            val ahead = pr.alongMeters - alongMeters
            if (ahead < minAheadMeters || ahead > windowMeters || pr.offsetMeters > corridorMeters) continue
            if (out.any { it.stop.kind == c.kind && haversine(it.stop.position, c.position) < 120.0 }) continue
            out += AheadStop(c, ahead, pr.offsetMeters)
        }
        return out.sortedBy { it.score }
    }

    fun parse(body: String): List<ToiletStop> {
        val elements = (json.parseToJsonElement(body).jsonObject["elements"] as? JsonArray).orEmpty()
        return elements.mapNotNull { toStop(it.jsonObject) }
    }

    private fun toStop(o: JsonObject): ToiletStop? {
        val tags = o["tags"]?.jsonObject ?: return null
        fun tag(k: String) = tags[k]?.jsonPrimitive?.content
        val kind = when {
            tag("amenity") == "toilets" -> ToiletKind.TOILETS
            tag("highway") == "rest_area" -> ToiletKind.REST_AREA
            tag("highway") == "services" -> ToiletKind.SERVICES
            tag("amenity") == "fuel" -> ToiletKind.FUEL
            else -> return null
        }
        val lat = o["lat"]?.jsonPrimitive?.doubleOrNull ?: o["center"]?.jsonObject?.get("lat")?.jsonPrimitive?.doubleOrNull ?: return null
        val lon = o["lon"]?.jsonPrimitive?.doubleOrNull ?: o["center"]?.jsonObject?.get("lon")?.jsonPrimitive?.doubleOrNull ?: return null
        // Toiletten, die nicht oeffentlich sind, ueberspringen
        if (kind == ToiletKind.TOILETS && (tag("access") == "private" || tag("access") == "customers")) return null
        return ToiletStop(kind, tag("name"), LatLon(lat, lon))
    }

    /** Die [n] naechsten (Luftlinie) Kandidaten, Dubletten (gleiche Art, unter 120 m) entfernt. */
    fun nearest(candidates: List<ToiletStop>, pos: LatLon, n: Int = 8): List<ToiletStop> {
        val out = ArrayList<ToiletStop>()
        for (c in candidates.sortedBy { haversine(pos, it.position) }) {
            if (out.none { it.kind == c.kind && haversine(it.position, c.position) < 120.0 }) out += c
            if (out.size >= n) break
        }
        return out
    }

    fun matrixRequest(from: LatLon, targets: List<ToiletStop>, costing: String = "auto"): String = buildJsonObject {
        put("costing", costing)
        put("sources", buildJsonArray { add(buildJsonObject { put("lat", from.lat); put("lon", from.lon) }) })
        put("targets", buildJsonArray { targets.forEach { t -> add(buildJsonObject { put("lat", t.position.lat); put("lon", t.position.lon) }) } })
    }.toString()

    /** Fahrzeit in Sekunden je Ziel (Reihenfolge wie in der Anfrage); null, wenn nicht erreichbar. */
    fun parseMatrix(body: String, targets: Int): List<Double?> {
        val row = json.parseToJsonElement(body).jsonObject["sources_to_targets"]?.jsonArray?.firstOrNull()?.jsonArray ?: return List(targets) { null }
        val result = MutableList<Double?>(targets) { null }
        for (cell in row) {
            val o = cell.jsonObject
            val i = o["to_index"]?.jsonPrimitive?.doubleOrNull?.toInt() ?: continue
            val t = o["time"]?.takeIf { it !is JsonNull }?.jsonPrimitive?.doubleOrNull
            if (i in result.indices) result[i] = t
        }
        return result
    }

    /**
     * Waehlt das Ziel mit der kuerzesten Fahrzeit (plus Aufschlag je Art). Ohne Fahrzeiten (offline) entscheidet die
     * Luftlinie, ebenfalls mit Aufschlag (als 50 km/h umgerechnet).
     */
    fun choose(pos: LatLon, candidates: List<ToiletStop>, secondsOrNull: List<Double?>?): ToiletChoice? {
        val seconds = secondsOrNull?.takeIf { it.isNotEmpty() }
        var best: ToiletChoice? = null
        var bestScore = Double.MAX_VALUE
        candidates.forEachIndexed { i, c ->
            val straight = haversine(pos, c.position)
            val t = seconds?.getOrNull(i)
            val score = (t ?: (straight / (50.0 / 3.6))) + penaltySeconds(c.kind)
            if (seconds != null && t == null) return@forEachIndexed // laut Routing nicht erreichbar
            if (score < bestScore) { bestScore = score; best = ToiletChoice(c, t, straight) }
        }
        return best
    }
}
