package app.kiloquest.nav

import kotlin.math.ceil
import kotlin.math.max

/** Ein Tagesziel (Uebernachtung oder Ende) auf einer Route. */
data class DayCut(
    /** 1-basierter Tag, an dessen Ende dieser Punkt erreicht wird. */
    val day: Int,
    val alongMeters: Double,
    val point: LatLon,
    /** Fahrzeit dieser Etappe in Sekunden (anteilig nach Strecke). */
    val stageSeconds: Double,
    val isFinal: Boolean,
)

/** Teilt eine lange Route in gleichmaessige Tagesetappen (reine Logik, deterministisch). */
object DayPlanner {
    const val MIN_BUDGET_SECONDS = 2 * 3600.0
    const val MAX_DAYS = 30

    /** Anzahl Tage bei Fahrzeit-Budget je Tag. */
    fun dayCount(totalSeconds: Double, budgetSeconds: Double): Int {
        if (totalSeconds <= 0.0) return 1
        val b = max(budgetSeconds, MIN_BUDGET_SECONDS)
        return ceil(totalSeconds / b - 1e-9).toInt().coerceIn(1, MAX_DAYS)
    }

    /**
     * Gleichmaessig verteilt: n Tage, jede Etappe gleich lang (keine winzige letzte Etappe).
     * Zeit wird anteilig zur Strecke verteilt. Letzter Eintrag ist das Ziel.
     */
    fun split(route: Route, budgetSeconds: Double): List<DayCut> {
        val total = route.lengthMeters
        val pts = route.line.points
        val n = dayCount(route.durationSeconds, budgetSeconds)
        val stageSec = route.durationSeconds / n
        return (1..n).map { d ->
            val final = d == n
            val along = if (final) total else total * d / n
            val point = if (final) pts.last() else pts[route.line.indexAtMeters(along)]
            DayCut(d, along, point, stageSec, final)
        }
    }

    /**
     * Waehlt aus Kandidaten (z. B. Orte mit Unterkunft) den besten fuer ein Tagesziel:
     * innerhalb von [windowMeters] um die Zielposition, bevorzugt gross (hoeheres [Candidate.score]), dann nah.
     * Gibt null zurueck, wenn niemand im Fenster liegt (dann bleibt der rechnerische Punkt).
     */
    fun pickOvernight(candidates: List<Candidate>, targetAlong: Double, windowMeters: Double): Candidate? =
        candidates
            .filter { kotlin.math.abs(it.alongMeters - targetAlong) <= windowMeters }
            .sortedWith(compareByDescending<Candidate> { it.score }.thenBy { kotlin.math.abs(it.alongMeters - targetAlong) }.thenBy { it.name })
            .firstOrNull()

    data class Candidate(val name: String, val position: LatLon, val alongMeters: Double, val score: Int)
}
