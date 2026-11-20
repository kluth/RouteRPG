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

/** Ausstattung eines Halts, soweit bekannt (z. B. aus den Betreiberdaten der Autobahn). */
enum class StopFeature { TOILET, WHEELCHAIR_WC, PLAYGROUND, RESTAURANT, FUEL, SHOWER }

/** [verified] = Toilette vom Betreiber bestaetigt (Autobahn GmbH), nicht nur aus OpenStreetMap geschlossen. */
data class ToiletStop(
    val kind: ToiletKind,
    val name: String?,
    val position: LatLon,
    val verified: Boolean = false,
    val features: Set<StopFeature> = emptySet(),
)

/** Ergebnis der Auswahl: Ziel und echte Fahrzeit (Sekunden) dorthin, null wenn nur geschaetzt. */
data class ToiletChoice(val stop: ToiletStop, val seconds: Double?, val straightMeters: Double, val aheadMeters: Double? = null)

/** Kandidat auf dem weiteren Reiseverlauf: [aheadMeters] bis zur Stelle auf der Route, [offsetMeters] Abstand von der Route (Umweg). */
data class AheadStop(val stop: ToiletStop, val aheadMeters: Double, val offsetMeters: Double) {
    /** Je kleiner, desto besser: Strecke bis dorthin plus doppelter Umweg (hin und zurueck); bestaetigte Toiletten zaehlen 1 km naeher. */
    val score: Double get() = aheadMeters + 2 * offsetMeters - (if (stop.verified) 1_000.0 else 0.0)
}

/**
 * Klo-Alarm: naechste Toilette in Fahrzeit (nicht Luftlinie) finden. Overpass liefert Kandidaten rund um die Position,
 * Valhalla `sources_to_targets` die echten Fahrzeiten (beachtet Einbahn/Autobahn: eine Ausfahrt hinter uns zaehlt nicht).
 * Kein Netzwerkcode hier.
 */
object ToiletFinder {
    private val json = Json { ignoreUnknownKeys = true }

    /** Aufschlag in Sekunden: Tankstellen haben meist, aber nicht sicher eine Toilette. */
    private fun penaltySeconds(s: ToiletStop) = (if (s.kind == ToiletKind.FUEL && !s.verified) 90.0 else 0.0) - (if (s.verified) 45.0 else 0.0)

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
        // Bestaetigte Halte zuerst, damit bei Dubletten der Betreiber-Eintrag bleibt
        for (c in candidates.sortedByDescending { it.verified }) {
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

    /**
     * Fuegt Betreiberdaten ([verified], z. B. Autobahn-Rastplaetze) mit OpenStreetMap-Treffern zusammen: Treffer aus OSM, die
     * weniger als [mergeMeters] neben einem bestaetigten Halt liegen, sind derselbe Ort und entfallen.
     */
    fun merge(osm: List<ToiletStop>, verified: List<ToiletStop>, mergeMeters: Double = 250.0): List<ToiletStop> =
        verified + osm.filter { o -> verified.none { haversine(it.position, o.position) < mergeMeters } }

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
            val score = (t ?: (straight / (50.0 / 3.6))) + penaltySeconds(c)
            if (seconds != null && t == null) return@forEachIndexed // laut Routing nicht erreichbar
            if (score < bestScore) { bestScore = score; best = ToiletChoice(c, t, straight) }
        }
        return best
    }
}

/**
 * Autobahn-Rastplaetze der Autobahn GmbH (frei, ohne Schluessel): `.../{Autobahn}/services/parking_lorry`. Jeder Eintrag hat
 * Koordinate, Namen und Ausstattung; hier zaehlen nur Plaetze mit "Toilette vorhanden". Die Betreiberangabe ist verlaesslicher als
 * OpenStreetMap, gilt aber nur fuer deutsche Autobahnen. Kein Netzwerkcode hier.
 */
object AutobahnRestAreas {
    private val json = Json { ignoreUnknownKeys = true }

    fun url(road: String) = AutobahnApi.url(road, "parking_lorry")

