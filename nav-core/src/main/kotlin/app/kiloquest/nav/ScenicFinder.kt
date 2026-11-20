package app.kiloquest.nav

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/** Eine Einschaetzung der Umgebung: [score] 0..5 (3 und mehr = schoene Gegend), [name] des markantesten Ortes, falls bekannt. */
data class Scenic(val score: Int, val name: String?) {
    companion object { val NONE = Scenic(0, null) }
}

/** Erkennt schoene Gegend aus OpenStreetMap-Tags rund um die Position (Overpass). Kein Netzwerkcode hier. */
object ScenicFinder {
    private val json = Json { ignoreUnknownKeys = true }

    fun buildQuery(lat: Double, lon: Double, radiusMeters: Int = 1500): String {
        val c = "${"%.5f".format(java.util.Locale.ROOT, lat)},${"%.5f".format(java.util.Locale.ROOT, lon)}"
        fun q(filter: String) = "nwr(around:$radiusMeters,$c)$filter;"
        return "[out:json][timeout:15];(" + q("[tourism=viewpoint]") + q("[natural~\"^(peak|glacier|cliff|beach|bay|coastline|water)$\"]") +
            q("[waterway=waterfall]") + q("[historic~\"^(castle|ruins)$\"]") + ");out center tags 40;"
    }

    private fun weight(tags: Map<String, String>): Int {
        if (tags["tourism"] == "viewpoint") return 3
        if (tags["waterway"] == "waterfall") return 3
        when (tags["natural"]) {
            "peak", "glacier" -> return 3
            "cliff", "beach", "bay", "coastline" -> return 2
            // Teiche gibt es ueberall: nur benannte Gewaesser zaehlen deutlich
            "water" -> return if (tags["name"] != null) 2 else 1
        }
        return when (tags["historic"]) { "castle" -> 2; "ruins" -> 1; else -> 0 }
    }

    /** Ergebnis: hoechstes Gewicht plus 1, wenn mehrere (mindestens 4) Orte da sind; hoechstens 5. */
    fun assess(body: String): Scenic {
        val elements = runCatching { json.parseToJsonElement(body).jsonObject["elements"] as? JsonArray }.getOrNull().orEmpty()
        var best = 0
        var bestName: String? = null
        var count = 0
        for (e in elements) {
            val tags = (e.jsonObject["tags"] as? kotlinx.serialization.json.JsonObject)?.mapValues { it.value.jsonPrimitive.content }.orEmpty()
            val w = weight(tags)
            if (w <= 0) continue
            count++
            if (w > best || (w == best && bestName == null)) { best = w; bestName = tags["name"] ?: bestName.takeIf { w == best } }
        }
        if (best == 0) return Scenic.NONE
        return Scenic((best + if (count >= 4) 1 else 0).coerceAtMost(5), bestName)
    }
}
