package app.kiloquest.nav

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class LanesSpeedTest {
    private fun res(name: String) = javaClass.classLoader.getResource(name)!!.readText()

    @Test
    fun `polyline encode decode roundtrip`() {
        val pts = listOf(LatLon(51.434412, 6.762312), LatLon(51.4350, 6.7640), LatLon(51.4300, 6.7700))
        val back = PolylineCodec.decode(PolylineCodec.encode(pts))
        assertEquals(pts.size, back.size)
        for (i in pts.indices) {
            assertEquals(pts[i].lat, back[i].lat, 1e-6)
            assertEquals(pts[i].lon, back[i].lon, 1e-6)
        }
    }

    @Test
    fun `indexAtMeters finds the right point`() {
        val line = Polyline((0..10).map { LatLon(50.0 + it * 0.001, 8.0) })
        assertEquals(0, line.indexAtMeters(0.0))
        assertEquals(5, line.indexAtMeters(line.cumulative[5] + 1.0))
        assertEquals(10, line.indexAtMeters(1e9))
    }

    @Test
    fun `lane hint detects left middle right and any`() {
        fun lanes(vararg valid: Boolean) = LaneGuidance(0.0, valid.map { Lane(listOf("straight"), it, it) })
        assertEquals(LaneHint.LEFT, lanes(true, false).hint())
        assertEquals(LaneHint.RIGHT, lanes(false, true).hint())
        assertEquals(LaneHint.MIDDLE, lanes(false, true, false).hint())
        assertEquals(LaneHint.LEFT, lanes(true, true, false).hint())
        assertEquals(LaneHint.ANY, lanes(true, true).hint())
        assertEquals(LaneHint.ANY, lanes(false, false).hint())
        assertEquals(LaneHint.ANY, lanes(true).hint())
    }

    @Test
    fun `lane symbols`() {
        assertEquals("←", laneSymbol("left"))
        assertEquals("↑", laneSymbol("straight"))
        assertEquals("·", laneSymbol("none"))
    }

    @Test
    fun `parses real osrm lanes and aligns them to the native route`() {
        val route = ValhallaApi.parseRoute(res("valhalla_route_lanes.json"))
        val lanes = LaneParser.parseOsrm(res("valhalla_osrm_lanes.json"), route)
        assertTrue(lanes.isNotEmpty(), "expected lane info on the sample route")
        assertTrue(lanes.all { it.atMeters in 0.0..route.lengthMeters && it.lanes.size >= 2 })
        assertEquals(lanes.map { it.atMeters }.sorted(), lanes.map { it.atMeters })
    }

    @Test
    fun `lane announcer speaks each hint once`() {
        val l1 = LaneGuidance(1000.0, listOf(Lane(listOf("left"), true, true), Lane(listOf("straight"), false, false)))
        val a = LaneAnnouncer(listOf(l1), distanceMeters = 700.0)
        assertNull(a.next(100.0))
        assertEquals(l1, a.next(400.0))
        assertNull(a.next(450.0))
        assertEquals(l1, a.current(450.0))
    }

    @Test
    fun `builds trace attributes request and reads real speed limits`() {
        val route = ValhallaApi.parseRoute(res("valhalla_route_speed.json"))
        val last = route.line.points.size - 1
        val req = TraceAttributes.buildRequest(route, 0, last)
        assertTrue("edge.speed_limit" in req && "edge_walk" in req)

        val segs = TraceAttributes.parse(res("valhalla_trace_speed.json"), route, startIndex = 0)
        assertTrue(segs.size > 50)
        val profile = SpeedProfile().also { it.addWindow(0.0, route.lengthMeters, segs) }
        assertEquals(50, profile.limitAt(10.0))
        assertTrue(profile.limitAt(route.lengthMeters / 2) != null || segs.any { it.limitKmh == null })
        assertTrue(segs.mapNotNull { it.limitKmh }.toSet().containsAll(setOf(50, 100)))
    }

    @Test
    fun `speed profile merges windows and handles unknown`() {
        val p = SpeedProfile()
        p.addWindow(0.0, 1000.0, listOf(SpeedSegment(0.0, 500.0, 50), SpeedSegment(500.0, 1000.0, null)))
        assertEquals(50, p.limitAt(100.0))
        assertNull(p.limitAt(700.0))
        p.addWindow(900.0, 2000.0, listOf(SpeedSegment(900.0, 2000.0, 80)))
        assertEquals(80, p.limitAt(1500.0))
        assertEquals(2000.0, p.coveredUntilMeters)
        assertNull(SpeedProfile().limitAt(10.0))
    }

    @Test
    fun `overspeed uses tolerance and ignores unlimited or unknown`() {
        assertTrue(SpeedProfile.isOverspeed(60.0, 50))
        assertTrue(!SpeedProfile.isOverspeed(52.0, 50))
        assertTrue(!SpeedProfile.isOverspeed(200.0, SpeedProfile.UNLIMITED))
        assertTrue(!SpeedProfile.isOverspeed(200.0, null))
    }

    @Test
    fun `osrm request flag is set`() {
        val r = ValhallaApi.buildRouteRequest(LatLon(1.0, 1.0), LatLon(2.0, 2.0), "de-DE", osrm = true)
        assertTrue("\"format\":\"osrm\"" in r && "banner_instructions" in r)
    }
}
