package app.kiloquest.nav

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DayPlannerTest {
    private fun route(km: Double, hours: Double): Route {
        // gerade Linie nach Norden, ~111.19 km je Grad
        val steps = 100
        val pts = (0..steps).map { LatLon(50.0 + (km / 111.19) * it / steps, 8.0) }
        val line = Polyline(pts)
        return Route(line, emptyList(), line.lengthMeters, hours * 3600.0)
    }

    @Test fun shortRouteIsSingleDay() {
        val cuts = DayPlanner.split(route(300.0, 3.5), 8 * 3600.0)
        assertEquals(1, cuts.size)
        assertTrue(cuts.single().isFinal)
    }

    @Test fun longRouteSplitsEvenly() {
        val cuts = DayPlanner.split(route(2000.0, 24.0), 8 * 3600.0)
        assertEquals(3, cuts.size)
        assertTrue(cuts.last().isFinal)
        cuts.forEach { assertEquals(8 * 3600.0, it.stageSeconds, 1.0) }
        assertEquals(cuts.last().alongMeters / 3, cuts[0].alongMeters, 1.0)
    }

    @Test fun noTinyLastDay() {
        // 8.5 h bei 8 h Budget -> 2 Tage zu je 4.25 h
        val cuts = DayPlanner.split(route(800.0, 8.5), 8 * 3600.0)
        assertEquals(2, cuts.size)
        assertEquals(4.25 * 3600, cuts[1].stageSeconds, 1.0)
    }

    @Test fun exactBudgetIsOneDay() = assertEquals(1, DayPlanner.dayCount(8 * 3600.0, 8 * 3600.0))

    @Test fun budgetHasMinimum() = assertEquals(3, DayPlanner.dayCount(6 * 3600.0, 60.0))

    @Test fun pickOvernightPrefersBigTownInWindow() {
        val a = DayPlanner.Candidate("Dorf", LatLon(0.0, 0.0), 100_000.0, 1)
        val b = DayPlanner.Candidate("Stadt", LatLon(0.0, 0.0), 120_000.0, 3)
        val c = DayPlanner.Candidate("Weit", LatLon(0.0, 0.0), 300_000.0, 5)
        assertEquals("Stadt", DayPlanner.pickOvernight(listOf(a, b, c), 105_000.0, 30_000.0)?.name)
        assertNull(DayPlanner.pickOvernight(listOf(c), 105_000.0, 30_000.0))
    }

    @Test fun pickOvernightIsDeterministicOnTies() {
        val a = DayPlanner.Candidate("B", LatLon(0.0, 0.0), 100_000.0, 2)
        val b = DayPlanner.Candidate("A", LatLon(0.0, 0.0), 100_000.0, 2)
        assertEquals("A", DayPlanner.pickOvernight(listOf(a, b), 100_000.0, 10_000.0)?.name)
    }
}
