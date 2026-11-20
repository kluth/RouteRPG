package app.kiloquest.nav

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

enum class LaneHint { LEFT, MIDDLE, RIGHT, ANY }

/** Eine Fahrspur vor einer Kreuzung. [valid] = fuehrt zum Manoever, [active] = empfohlene Spur. */
data class Lane(val indications: List<String>, val valid: Boolean, val active: Boolean) {
    /** Pfeilsymbol fuer die Anzeige (bevorzugt die gueltige Richtung). */
    val symbol: String get() = laneSymbol(indications.firstOrNull() ?: "none")
}

fun laneSymbol(indication: String): String = when (indication) {
    "left" -> "←"
    "slight left" -> "↖"
    "sharp left" -> "↙"
    "straight" -> "↑"
    "right" -> "→"
    "slight right" -> "↗"
    "sharp right" -> "↘"
    "uturn" -> "↶"
    else -> "·"
}

/** Spurinformation an einem Manoever (Streckenmeter ab Routenstart). */
data class LaneGuidance(val atMeters: Double, val lanes: List<Lane>) {
    /** Wo liegen die gueltigen Spuren? ANY, wenn alle oder keine gueltig sind oder nur eine Spur existiert. */
    fun hint(): LaneHint {
        val n = lanes.size
        val valid = lanes.indices.filter { lanes[it].valid }
        if (n < 2 || valid.isEmpty() || valid.size == n) return LaneHint.ANY
        val mean = valid.average()
        val center = (n - 1) / 2.0
        return when {
            mean < center - 0.25 -> LaneHint.LEFT
            mean > center + 0.25 -> LaneHint.RIGHT
            else -> LaneHint.MIDDLE
        }
    }
}

/** Liest Spuren aus Valhallas OSRM-Antwort (`format: osrm`) und ordnet sie der nativen Route zu. */
object LaneParser {
    private val json = Json { ignoreUnknownKeys = true }

    fun parseOsrm(body: String, route: Route, maxOffsetMeters: Double = 40.0): List<LaneGuidance> {
        val legs = (json.parseToJsonElement(body).jsonObject["routes"] as? JsonArray)
            ?.firstOrNull()?.jsonObject?.get("legs") as? JsonArray ?: return emptyList()
        val result = ArrayList<LaneGuidance>()
        var from = 0
        for (leg in legs) {
            for (step in (leg.jsonObject["steps"] as? JsonArray).orEmpty()) {
                val st = step.jsonObject
                val man = st["maneuver"]?.jsonObject ?: continue
                val type = man["type"]?.jsonPrimitive?.contentOrNull
                if (type == "depart" || type == "arrive") continue
                // Spuren am Manoeverpunkt = erste Kreuzung des Schritts
                val lanesJson = ((st["intersections"] as? JsonArray)?.firstOrNull()?.jsonObject?.get("lanes") as? JsonArray)
                    ?: continue
                val loc = man["location"] as? JsonArray ?: continue
                val lon = loc.getOrNull(0)?.jsonPrimitive?.doubleOrNull ?: continue
                val lat = loc.getOrNull(1)?.jsonPrimitive?.doubleOrNull ?: continue
                val lanes = lanesJson.map { l -> toLane(l.jsonObject) }
                if (lanes.size < 2) continue
                val proj = route.line.project(LatLon(lat, lon), fromSegment = from, window = 2_000)
                    .let { if (it.offsetMeters > maxOffsetMeters) route.line.project(LatLon(lat, lon)) else it }
                if (proj.offsetMeters > maxOffsetMeters) continue
                from = proj.segmentIndex
                result += LaneGuidance(proj.alongMeters, lanes)
            }
        }
        return result.sortedBy { it.atMeters }
    }

    private fun toLane(o: JsonObject): Lane {
        val ind = (o["indications"] as? JsonArray).orEmpty().map { it.jsonPrimitive.content }
        val valid = o["valid"]?.jsonPrimitive?.booleanOrNull ?: false
        val active = o["active"]?.jsonPrimitive?.booleanOrNull ?: false
        // Gueltige Richtung zuerst anzeigen
        val vi = o["valid_indication"]?.jsonPrimitive?.contentOrNull
        val ordered = if (vi != null && vi in ind) listOf(vi) + ind.filter { it != vi } else ind
        return Lane(ordered, valid, active)
    }
}

/** Sagt Spurhinweise genau einmal an, wenn das Manoever naeher als [distanceMeters] ist. */
class LaneAnnouncer(private val lanes: List<LaneGuidance>, private val distanceMeters: Double = 700.0) {
    private val spoken = HashSet<Double>()

    /** Naechste faellige Spurinformation mit eindeutigem Hinweis (nicht ANY) oder null. */
    fun next(alongMeters: Double): LaneGuidance? {
        val g = lanes.firstOrNull { it.atMeters - alongMeters in -5.0..distanceMeters && it.hint() != LaneHint.ANY && it.atMeters !in spoken }
            ?: return null
        spoken += g.atMeters
        return g
    }

    /** Aktuell anzuzeigende Spurinformation (naechstes Manoever mit Spuren innerhalb [showMeters]). */
    fun current(alongMeters: Double, showMeters: Double = 900.0): LaneGuidance? =
        lanes.firstOrNull { it.atMeters - alongMeters in -5.0..showMeters }
}
