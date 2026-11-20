package app.kiloquest.game

import java.net.URLEncoder
import java.util.Locale
import kotlin.random.Random
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

enum class SpeciesGroup { BIRD, MAMMAL, OTHER }

data class Species(val name: String, val scientific: String, val group: SpeciesGroup, val count: Int)

/**
 * Tier-Aufgaben aus Beobachtungen der Umgebung (iNaturalist, nur "research grade"):
 * "Hier sieht man oft: Elch. Entdeckt ihr einen?" Kein Netzwerkcode hier.
 */
object NatureSpotting {
    const val PENDING_KEY = "nature_pending"
    const val BIRD_KEY = "nature_bird"
    const val MAMMAL_KEY = "nature_mammal"
    const val SOURCE_KEY = "nature_source"

    val textKeys: List<String> = listOf(PENDING_KEY, BIRD_KEY, MAMMAL_KEY, SOURCE_KEY)

    private val json = Json { ignoreUnknownKeys = true }

    fun buildUrl(lat: Double, lon: Double, lang: String, radiusKm: Int = 25, limit: Int = 30): String {
        fun e(s: String) = URLEncoder.encode(s, "UTF-8")
        fun d(v: Double) = String.format(Locale.ROOT, "%.4f", v)
        val params = listOf(
            "lat" to d(lat), "lng" to d(lon), "radius" to radiusKm.toString(),
            "locale" to WikiStories.safeLang(lang), "quality_grade" to "research",
            "iconic_taxa" to "Aves,Mammalia", "per_page" to limit.toString(),
        )
        return "https://api.inaturalist.org/v1/observations/species_counts?" +
            params.joinToString("&") { (k, v) -> "$k=${e(v)}" }
    }

    fun parse(body: String): List<Species> =
        json.decodeFromString<Response>(body).results.mapNotNull { r ->
            val t = r.taxon ?: return@mapNotNull null
            val name = t.preferredCommonName?.trim().orEmpty()
            if (name.isEmpty()) return@mapNotNull null
            val group = when (t.iconicTaxonName) {
                "Aves" -> SpeciesGroup.BIRD
                "Mammalia" -> SpeciesGroup.MAMMAL
                else -> SpeciesGroup.OTHER
            }
            if (group == SpeciesGroup.OTHER) null else Species(name, t.name.orEmpty(), group, r.count)
        }

    /**
     * Zufallsauswahl, gewichtet nach Haeufigkeit (leichter zu sehen); Saeugetiere sind spannender
     * und zaehlen doppelt. Ohne Treffer null.
     */
    fun pick(species: List<Species>, random: Random): Species? {
        if (species.isEmpty()) return null
        val weights = species.map { it.count.coerceAtLeast(1) * (if (it.group == SpeciesGroup.MAMMAL) 2 else 1) }
        var r = random.nextInt(weights.sum())
        for ((i, w) in weights.withIndex()) { if (r < w) return species[i]; r -= w }
        return species.last()
    }

    fun resolve(placeholder: Quest, species: Species?): Quest =
        if (species != null) {
            val key = if (species.group == SpeciesGroup.BIRD) BIRD_KEY else MAMMAL_KEY
            placeholder.copy(type = QuestType.NATURE, textKey = key, args = listOf(species.name))
        } else {
            placeholder.copy(type = QuestType.NATURE, textKey = RouteFacts.FALLBACK_KEY, args = emptyList())
        }

    @Serializable private data class Response(val results: List<Result> = emptyList())
    @Serializable private data class Result(val count: Int = 0, val taxon: Taxon? = null)
    @Serializable private data class Taxon(
        val name: String? = null,
        @kotlinx.serialization.SerialName("preferred_common_name") val preferredCommonName: String? = null,
        @kotlinx.serialization.SerialName("iconic_taxon_name") val iconicTaxonName: String? = null,
    )
}
