package app.kiloquest.nav

/**
 * Eigenes Raetsel von Eltern (nur lokal gespeichert): Frage mit drei Antworten, ausgeloest
 * nach [atKm] Fahrstrecke ab Etappenstart ODER in der Naehe von [near] (Radius [RiddleRules.NEAR_METERS]).
 */
data class Riddle(
    val id: String,
    val question: String,
    val options: List<String>,
    val correctIndex: Int,
    val atKm: Double? = null,
    val near: LatLon? = null,
    val nearName: String? = null,
)

object RiddleRules {
    const val NEAR_METERS = 1_500.0
    const val MAX_QUESTION = 160
    const val MAX_OPTION = 40

    /** Fehlerliste; leer = gueltig. Die Texte sind technische Schluessel, die Oberflaeche uebersetzt sie. */
    fun validate(r: Riddle): List<String> = buildList {
        if (r.question.isBlank()) add("question_empty")
        if (r.question.length > MAX_QUESTION) add("question_too_long")
        if (r.options.size != 3 || r.options.any { it.isBlank() }) add("options_missing")
        if (r.options.any { it.length > MAX_OPTION }) add("option_too_long")
        if (r.options.map { it.trim().lowercase() }.toSet().size != r.options.size) add("options_not_distinct")
        if (r.correctIndex !in r.options.indices) add("correct_missing")
        if (r.atKm == null && r.near == null) add("trigger_missing")
        if (r.atKm != null && r.atKm < 0) add("km_negative")
    }

    /** Ist das Raetsel jetzt faellig (noch nicht benutzt, Strecke erreicht oder Ort nah genug)? */
    fun due(r: Riddle, alongMeters: Double, position: LatLon, used: Set<String>): Boolean {
        if (r.id in used) return false
        val byKm = r.atKm != null && alongMeters >= r.atKm * 1000.0
        val byPlace = r.near != null && haversine(position, r.near) <= NEAR_METERS
        return byKm || byPlace
    }
}
