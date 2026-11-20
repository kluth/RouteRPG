package app.kiloquest.nav

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.time.LocalDateTime

enum class SpotKind { PLAYGROUND, ICE_CREAM, SWIMMING, ZOO, CASTLE }

/** Ein Ziel fuer einen Zwischenstopp mit Kindern. [open] = Oeffnungszeiten laut OpenStreetMap (UNKNOWN heisst: unsicher). */
data class KidSpot(
    val kind: SpotKind,
    val name: String?,
    val position: LatLon,
    val alongMeters: Double,
    /** Abstand zur Route (Luftlinie) in Metern. */
    val offsetMeters: Double,
    val open: OpenState = OpenState.UNKNOWN,
) {
    /** Geschaetzter Umweg in Minuten (hin und zurueck, ca. 40 km/h abseits der Hauptstrasse), mindestens 1. */
    val detourMinutes: Int get() = Math.ceil(offsetMeters * 2.0 / (40_000.0 / 60.0)).toInt().coerceAtLeast(1)
}

/**
 * Zwischenstopp-Finder "Kinder-Highlights" aus OpenStreetMap (Overpass): Spielplatz, Eisdiele, Schwimmbad, Zoo/Freizeitpark, Burg.
 * Oeffnungszeiten in OSM sind lueckenhaft: geschlossene Orte fallen weg, Orte ohne Angabe bleiben mit "unsicher". Kein Netzwerkcode hier.
 */
object KidSpots {
    private val json = Json { ignoreUnknownKeys = true }

    fun buildQuery(route: Route, fromMeters: Double, toMeters: Double, corridorMeters: Int = 3000, stepMeters: Double = 1500.0): String {
        val end = minOf(toMeters, route.line.lengthMeters)
        val pts = ArrayList<LatLon>()
        var m = fromMeters.coerceAtLeast(0.0)
        while (m < end) { pts += route.line.points[route.line.indexAtMeters(m)]; m += stepMeters }
        pts += route.line.points[route.line.indexAtMeters(end)]
        val line = pts.distinct().joinToString(",") { "${"%.4f".format(java.util.Locale.ROOT, it.lat)},${"%.4f".format(java.util.Locale.ROOT, it.lon)}" }
        fun q(f: String) = "nwr(around:$corridorMeters,$line)$f;"
        return "[out:json][timeout:25];(" +
            q("[leisure=playground][name]") + q("[leisure=playground][playground]") +
            q("[amenity=ice_cream]") + q("[leisure=water_park]") + q("[leisure=swimming_pool][access=public]") +
            q("[tourism=zoo]") + q("[tourism=theme_park]") + q("[tourism=aquarium]") + q("[historic=castle][name]") +
            ");out center tags 200;"
    }

    private fun kindOf(tags: JsonObject): SpotKind? {
        fun t(k: String) = tags[k]?.jsonPrimitive?.content
        return when {
            t("leisure") == "playground" -> SpotKind.PLAYGROUND
            t("amenity") == "ice_cream" -> SpotKind.ICE_CREAM
            t("leisure") == "water_park" || t("leisure") == "swimming_pool" -> SpotKind.SWIMMING
            t("tourism") in setOf("zoo", "theme_park", "aquarium") -> SpotKind.ZOO
            t("historic") == "castle" -> SpotKind.CASTLE
            else -> null
        }
    }

    /** Treffer nahe der Route, ohne geschlossene Orte, ohne Dubletten (gleiche Art unter 150 m), nach Streckenmetern sortiert. */
    fun parse(body: String, route: Route, now: LocalDateTime, fromMeters: Double = 0.0, toMeters: Double = Double.MAX_VALUE, maxOffsetMeters: Double = 3500.0): List<KidSpot> {
        val elements = (json.parseToJsonElement(body).jsonObject["elements"] as? JsonArray).orEmpty()
        val spots = ArrayList<KidSpot>()
        for (e in elements) {
            val o = e.jsonObject
            val tags = o["tags"] as? JsonObject ?: continue
            val kind = kindOf(tags) ?: continue
            val lat = o["lat"]?.jsonPrimitive?.doubleOrNull ?: (o["center"] as? JsonObject)?.get("lat")?.jsonPrimitive?.doubleOrNull ?: continue
            val lon = o["lon"]?.jsonPrimitive?.doubleOrNull ?: (o["center"] as? JsonObject)?.get("lon")?.jsonPrimitive?.doubleOrNull ?: continue
            val pos = LatLon(lat, lon)
            val p = route.line.project(pos)
            if (p.offsetMeters > maxOffsetMeters || p.alongMeters < fromMeters || p.alongMeters > toMeters) continue
            val status = OpeningHours.statusAt(tags["opening_hours"]?.jsonPrimitive?.content, now).state
            if (status == OpenState.CLOSED) continue
            spots += KidSpot(kind, tags["name"]?.jsonPrimitive?.content, pos, p.alongMeters, p.offsetMeters, status)
        }
        val dedup = ArrayList<KidSpot>()
        for (s in spots.sortedBy { it.alongMeters }) if (dedup.none { d -> d.kind == s.kind && haversine(d.position, s.position) < 150.0 }) dedup += s
        return dedup
    }

    /** Vorschlaege voraus: ab [minAheadMeters] bis [maxAheadMeters] vor uns, kleinster Umweg zuerst je Art, hoechstens [limit]. */
    fun ahead(spots: List<KidSpot>, alongMeters: Double, kinds: Set<SpotKind> = SpotKind.entries.toSet(), minAheadMeters: Double = 1_500.0, maxAheadMeters: Double = 80_000.0, limit: Int = 6): List<KidSpot> =
        spots.filter { it.kind in kinds && it.alongMeters - alongMeters in minAheadMeters..maxAheadMeters }
            .sortedWith(compareBy<KidSpot> { it.open != OpenState.OPEN }.thenBy { it.detourMinutes }.thenBy { it.alongMeters })
            .groupBy { it.kind }.values.flatMap { it.take(2) }
            .sortedBy { it.alongMeters }.take(limit)
}
