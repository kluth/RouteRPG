package app.kiloquest.nav

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class NavTest {
    @Test
    fun `decodes known google polyline with precision 5`() {
        // Beispiel aus der Google-Dokumentation
        val pts = PolylineCodec.decode("_p~iF~ps|U_ulLnnqC_mqNvxq`@", precision = 5)
        assertEquals(3, pts.size)
        assertEquals(38.5, pts[0].lat, 1e-5)
        assertEquals(-120.2, pts[0].lon, 1e-5)
        assertEquals(43.252, pts[2].lat, 1e-5)
        assertEquals(-126.453, pts[2].lon, 1e-5)
    }

    @Test
    fun `haversine one degree latitude is about 111 km`() {
        val d = haversine(LatLon(0.0, 0.0), LatLon(1.0, 0.0))
        assertEquals(111_195.0, d, 100.0)
    }

    // Gerade Strecke nach Norden, ca. 2 km, Manoever bei 1 km
    private fun straightRoute(): Route {
        val pts = (0..20).map { LatLon(50.0 + it * 0.001, 8.0) }
        val line = Polyline(pts)
        val m = listOf(
            Maneuver(1, "Fahre los", null, null, emptyList(), 0.0, 1000.0),
            Maneuver(10, "Biege rechts ab", "In 500 Metern rechts abbiegen", null, emptyList(), line.cumulative[10], 1000.0),
            Maneuver(4, "Ziel erreicht", null, null, emptyList(), line.lengthMeters, 0.0),
        )
        return Route(line, m, line.lengthMeters, 120.0)
    }

    @Test
    fun `projection finds position along and offset`() {
        val r = straightRoute()
        val p = r.line.project(LatLon(50.005, 8.0005))
        assertEquals(r.line.cumulative[5], p.alongMeters, 1.0)
        assertTrue(p.offsetMeters in 30.0..40.0) // 0.0005 deg lon bei 50N ~ 35.8 m
    }

    @Test
    fun `guidance tracks progress and next maneuver`() {
        val r = straightRoute()
        val g = GuidanceEngine(r)
        val s = g.update(LatLon(50.0055, 8.0))
        assertFalse(s.isOffRoute)
        assertEquals(1, s.nextManeuverIndex)
        assertTrue(s.metersToNextManeuver in 480.0..520.0)
    }

    @Test
    fun `off route detection and no backwards progress`() {
        val r = straightRoute()
        val g = GuidanceEngine(r)
        g.update(LatLon(50.008, 8.0))
        val back = g.update(LatLon(50.006, 8.0))
        assertTrue(back.alongMeters >= r.line.cumulative[8] - 1.0)
        val off = g.update(LatLon(50.008, 8.01))
        assertTrue(off.isOffRoute)
    }

    @Test
    fun `arrival detected`() {
        val r = straightRoute()
        val g = GuidanceEngine(r)
        val s = g.update(LatLon(50.02, 8.0))
        assertTrue(s.arrived)
    }

    @Test
    fun `announcements fire once at pre and now distances`() {
        val r = straightRoute()
        val g = GuidanceEngine(r)
        val planner = AnnouncementPlanner(r)
        assertNull(planner.next(g.update(LatLon(50.002, 8.0)))) // zu weit
        assertEquals("In 500 Metern rechts abbiegen", planner.next(g.update(LatLon(50.006, 8.0))))
        assertNull(planner.next(g.update(LatLon(50.0065, 8.0)))) // nicht doppelt
        assertEquals("Biege rechts ab", planner.next(g.update(LatLon(50.0095, 8.0))))
    }

    @Test
    fun `builds and parses valhalla route`() {
        val req = ValhallaApi.buildRouteRequest(LatLon(52.5, 13.4), LatLon(48.1, 11.6), "de-DE")
        assertTrue(req.contains("\"language\":\"de-DE\""))
        val body = """{"trip":{"status":0,"summary":{"length":1.0,"time":60.0},
          "legs":[{"shape":"_p~iF~ps|U_ulLnnqC_mqNvxq`@","maneuvers":[
          {"type":1,"instruction":"Los","begin_shape_index":0,"length":0.5},
          {"type":4,"instruction":"Ziel","begin_shape_index":2,"length":0.0}]}]}}"""
        // Shape in Praezision 6 dekodiert ergibt Mini-Koordinaten, reicht fuer Strukturtest
        val route = ValhallaApi.parseRoute(body)
        assertEquals(2, route.maneuvers.size)
        assertEquals(1000.0, route.lengthMeters, 0.001)
        assertNotNull(route.maneuvers[1].atMeters)
    }
}
