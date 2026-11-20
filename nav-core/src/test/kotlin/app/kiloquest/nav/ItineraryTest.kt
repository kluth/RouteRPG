package app.kiloquest.nav

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ItineraryTest {
    private val h = 3600.0
    private fun leg(hours: Double) = LegSummary(hours * 60_000.0, hours * h)
    private fun stay(name: String, checkIn: Int, nights: Int = 2) = PlanStay(name, 50.0, 8.0, checkIn, nights)

    @Test fun singleLegWithoutStays() {
        val it = ItineraryPlanner.build(100, "A", "B", emptyList(), listOf(leg(18.0)), 8 * h)
        assertEquals(3, it.legs.single().driveDays)
        assertEquals(listOf(100, 101), it.legs.single().roadNights)
        assertEquals(102, it.arrivalDay)
        assertTrue(it.ok)
    }

    @Test fun drivingEndsOnCheckInDayAndSpareDaysAreReported() {
        // 10 h = 2 Fahrtage, Check-in an Tag 110, Start Tag 100 -> 11 Tage verfuegbar
        val it = ItineraryPlanner.build(100, "A", "B", listOf(stay("Hotel", 110)), listOf(leg(10.0), leg(2.0)), 8 * h)
        val l0 = it.legs[0]
        assertEquals(109, l0.departDay)
        assertEquals(110, l0.arriveDay)
        assertEquals(9, l0.spareDays)
        assertEquals(listOf(109), l0.roadNights)
        // naechste Etappe startet am Check-out (110 + 2)
        assertEquals(112, it.legs[1].departDay)
        assertEquals(112, it.arrivalDay)
    }

    @Test fun tooFewDaysIsReported() {
        val it = ItineraryPlanner.build(100, "A", "B", listOf(stay("Hotel", 101)), listOf(leg(24.0), leg(1.0)), 8 * h)
        val issue = it.issues.single()
        assertEquals(IssueKind.TOO_FEW_DAYS, issue.kind)
        assertEquals(0, issue.legIndex)
        assertEquals(1, issue.missingDays) // 3 noetig, 2 verfuegbar
    }

    @Test fun overlapIsReported() {
        val stays = listOf(stay("A", 100, 5), stay("B", 102, 1))
        val it = ItineraryPlanner.build(99, "S", "Z", stays, listOf(leg(1.0), leg(1.0), leg(1.0)), 8 * h)
        assertTrue(it.issues.any { it.kind == IssueKind.OVERLAP && it.legIndex == 1 })
    }

    @Test fun staysAreSortedByCheckIn() {
        val stays = listOf(stay("Spaet", 120), stay("Frueh", 105))
        val it = ItineraryPlanner.build(100, "S", "Z", stays, listOf(leg(1.0), leg(1.0), leg(1.0)), 8 * h)
        assertEquals(listOf("Frueh", "Spaet", "Z"), it.legs.map { it.toName })
    }

    @Test fun sameDayArrivalIsOk() {
        val it = ItineraryPlanner.build(100, "A", "B", listOf(stay("H", 100)), listOf(leg(3.0), leg(1.0)), 8 * h)
        assertTrue(it.ok)
        assertEquals(100, it.legs[0].arriveDay)
        assertEquals(0, it.legs[0].spareDays)
    }

    @Test fun extraSecondsNeverNegative() {
        assertEquals(0.0, ItineraryPlanner.extraSeconds(100.0, 200.0))
        assertEquals(60.0, ItineraryPlanner.extraSeconds(260.0, 200.0))
    }

    @Test fun offRouteDistanceUsesDirectLine() {
        val line = Polyline((0..10).map { LatLon(50.0 + it * 0.1, 8.0) })
        val d = ItineraryPlanner.offRouteMeters(line, listOf(PlanStay("X", 50.5, 8.1, 1)))
        assertTrue(d.single() in 6_000.0..8_000.0)
    }

    @Test fun planStoreRoundTripAndBadInput() {
        val t = PlannedTrip("1", "Lappland", 20000, PlanPlace("Duisburg", 51.4, 6.7), PlanPlace("Rovaniemi", 66.5, 25.7), listOf(stay("H", 20003)))
        assertEquals(listOf(t), PlanStore.decode(PlanStore.encode(listOf(t))))
        assertEquals(emptyList(), PlanStore.decode("kaputt{"))
        assertEquals(emptyList(), PlanStore.decode(null))
    }

    @Test fun parseLegRoutesGivesOneRoutePerLeg() {
        fun shape(a: Double) = PolylineCodec.encode(listOf(LatLon(a, 8.0), LatLon(a + 0.1, 8.0)))
        val body = """{"trip":{"status":0,"summary":{"length":30.0,"time":2400.0},"legs":[
          {"shape":"${shape(50.0).replace("\\", "\\\\")}","summary":{"length":10.0,"time":600.0},"maneuvers":[]},
          {"shape":"${shape(51.0).replace("\\", "\\\\")}","summary":{"length":20.0,"time":1800.0},"maneuvers":[]}]}}"""
        val routes = ValhallaApi.parseLegRoutes(body)
        assertEquals(2, routes.size)
        assertEquals(20_000.0, routes[1].lengthMeters, 0.1)
        assertEquals(600.0, routes[0].durationSeconds, 0.1)
    }
}
