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

/** Bereits gebuchte Unterkunft: feste Uebernachtung, die die Aufteilung respektieren muss. */
data class BookedStay(val name: String, val position: LatLon)

enum class DayEnd { BOOKED, COMPUTED, FINAL }

/** Tagesziel mit Art; [offRouteMeters] = Abstand der gebuchten Unterkunft zur Route (0 bei berechneten Punkten). */
data class PlannedDay(
    val day: Int,
    val end: DayEnd,
    val alongMeters: Double,
    val point: LatLon,
    val name: String?,
    val stageSeconds: Double,
    val offRouteMeters: Double,
    /** Etappe laenger als das Fahrzeit-Budget (nur bei festen Unterkuenften unvermeidbar). */
    val overBudget: Boolean,
)

object BookedPlanner {
    private class Anchor(val along: Double, val stay: BookedStay?, val offRoute: Double)

    /** Gebuchte Unterkuenfte weiter als das von der Route entfernt gelten als Umweg (Warnung). */
    const val FAR_OFF_ROUTE_METERS = 40_000.0
    private const val EDGE_METERS = 2_000.0

    /**
     * Teilt die Route so, dass jede gebuchte Unterkunft ein Tagesende ist (Reihenfolge entlang der Route, nicht der Eingabe).
     * Zwischen zwei festen Punkten wird bei Bedarf gleichmaessig zusaetzlich geteilt. Unterkuenfte direkt am Start oder Ziel
     * werden ignoriert. Zwei Unterkuenfte am selben Routenpunkt ergeben nur einen Tag.
     */
    fun plan(route: Route, booked: List<BookedStay>, budgetSeconds: Double): List<PlannedDay> {
        val total = route.lengthMeters
        val secPerMeter = if (total > 0.0) route.durationSeconds / total else 0.0
        val fixed = booked
            .map { b -> Anchor(route.line.project(b.position).alongMeters, b, route.line.project(b.position).offsetMeters) }
            .filter { it.along > EDGE_METERS && it.along < total - EDGE_METERS }
            .sortedBy { it.along }
            .fold(ArrayList<Anchor>()) { acc, t ->
                if (acc.isEmpty() || t.along - acc.last().along > EDGE_METERS) acc += t
                acc
            }
        val anchors = fixed + Anchor(total, null, 0.0)
        val out = ArrayList<PlannedDay>()
        var prev = 0.0
        var day = 1
        for (anchor in anchors) {
            val along = anchor.along
            val stay = anchor.stay
            val off = anchor.offRoute
            val segSec = (along - prev) * secPerMeter
            val n = DayPlanner.dayCount(segSec, budgetSeconds)
            for (i in 1 until n) {
                val a = prev + (along - prev) * i / n
                out += PlannedDay(day++, DayEnd.COMPUTED, a, route.line.points[route.line.indexAtMeters(a)], null, segSec / n, 0.0, false)
            }
            val end = if (stay == null) DayEnd.FINAL else DayEnd.BOOKED
            val point = if (stay != null) stay.position else route.line.points.last()
            out += PlannedDay(day++, end, along, point, stay?.name, segSec / n, off, segSec / n > budgetSeconds + 60.0)
            prev = along
        }
        return out
    }
}