    /** Alle Rastplaetze; mit [requireToilet] nur die mit "Toilette vorhanden" (Standard, fuer den Klo-Alarm). */
    fun parse(body: String, requireToilet: Boolean = true): List<ToiletStop> {
        val items = (json.parseToJsonElement(body).jsonObject["parking_lorry"] as? JsonArray) ?: return emptyList()
        return items.mapNotNull { runCatching { toStop(it.jsonObject, requireToilet) }.getOrNull() }
    }

    /**
     * Liegt der Platz auf unserer Fahrbahn? Rastplaetze gibt es je Richtung; die der Gegenrichtung liegen links von der Route
     * (Rechtsverkehr) und sind nicht erreichbar. Mehr als [toleranceMeters] links der Linie zaehlt als Gegenfahrbahn (echte A1-Daten: unsere Seite 28 bis 50 m rechts, Gegenrichtung 38 bis 74 m links).
     */
    fun onOurSide(route: Route, stop: ToiletStop, toleranceMeters: Double = 15.0): Boolean {
        val pr = route.line.project(stop.position)
        val a = route.line.points[pr.segmentIndex]
        val b = route.line.points[(pr.segmentIndex + 1).coerceAtMost(route.line.points.size - 1)]
        val kx = Math.cos(Math.toRadians(a.lat)) * 111_320.0
        val ky = 110_540.0
        val bx = (b.lon - a.lon) * kx; val by = (b.lat - a.lat) * ky
        val px = (stop.position.lon - a.lon) * kx; val py = (stop.position.lat - a.lat) * ky
        val len = Math.sqrt(bx * bx + by * by)
        if (len == 0.0) return true
        val leftMeters = (bx * py - by * px) / len // positiv = links der Fahrtrichtung
        return leftMeters <= toleranceMeters
    }

    /** Als Pausen-Kandidaten (mit Ausstattung): nur Plaetze zwischen [fromMeters] und [toMeters] und hoechstens [maxOffsetMeters] von der Route. */
    fun toRestStops(stops: List<ToiletStop>, route: Route, fromMeters: Double, toMeters: Double, maxOffsetMeters: Double = 700.0): List<RestStop> =
        stops.mapNotNull { s ->
            val pr = route.line.project(s.position)
            if (pr.offsetMeters > maxOffsetMeters || pr.alongMeters < fromMeters || pr.alongMeters > toMeters) return@mapNotNull null
            RestStop(
                kind = if (s.kind == ToiletKind.SERVICES) StopKind.SERVICES else StopKind.REST_AREA,
                name = s.name, position = s.position, alongMeters = pr.alongMeters, offsetMeters = pr.offsetMeters,
                playgroundNearby = StopFeature.PLAYGROUND in s.features, toiletsNearby = StopFeature.TOILET in s.features, verified = true,
            )
        }

    private fun toStop(o: JsonObject, requireToilet: Boolean): ToiletStop? {
        val feats = (o["lorryParkingFeatureIcons"] as? JsonArray).orEmpty().mapNotNull { it.jsonObject["description"]?.jsonPrimitive?.content }
        if (requireToilet && "Toilette vorhanden" !in feats) return null
        val c = o["coordinate"]?.jsonObject ?: return null
        val lat = c["lat"]?.jsonPrimitive?.doubleOrNull ?: return null
        val lon = c["long"]?.jsonPrimitive?.doubleOrNull ?: return null
        val restaurant = feats.any { it.startsWith("Raststätte") || it == "Tankstelle mit Raststätte" }
        val fuel = feats.any { it.startsWith("Tankstelle") }
        val kind = if (restaurant) ToiletKind.SERVICES else ToiletKind.REST_AREA
        val features = buildSet {
            if ("Toilette vorhanden" in feats) add(StopFeature.TOILET)
            if ("Barrierefreie Toilettenkabine" in feats) add(StopFeature.WHEELCHAIR_WC)
            if ("Spielplatz" in feats) add(StopFeature.PLAYGROUND)
            if (restaurant) add(StopFeature.RESTAURANT)
            if (fuel) add(StopFeature.FUEL)
            if ("Dusche vorhanden" in feats) add(StopFeature.SHOWER)
        }
        val name = o["subtitle"]?.jsonPrimitive?.content?.takeIf { it.isNotBlank() }
        return ToiletStop(kind, name, LatLon(lat, lon), verified = true, features = features)
    }
}
