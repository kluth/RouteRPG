package app.kiloquest.nav

import kotlin.math.max

enum class IssueKind { OVERLAP, TOO_FEW_DAYS }

/** [missingDays] nur bei TOO_FEW_DAYS; [legIndex] = Etappe (0 = Start bis erste Buchung). */
data class ItineraryIssue(val kind: IssueKind, val legIndex: Int, val missingDays: Int = 0)

/** Eine Etappe zwischen zwei festen Punkten (Start, Buchung, Ziel). */
data class LegPlan(
    val index: Int,
    val fromName: String,
    val toName: String,
    val lengthMeters: Double,
    val durationSeconds: Double,
    /** Benoetigte Fahrtage laut Tagesbudget. */
    val driveDays: Int,
    /** Erste Fahrt (Epochentag) und Ankunft. */
    val departDay: Int,
    val arriveDay: Int,
    /** Freie Tage, die vor der Abfahrt am vorigen Ort bleiben (nur wenn Check-in spaeter liegt). */
    val spareDays: Int,
    /** Tage (Epochentag), an deren Ende unterwegs uebernachtet wird und noch keine Buchung vorliegt. */
    val roadNights: List<Int>,
)

data class Itinerary(val legs: List<LegPlan>, val issues: List<ItineraryIssue>, val arrivalDay: Int) {
    val ok: Boolean get() = issues.isEmpty()
    val totalRoadNights: Int get() = legs.sumOf { it.roadNights.size }
}

/** Terminplanung einer Reise mit festen Buchungen. Reine Logik, deterministisch. */
object ItineraryPlanner {
    /**
     * [legs] = Etappen in Reihenfolge (Start->Buchung 1, ..., letzte Buchung->Ziel), also stays.size + 1 Eintraege.
     * Die Buchungen werden nach Check-in sortiert; [legs] muessen zu dieser Reihenfolge passen.
     */
    fun build(
        startDay: Int,
        originName: String,
        destinationName: String,
        stays: List<PlanStay>,
        legs: List<LegSummary>,
        budgetSeconds: Double,
    ): Itinerary {
        val sorted = stays.sortedWith(compareBy({ it.checkInDay }, { it.name }))
        require(legs.size == sorted.size + 1) { "legs must be stays+1" }
        val issues = ArrayList<ItineraryIssue>()
        val legPlans = ArrayList<LegPlan>()
        var departDay = startDay
        var fromName = originName
        for (i in legs.indices) {
            val next = sorted.getOrNull(i)
            val toName = next?.name ?: destinationName
            val leg = legs[i]
            val need = DayPlanner.dayCount(leg.durationSeconds, budgetSeconds)
            if (next != null) {
                if (next.checkInDay < departDay) issues += ItineraryIssue(IssueKind.OVERLAP, i)
                val available = next.checkInDay - departDay + 1
                val arrive = next.checkInDay
                val drive = if (available >= need) arrive - need + 1 else departDay
                val missing = max(0, need - max(available, 0))
                if (missing > 0 && next.checkInDay >= departDay) issues += ItineraryIssue(IssueKind.TOO_FEW_DAYS, i, missing)
                val spare = max(0, available - need)
                val nights = (0 until need - 1).map { drive + it }.filter { it < arrive }
                legPlans += LegPlan(i, fromName, toName, leg.lengthMeters, leg.durationSeconds, need, drive, arrive, spare, nights)
                departDay = next.checkOutDay
                fromName = next.name
            } else {
                val arrive = departDay + need - 1
                legPlans += LegPlan(i, fromName, toName, leg.lengthMeters, leg.durationSeconds, need, departDay, arrive, 0, (0 until need - 1).map { departDay + it })
            }
        }
        return Itinerary(legPlans, issues, legPlans.last().arriveDay)
    }

    /** Zusatzfahrzeit der Route ueber alle Buchungen gegenueber der direkten Route (nie negativ). */
    fun extraSeconds(viaSeconds: Double, directSeconds: Double): Double = max(0.0, viaSeconds - directSeconds)

    /** Abstand jeder Buchung zur direkten Route in Metern, in der Reihenfolge von [stays]. */
    fun offRouteMeters(direct: Polyline, stays: List<PlanStay>): List<Double> = stays.map { direct.project(it.position).offsetMeters }
}
