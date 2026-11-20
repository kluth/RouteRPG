package app.kiloquest.nav

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.contentOrNull

data class Hospital(val name: String, val position: LatLon, val distanceMeters: Double)

/**
 * Notfall-Karte: Notrufnummer des Landes, Standort als Text und naechstes Krankenhaus. Kein Ersatz fuer den Notruf; die App waehlt nie selbst.
 * Alles offline-faehig bis auf die Krankenhaus-Suche (Overpass).
 */
object Emergency {
    private val special = mapOf("GB" to "999", "US" to "911", "CA" to "911", "AU" to "000", "NZ" to "111")
    private val json = Json { ignoreUnknownKeys = true }

    /** Europaeische Notrufnummer 112 gilt in der EU und fast ganz Europa; Ausnahmen stehen in der Tabelle. */
    fun numberFor(country: String?): String = special[country?.uppercase()] ?: "112"

    /** Koordinaten und ein OpenStreetMap-Link, zum Vorlesen am Telefon oder Teilen. */
    fun locationText(pos: LatLon, road: String?): String {
        val lat = "%.5f".format(java.util.Locale.ROOT, pos.lat)
        val lon = "%.5f".format(java.util.Locale.ROOT, pos.lon)
        return buildString {
            if (!road.isNullOrBlank()) append(road).append("\n")
            append(lat).append(", ").append(lon).append("\n")
            append("https://www.openstreetmap.org/?mlat=").append(lat).append("&mlon=").append(lon).append("&zoom=16")
        }
    }

    fun hospitalQuery(pos: LatLon, radiusMeters: Int = 30_000): String {
        val lat = "%.5f".format(java.util.Locale.ROOT, pos.lat); val lon = "%.5f".format(java.util.Locale.ROOT, pos.lon)
        return "[out:json][timeout:20];nwr(around:$radiusMeters,$lat,$lon)[amenity=hospital][emergency!=no];out center tags 40;"
    }

    /** Krankenhaeuser aus der Overpass-Antwort, nach Luftlinie sortiert (nur mit Namen), hoechstens [max]. */
    fun parseHospitals(body: String, from: LatLon, max: Int = 3): List<Hospital> {
        val elements = (json.parseToJsonElement(body).jsonObject["elements"] as? JsonArray).orEmpty()
        return elements.mapNotNull { e ->
            val o = e.jsonObject
            val name = (o["tags"] as? JsonObject)?.get("name")?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
            val c = o["center"] as? JsonObject
            val lat = o["lat"]?.jsonPrimitive?.doubleOrNull ?: c?.get("lat")?.jsonPrimitive?.doubleOrNull ?: return@mapNotNull null
            val lon = o["lon"]?.jsonPrimitive?.doubleOrNull ?: c?.get("lon")?.jsonPrimitive?.doubleOrNull ?: return@mapNotNull null
            val p = LatLon(lat, lon)
            Hospital(name, p, haversine(from, p))
        }.distinctBy { it.name }.sortedBy { it.distanceMeters }.take(max)
    }
}
