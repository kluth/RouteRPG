package app.kiloquest.nav

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

enum class LodgingKind { HOTEL, MOTEL, GUEST_HOUSE, APARTMENT, HOSTEL, CAMPING }

/** Unterkunft aus OpenStreetMap. Kontaktdaten sind optional und nur zur Anzeige. */
data class LodgingOption(
    val kind: LodgingKind,
    val name: String,
    val position: LatLon,
    val stars: Int? = null,
    val website: String? = null,
    val phone: String? = null,
)

/** Unterkuenfte rund um ein Tagesziel suchen und fuer Familien sortieren. Kein Netzwerkcode hier. */
object LodgingFinder {
    private val json = Json { ignoreUnknownKeys = true }

    fun buildQuery(center: LatLon, radiusMeters: Int, includeCamping: Boolean): String {
        val c = "${"%.5f".format(java.util.Locale.ROOT, center.lat)},${"%.5f".format(java.util.Locale.ROOT, center.lon)}"
        val kinds = buildList {
            add("hotel|motel|guest_house|apartment|hostel")
            if (includeCamping) add("camp_site|caravan_site")
        }
        return "[out:json][timeout:25];(" + kinds.joinToString("") { "nwr(around:$radiusMeters,$c)[tourism~\"^($it)\$\"][name];" } + ");out center tags 120;"
    }

    fun parse(body: String): List<LodgingOption> {
        val elements = (json.parseToJsonElement(body).jsonObject["elements"] as? JsonArray).orEmpty()
        return elements.mapNotNull { toOption(it.jsonObject) }
    }

    private fun toOption(o: JsonObject): LodgingOption? {
        val tags = o["tags"]?.jsonObject ?: return null
        fun tag(k: String) = tags[k]?.jsonPrimitive?.content?.takeIf { it.isNotBlank() }
        val kind = when (tag("tourism")) {
            "hotel" -> LodgingKind.HOTEL
            "motel" -> LodgingKind.MOTEL
            "guest_house" -> LodgingKind.GUEST_HOUSE
            "apartment" -> LodgingKind.APARTMENT
            "hostel" -> LodgingKind.HOSTEL
            "camp_site", "caravan_site" -> LodgingKind.CAMPING
            else -> return null
        }
        val name = tag("name") ?: return null
        val lat = o["lat"]?.jsonPrimitive?.doubleOrNull ?: o["center"]?.jsonObject?.get("lat")?.jsonPrimitive?.doubleOrNull ?: return null
        val lon = o["lon"]?.jsonPrimitive?.doubleOrNull ?: o["center"]?.jsonObject?.get("lon")?.jsonPrimitive?.doubleOrNull ?: return null
        return LodgingOption(
            kind, name, LatLon(lat, lon),
            stars = tag("stars")?.trim()?.takeWhile { it.isDigit() }?.toIntOrNull()?.takeIf { it in 1..5 },
            website = tag("website") ?: tag("contact:website"),
            phone = tag("phone") ?: tag("contact:phone"),
        )
    }

    /** Familienfreundlich zuerst: Ferienwohnung/Pension/Hotel vor Motel/Hostel, bekannte Kontaktdaten, dann nah am Ziel. Dubletten entfernt. */
    fun rank(options: List<LodgingOption>, target: LatLon, limit: Int = 12): List<LodgingOption> {
        fun score(o: LodgingOption): Double {
            val kindScore = when (o.kind) {
                LodgingKind.APARTMENT -> 3.0
                LodgingKind.GUEST_HOUSE -> 2.5
                LodgingKind.HOTEL -> 2.5
                LodgingKind.CAMPING -> 1.5
                LodgingKind.MOTEL -> 1.0
                LodgingKind.HOSTEL -> 0.5
            }
            val contact = (if (o.website != null) 0.5 else 0.0) + (if (o.phone != null) 0.3 else 0.0)
            val km = haversine(target, o.position) / 1000.0
            return kindScore + contact + (o.stars ?: 0) * 0.1 - km * 0.15
        }
        val out = ArrayList<LodgingOption>()
        for (o in options.sortedWith(compareByDescending<LodgingOption> { score(it) }.thenBy { it.name })) {
            if (out.none { it.name.equals(o.name, ignoreCase = true) && haversine(it.position, o.position) < 200.0 }) out += o
            if (out.size >= limit) break
        }
        return out
    }
}
