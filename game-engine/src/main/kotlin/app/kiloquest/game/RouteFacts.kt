package app.kiloquest.game

import kotlin.random.Random
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

enum class FactKind(val textKey: String) {
    RIVER("fact_river"),
    LAKE("fact_lake"),
    CASTLE("fact_castle"),
    PEAK("fact_peak"),
    BRIDGE("fact_bridge"),
}

data class RouteFact(val kind: FactKind, val name: String)

/**
 * Streckenbezogene Aufgaben aus OpenStreetMap (Overpass): Fluesse, Seen, Burgen, Gipfel, Bruecken
 * in der Naehe der aktuellen Position. Kein Netzwerkcode hier, nur Anfrage bauen und Antwort lesen.
 */
object RouteFacts {
    /** Schluessel fuer Platzhalter-Quest und Rueckfall (Texte in den String-Ressourcen). */
    const val PENDING_KEY = "fact_pending"
    const val FALLBACK_KEY = "fact_fallback"

    val textKeys: List<String> = FactKind.entries.map { it.textKey } + listOf(PENDING_KEY, FALLBACK_KEY)

    private val json = Json { ignoreUnknownKeys = true }

    fun buildQuery(lat: Double, lon: Double, radiusMeters: Int = 3000): String {
        fun q(filter: String) = "nwr(around:$radiusMeters,$lat,$lon)$filter[name];"
        return "[out:json][timeout:10];(" +
            q("[waterway=river]") + q("[natural=water][water~\"lake|reservoir\"]") +
            q("[historic=castle]") + q("[natural=peak]") + q("[man_made=bridge]") +
            ");out tags 40;"
    }

    fun parse(body: String): List<RouteFact> {
        val resp = json.decodeFromString<Response>(body)
        return resp.elements.mapNotNull { e ->
            val name = e.tags["name"]?.trim().orEmpty()
            if (name.isEmpty()) return@mapNotNull null
            val kind = when {
                e.tags["waterway"] == "river" -> FactKind.RIVER
                e.tags["natural"] == "water" -> FactKind.LAKE
                e.tags["historic"] == "castle" -> FactKind.CASTLE
                e.tags["natural"] == "peak" -> FactKind.PEAK
                e.tags["man_made"] == "bridge" -> FactKind.BRIDGE
                else -> return@mapNotNull null
            }
            RouteFact(kind, name)
        }.distinct()
    }

    /** Waehlt einen Fakt; seltene Arten (Burg, Gipfel) bevorzugt, damit es spannend bleibt. */
    fun pick(facts: List<RouteFact>, random: Random): RouteFact? {
        if (facts.isEmpty()) return null
        val rare = facts.filter { it.kind == FactKind.CASTLE || it.kind == FactKind.PEAK }
        return (rare.ifEmpty { facts }).let { it[random.nextInt(it.size)] }
    }

    /** Baut aus der Platzhalter-Quest eine konkrete Quest (Fakt oder Rueckfall). */
    fun resolve(placeholder: Quest, fact: RouteFact?): Quest =
        if (fact != null) {
            placeholder.copy(type = QuestType.ROUTE_FACT, textKey = fact.kind.textKey, args = listOf(fact.name))
        } else {
            placeholder.copy(type = QuestType.ROUTE_FACT, textKey = FALLBACK_KEY, args = emptyList())
        }

    @Serializable private data class Response(val elements: List<Element> = emptyList())
    @Serializable private data class Element(val tags: Map<String, String> = emptyMap())
}
