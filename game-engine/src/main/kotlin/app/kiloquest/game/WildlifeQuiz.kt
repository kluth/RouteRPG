package app.kiloquest.game

import kotlin.math.roundToLong
import kotlin.random.Random

/**
 * Beobachtete Art (z. B. aus GBIF). [key] ist der String-Key-Teil (`species_<key>`), [scientificName] der Rueckfall.
 * [displayName] ist ein optionaler Name aus den Daten (sprachabhaengig, nicht vertrauenswuerdig fuer i18n).
 */
data class WildlifeSpecies(val scientificName: String, val displayName: String? = null, val license: String = "") {
    val key: String get() = scientificName.trim().lowercase().replace(Regex("[^a-z0-9]+"), "_").trim('_')
    val nameKey: String get() = "species_$key"
}

data class WildlifeQuestion(
    val textKey: String,
    val options: List<WildlifeSpecies>,
    val correctIndex: Int,
) {
    val correct: WildlifeSpecies get() = options[correctIndex]
    fun isCorrect(index: Int) = index == correctIndex
}

object WildlifeQuiz {
    const val QUESTION_KEY = "wildlife_q_what_lives_here"
    const val OPTIONS = 3

    /** Offline-Rueckfall: verbreitete europaeische Arten (nur wissenschaftliche Namen; Texte ueber `species_<key>`). */
    val FALLBACK: List<WildlifeSpecies> = listOf(
        "Vulpes vulpes", "Sciurus vulgaris", "Erinaceus europaeus", "Capreolus capreolus", "Sus scrofa",
        "Lepus europaeus", "Meles meles", "Cervus elaphus", "Turdus merula", "Erithacus rubecula",
        "Pica pica", "Parus major", "Passer domesticus", "Ciconia ciconia", "Buteo buteo",
        "Cygnus olor", "Anas platyrhynchos", "Apis mellifera", "Coccinella septempunctata", "Rana temporaria",
    ).map { WildlifeSpecies(it, license = "CC0_1_0") }

    private val CC_BY_VERSION = Regex("^cc by( \\d+( \\d+)?)?$")
    private val CC_URL = Regex("creativecommons org licenses by \\d+( \\d+)?")

    /** Erlaubt: CC0 und CC BY (ohne NC/SA/ND). GBIF-Schreibweise (CC0_1_0, CC_BY_4_0), Text oder URL. */
    fun isAllowedLicense(license: String?): Boolean {
        val n = license?.lowercase()?.replace(Regex("[^a-z0-9]+"), " ")?.trim().orEmpty()
        if (n.isEmpty()) return false
        if (n == "cc0" || n.startsWith("cc0 ")) return true
        if (CC_BY_VERSION.matches(n)) return true
        if (n.contains("creativecommons org publicdomain zero")) return true
        return CC_URL.containsMatchIn(n) && !n.contains("by nc") && !n.contains("by sa") && !n.contains("by nd")
    }

    fun filterLicensed(species: List<WildlifeSpecies>): List<WildlifeSpecies> = species.filter { isAllowedLicense(it.license) }

    /** Datenschutz: Koordinaten auf 0,1 Grad (ca. 11 km) runden. */
    fun roundCoord(deg: Double): Double = (deg * 10.0).roundToLong() / 10.0

    /**
     * Fragen "Was lebt hier?". Richtige Antworten aus [observed] (nach Lizenzfilter, ohne Duplikate),
     * Distraktoren aus [fallback]/[observed]. Ohne Beobachtungen kommen richtige Antworten aus dem Rueckfall.
     */
    fun generate(observed: List<WildlifeSpecies>, seed: Long, count: Int, fallback: List<WildlifeSpecies> = FALLBACK): List<WildlifeQuestion> {
        val rnd = Random(seed)
        val pool = filterLicensed(observed).distinctBy { it.key }
        val answers = (pool.ifEmpty { fallback }).shuffled(rnd).take(count.coerceAtLeast(0))
        val all = (pool + fallback).distinctBy { it.key }
        return answers.map { correct ->
            val distractors = all.filter { it.key != correct.key }.shuffled(rnd).take(OPTIONS - 1)
            val options = (distractors + correct).shuffled(rnd)
            WildlifeQuestion(QUESTION_KEY, options, options.indexOfFirst { it.key == correct.key })
        }
    }
}
