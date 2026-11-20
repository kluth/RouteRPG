package app.kiloquest.nav

import kotlin.math.PI
import kotlin.math.sin
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CurvesBreaksTest {
    private fun routeOf(pts: List<LatLon>): Route {
        val line = Polyline(pts)
        return Route(line, emptyList(), line.lengthMeters, 600.0)
    }

    private val straight = routeOf((0..300).map { LatLon(50.0 + it * 0.0002, 8.0) }) // ca. 6.7 km nach Norden

    // Schlangenlinie: Sinus entlang Norden, ca. 6.7 km
    private val winding = routeOf((0..300).map { i ->
        LatLon(50.0 + i * 0.0002, 8.0 + 0.0015 * sin(i * 2 * PI / 20.0))
    })

    @Test
    fun `straight road is not curvy`() {
        val c = CurveProfile(straight)
        assertEquals(0.0, c.degreesPerKm(1000.0), 1e-6)
        assertTrue(!c.isCurvy(1000.0))
    }

    @Test
    fun `winding road is curvy`() {
        val c = CurveProfile(winding)
        assertTrue(c.degreesPerKm(1000.0) > 150.0, "dpk=${c.degreesPerKm(1000.0)}")
        assertTrue(c.isCurvy(1000.0))
    }

    @Test
    fun `single sharp turn at an intersection does not count`() {
        // erst 3 km nach Norden, dann scharf 90 Grad nach Osten
        val pts = (0..150).map { LatLon(50.0 + it * 0.0002, 8.0) } + (1..150).map { LatLon(50.03, 8.0 + it * 0.0003) }
        assertTrue(!CurveProfile(routeOf(pts)).isCurvy(2800.0))
    }

    @Test
    fun `sickness coach waits for sustained curves and rate limits`() {
        val c = SicknessCoach(minCurvyMs = 20_000, promptEveryMs = 180_000)
        assertTrue(!c.onTick(true, 0))
        assertTrue(!c.onTick(true, 10_000))
        assertTrue(c.onTick(true, 21_000))
        assertTrue(!c.onTick(true, 60_000))
        assertTrue(!c.onTick(false, 70_000))
        assertTrue(!c.onTick(true, 80_000))
        assertTrue(c.onTick(true, 205_000)) // >= 180 s seit letztem Hinweis
    }

    // ---- Rastplaetze
    private val sampleOverpass = """{"elements":[
      {"type":"node","id":1,"lat":50.0100,"lon":8.0004,"tags":{"highway":"rest_area","name":"Rastplatz Nord"}},
      {"type":"way","id":2,"center":{"lat":50.0102,"lon":8.0006},"tags":{"leisure":"playground"}},
      {"type":"node","id":3,"lat":50.0101,"lon":8.0003,"tags":{"amenity":"toilets"}},
      {"type":"node","id":4,"lat":50.0300,"lon":8.0002,"tags":{"highway":"services","name":"Raststaette Sued"}},
      {"type":"node","id":5,"lat":50.0500,"lon":8.0500,"tags":{"leisure":"playground","name":"zu weit weg"}},
      {"type":"node","id":6,"lat":50.0100,"lon":8.0004,"tags":{"highway":"rest_area"}},
      {"type":"node","id":7,"lat":50.0200,"lon":8.0003,"tags":{"shop":"kiosk"}}]}"""

    private val longRoute = routeOf((0..400).map { LatLon(50.0 + it * 0.0002, 8.0) }) // ca. 8.9 km

    @Test
    fun `builds an overpass line query`() {
        val q = RestStops.buildQuery(longRoute, 1000.0, 5000.0)
        assertTrue("around:500," in q && "[highway=rest_area]" in q && "[leisure=playground]" in q && "out center" in q)
    }

    @Test
    fun `parses stops, links playground and toilets, drops far and duplicate ones`() {
        val stops = RestStops.parse(sampleOverpass, longRoute, fromMeters = 0.0, toMeters = 9000.0)
        val rest = stops.first { it.name == "Rastplatz Nord" }
        assertTrue(rest.playgroundNearby && rest.toiletsNearby)
        assertTrue(stops.none { it.name == "zu weit weg" })
        assertEquals(1, stops.count { it.kind == StopKind.REST_AREA }) // Dublette entfernt
        assertTrue(stops.any { it.kind == StopKind.SERVICES })
    }

    @Test
    fun `picks the best stop near the target`() {
        val stops = RestStops.parse(sampleOverpass, longRoute, 0.0, 9000.0)
        val best = RestStops.pick(stops, targetAlongMeters = 1000.0)
        assertEquals("Rastplatz Nord", best?.name) // Spielplatz + WC in der Naehe
        assertNull(RestStops.pick(emptyList(), 0.0))
        assertNotNull(RestStops.pick(stops, 3300.0))
    }

    // ---- Pausenplaner
    @Test
    fun `break becomes due after the interval of driving`() {
        val b = BreakPlanner(intervalMs = 60 * 60_000L)
        var due = false
        var t = 0L
        while (t < 61 * 60_000L && !due) { due = b.onTick(t, 90.0); t += 30_000 }
        assertTrue(due)
        assertTrue(!b.onTick(t, 90.0)) // nur einmal
    }

    @Test
    fun `stopping for five minutes resets the timer`() {
        val b = BreakPlanner(intervalMs = 60 * 60_000L)
        var t = 0L
        repeat(60) { b.onTick(t, 100.0); t += 30_000 } // 30 min fahren
        assertTrue(b.drivingMs > 25 * 60_000L)
        repeat(12) { b.onTick(t, 0.0); t += 30_000 }   // 6 min stehen
        assertEquals(0L, b.drivingMs)
    }

    @Test
    fun `snooze brings the reminder back after the given time`() {
        val b = BreakPlanner(intervalMs = 60 * 60_000L)
        var t = 0L
        while (!b.onTick(t, 90.0)) t += 30_000
        b.snooze(10 * 60_000L)
        var due = false
        var extra = 0L
        while (extra < 12 * 60_000L && !due) { t += 30_000; extra += 30_000; due = b.onTick(t, 90.0) }
        assertTrue(due && extra in 9 * 60_000L..11 * 60_000L, "extra=$extra")
    }
}
