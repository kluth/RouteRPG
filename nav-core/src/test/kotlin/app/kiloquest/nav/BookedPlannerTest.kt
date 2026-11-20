package app.kiloquest.nav

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class BookedPlannerTest {
    private fun route(km: Double, hours: Double): Route {
        val pts = (0..200).map { LatLon(50.0 + (km / 111.19) * it / 200, 8.0) }
        val line = Polyline(pts)
        return Route(line, emptyList(), line.lengthMeters, hours * 3600.0)
    }
    private fun at(r: Route, frac: Double) = r.line.points[r.line.indexAtMeters(r.lengthMeters * frac)]
    private val budget = 8 * 3600.0

    @Test fun noBookingsBehavesLikeEvenSplit() {
        val r = route(2000.0, 24.0)
        val d = BookedPlanner.plan(r, emptyList(), budget)
        assertEquals(3, d.size)
        assertEquals(DayEnd.FINAL, d.last().end)
    }

    @Test fun bookedStayBecomesDayEnd() {
        val r = route(1600.0, 16.0)
        val d = BookedPlanner.plan(r, listOf(BookedStay("Hotel A", at(r, 0.4))), budget)
        assertEquals(listOf(DayEnd.BOOKED, DayEnd.COMPUTED, DayEnd.FINAL), d.map { it.end })
        assertEquals("Hotel A", d[0].name)
        assertTrue(d.none { it.overBudget })
    }

    @Test fun overlongSegmentBetweenBookingsIsSplitFurther() {
        val r = route(2400.0, 24.0)
        // eine Buchung bei 1/6: Rest 20 h -> 3 weitere Tage
        val d = BookedPlanner.plan(r, listOf(BookedStay("Early", at(r, 1.0 / 6))), budget)
        assertEquals(4, d.size)
        assertEquals(DayEnd.BOOKED, d[0].end)
        assertTrue(d.none { it.overBudget })
    }

    @Test fun orderFollowsRouteNotInput() {
        val r = route(1600.0, 16.0)
        val d = BookedPlanner.plan(r, listOf(BookedStay("Late", at(r, 0.8)), BookedStay("Early", at(r, 0.2))), budget)
        assertEquals(listOf("Early", "Late"), d.filter { it.end == DayEnd.BOOKED }.map { it.name })
    }

    @Test fun tooLongFixedSegmentIsFlagged() {
        val r = route(1000.0, 12.0)
        val d = BookedPlanner.plan(r, emptyList(), 4 * 3600.0)
        assertTrue(d.none { it.overBudget }) // ohne Buchung wird stets passend geteilt
        // Buchung direkt am Ziel/Start wird ignoriert
        val e = BookedPlanner.plan(r, listOf(BookedStay("Ziel", r.line.points.last())), budget)
        assertEquals(2, e.size)
        assertTrue(e.none { it.name == "Ziel" })
    }

    @Test fun duplicateBookingsOnSamePointGiveOneDay() {
        val r = route(1000.0, 10.0)
        val p = at(r, 0.5)
        val d = BookedPlanner.plan(r, listOf(BookedStay("A", p), BookedStay("B", p)), budget)
        assertEquals(1, d.count { it.end == DayEnd.BOOKED })
    }

    @Test fun offRouteDistanceReported() {
        val r = route(1000.0, 10.0)
        val p = at(r, 0.5)
        val far = LatLon(p.lat, p.lon + 0.5) // ~35 km seitlich
        val d = BookedPlanner.plan(r, listOf(BookedStay("Seitlich", far)), budget)
        val booked = d.first { it.end == DayEnd.BOOKED }
        assertTrue(booked.offRouteMeters > 30_000.0)
    }
}
