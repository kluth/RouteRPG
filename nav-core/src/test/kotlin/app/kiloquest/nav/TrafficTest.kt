package app.kiloquest.nav

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TrafficTest {
    // Gekuerzte echte Antwort der Autobahn-API (Struktur unveraendert)
    private val warning = """{"warning":[{"identifier":"INRIX--1","isBlocked":"false","future":false,
      "title":"A1 | Remscheid-Lennep - Kucksiepen","delayTimeValue":"31",
      "coordinate":{"lat":51.2274,"long":7.2347},
      "description":["Zusammengesetzte Verkehrsinformation","Ereignismeldung:","- Unfall","- Fahrstreifen gesperrt","- Reisezeitverlust: 31 Minuten"],
      "geometry":{"type":"LineString","coordinates":[[7.2347,51.2274],[7.2346,51.2275],[7.2338,51.2280]]}},
      {"identifier":"X--2","isBlocked":"false","future":true,"title":"A1 | zukuenftig","coordinate":{"lat":51.0,"long":7.0},"description":["Stau"]},
      {"identifier":"X--3","isBlocked":"true","future":false,"title":"A7 | Sperrung","coordinate":{"lat":53.0,"long":9.9},"description":["Vollsperrung"]}]}"""
    private val closure = """{"closure":[{"identifier":"C--1","isBlocked":"false","future":false,
      "title":"A1 | Nonnweiler-Bierfeld - Steinerwald","coordinate":{"lat":49.6148,"long":6.9547},"description":["Baustelle"]}]}"""

    @Test
    fun `parses warnings with type delay and geometry`() {
        val list = AutobahnApi.parse(warning, AutobahnApi.WARNING)
        assertEquals(3, list.size)
        val a = list[0]
        assertEquals(IncidentType.ACCIDENT, a.type)
        assertEquals(31, a.delayMinutes)
        assertEquals("A1", a.road)
        assertEquals(3, a.geometry.size)
        assertTrue(list[1].inFuture)
        assertEquals(IncidentType.CLOSURE, list[2].type) // isBlocked=true
        assertEquals(1, list[2].geometry.size)           // nur coordinate
    }

    @Test
    fun `closure service gives closure type`() {
        assertEquals(IncidentType.CLOSURE, AutobahnApi.parse(closure, AutobahnApi.CLOSURE).single().type)
    }

    @Test
    fun `bad or empty bodies give empty list`() {
        assertTrue(AutobahnApi.parse("""{"warning":[]}""", AutobahnApi.WARNING).isEmpty())
        assertTrue(AutobahnApi.parse("""{"other":1}""", AutobahnApi.WARNING).isEmpty())
    }

    @Test
    fun `extracts autobahn refs from street names`() {
        val names = listOf("A 1", "Bundesautobahn 7", "Hauptstraße", "A 40 / E 34", "E 45", "A7")
        assertEquals(listOf("A1", "A7", "A40"), AutobahnApi.roadsFromNames(names))
    }

    // Route gerade nach Norden entlang lon 8.0
    private val route: Route = run {
        val pts = (0..40).map { LatLon(50.0 + it * 0.001, 8.0) } // ca. 4.4 km
        val line = Polyline(pts)
        Route(line, emptyList(), line.lengthMeters, 300.0)
    }

    private fun inc(id: String, vararg pts: LatLon, delay: Int? = 20, blocked: Boolean = false, type: IncidentType = IncidentType.JAM) =
        TrafficIncident(id, type, "A1", "t", "x", delay, blocked, pts.toList(), false)

    @Test
    fun `matches incident ahead in driving direction`() {
        val i = inc("a", LatLon(50.01, 8.0), LatLon(50.015, 8.0))
        val r = IncidentMatcher.match(route, listOf(i), progressMeters = 100.0).single()
        assertTrue(r.aheadMeters in 900.0..1100.0, "ahead=${r.aheadMeters}")
        assertTrue(r.lengthMeters in 500.0..600.0)
    }

    @Test
    fun `ignores opposite direction and off route incidents`() {
        val opposite = inc("o", LatLon(50.015, 8.0), LatLon(50.01, 8.0))
        val off = inc("f", LatLon(50.01, 8.01), LatLon(50.015, 8.01))
        assertTrue(IncidentMatcher.match(route, listOf(opposite, off), 0.0).isEmpty())
    }

    @Test
    fun `ignores incidents already behind us`() {
        val behind = inc("b", LatLon(50.001, 8.0), LatLon(50.002, 8.0))
        assertTrue(IncidentMatcher.match(route, listOf(behind), progressMeters = 3000.0).isEmpty())
    }

    @Test
    fun `advisor picks significant incidents once`() {
        val adv = RerouteAdvisor(minDelayMinutes = 10)
        val small = RouteIncident(inc("s", LatLon(50.01, 8.0), delay = 3), 1000.0, 0.0, 1000.0)
        val big = RouteIncident(inc("g", LatLon(50.02, 8.0), delay = 25), 2000.0, 0.0, 2000.0)
        val closed = RouteIncident(inc("c", LatLon(50.03, 8.0), delay = null, blocked = true, type = IncidentType.CLOSURE), 3000.0, 0.0, 3000.0)
        assertEquals("g", adv.next(listOf(small, big, closed))?.incident?.id)
        adv.markConsidered("g")
        assertEquals("c", adv.next(listOf(small, big, closed))?.incident?.id)
        adv.markConsidered("c")
        assertNull(adv.next(listOf(small, big, closed)))
    }

    @Test
    fun `advisor ignores far away incidents`() {
        val adv = RerouteAdvisor(lookAheadMeters = 50_000.0)
        val far = RouteIncident(inc("far", LatLon(50.0, 8.0), delay = 60), 90_000.0, 0.0, 90_000.0)
        assertNull(adv.next(listOf(far)))
    }

    @Test
    fun `exclusion zones are closed rings around the incident`() {
        val i = inc("z", LatLon(50.0, 8.0), LatLon(50.005, 8.0), LatLon(50.01, 8.0))
        val zones = ExclusionZones.forIncident(i)
        assertTrue(zones.size in 2..8)
        for (ring in zones) {
            assertEquals(5, ring.size)
            assertEquals(ring.first(), ring.last())
        }
    }

    @Test
    fun `valhalla request includes exclude polygons`() {
        val i = inc("z", LatLon(50.0, 8.0))
        val req = ValhallaApi.buildRouteRequest(LatLon(50.0, 7.9), LatLon(51.0, 8.1), "de-DE", excludePolygons = ExclusionZones.forIncident(i))
        assertTrue("exclude_polygons" in req)
        assertNotNull(ValhallaApi.buildRouteRequest(LatLon(1.0, 1.0), LatLon(2.0, 2.0), "en-US"))
    }
}

/** Echte (gekuerzte) Antworten der Autobahn-API vom 20.11.2026 als Regressionstest fuer den Parser. */
class AutobahnRealDataTest {
    private fun res(name: String) = javaClass.classLoader.getResource(name)!!.readText()

    @Test
    fun `parses real warning response`() {
        val list = AutobahnApi.parse(res("autobahn_a1_warning.json"), AutobahnApi.WARNING)
        assertEquals(6, list.size)
        assertTrue(list.all { it.geometry.isNotEmpty() && it.road == "A1" })
        assertTrue(list.any { (it.delayMinutes ?: 0) > 0 })
    }

    @Test
    fun `parses real closure response`() {
        val list = AutobahnApi.parse(res("autobahn_a1_closure.json"), AutobahnApi.CLOSURE)
        assertEquals(6, list.size)
        assertTrue(list.all { it.type == IncidentType.CLOSURE && it.geometry.isNotEmpty() })
    }
}

class TripTest {
    private fun res(name: String) = javaClass.classLoader.getResource(name)!!.readText()

    @Test
    fun `request contains start and every stop in order`() {
        val req = TripApi.buildRequest(LatLon(51.0, 6.0), listOf(LatLon(52.0, 7.0), LatLon(53.0, 8.0)), "de-DE")
        val lats = Regex("\"lat\":([0-9.]+)").findAll(req).map { it.groupValues[1].toDouble() }.toList()
        assertEquals(listOf(51.0, 52.0, 53.0), lats)
    }

    @Test
    fun `parses legs of a real three stop trip`() {
        val legs = TripApi.parseLegs(res("valhalla_trip_3stops.json"))
        assertEquals(2, legs.size)
        assertEquals(24_794.0, legs[0].lengthMeters, 1.0)
        assertEquals(37_641.0, legs[1].lengthMeters, 1.0)
        val total = TripApi.totals(legs)
        assertEquals(62_435.0, total.lengthMeters, 2.0)
        assertTrue(total.durationSeconds > 3700)
    }

    @Test
    fun `long stage detection and empty responses`() {
        assertTrue(TripApi.isLongStage(LegSummary(700_000.0, 9 * 3600.0)))
        assertTrue(!TripApi.isLongStage(LegSummary(300_000.0, 4 * 3600.0)))
        assertTrue(TripApi.parseLegs("""{"error":"x"}""").isEmpty())
    }
}

class RiddleTest {
    private val ok = Riddle("r1", "Wie heißt unser Hund?", listOf("Bello", "Rex", "Luna"), 2, atKm = 30.0)

    @Test
    fun `valid riddle has no errors`() {
        assertTrue(RiddleRules.validate(ok).isEmpty())
        assertTrue(RiddleRules.validate(ok.copy(atKm = null, near = LatLon(51.0, 6.0), nearName = "Oma")).isEmpty())
    }

    @Test
    fun `invalid riddles are reported`() {
        assertTrue("question_empty" in RiddleRules.validate(ok.copy(question = " ")))
        assertTrue("options_missing" in RiddleRules.validate(ok.copy(options = listOf("a", "b"))))
        assertTrue("options_missing" in RiddleRules.validate(ok.copy(options = listOf("a", "", "c"))))
        assertTrue("options_not_distinct" in RiddleRules.validate(ok.copy(options = listOf("Rex", "rex", "Luna"))))
        assertTrue("correct_missing" in RiddleRules.validate(ok.copy(correctIndex = 5)))
        assertTrue("trigger_missing" in RiddleRules.validate(ok.copy(atKm = null)))
        assertTrue("question_too_long" in RiddleRules.validate(ok.copy(question = "x".repeat(200))))
    }

    @Test
    fun `due by distance only after the km and only once`() {
        val pos = LatLon(50.0, 8.0)
        assertTrue(!RiddleRules.due(ok, 29_000.0, pos, emptySet()))
        assertTrue(RiddleRules.due(ok, 30_100.0, pos, emptySet()))
        assertTrue(!RiddleRules.due(ok, 40_000.0, pos, setOf("r1")))
    }

    @Test
    fun `due by place within radius`() {
        val r = ok.copy(atKm = null, near = LatLon(50.0, 8.0))
        assertTrue(RiddleRules.due(r, 0.0, LatLon(50.005, 8.0), emptySet()))   // ca. 550 m
        assertTrue(!RiddleRules.due(r, 0.0, LatLon(50.05, 8.0), emptySet()))   // ca. 5.5 km
    }
}

class CorridorTest {
    // ca. 300 km nach Norden von Duisburg aus
    private val route: Route = run {
        val pts = (0..3000).map { LatLon(51.4 + it * 0.0009, 6.76) }
        val line = Polyline(pts)
        Route(line, emptyList(), line.lengthMeters, 3 * 3600.0)
    }

    @Test
    fun `splits the route into chunk boxes that cover all points`() {
        val boxes = Corridor.boxes(route, chunkMeters = 60_000.0, padKm = 10.0)
        assertTrue(boxes.size in 4..7, "boxes=${boxes.size}")
        for (p in route.line.points.filterIndexed { i, _ -> i % 50 == 0 }) {
            assertTrue(boxes.any { p.lat in it.south..it.north && p.lon in it.west..it.east }, "uncovered $p")
        }
        assertTrue(boxes.all { it.south < it.north && it.west < it.east })
    }

    @Test
    fun `padding widens the box`() {
        val small = Corridor.boxes(route, padKm = 2.0).first()
        val large = Corridor.boxes(route, padKm = 20.0).first()
        assertTrue(large.east - large.west > small.east - small.west)
    }

    @Test
    fun `tile counts grow with zoom and are one tile at zoom 0`() {
        val b = Corridor.boxes(route).first()
        assertEquals(1L, Corridor.tilesAt(b, 0))
        assertTrue(Corridor.tilesAt(b, 12) > Corridor.tilesAt(b, 8))
        val total = Corridor.estimateTiles(Corridor.boxes(route), 5, 12)
        assertTrue(total in 200L..20_000L, "tiles=$total")
        assertTrue(Corridor.estimateMegabytes(total) > 1.0)
    }

    @Test
    fun `sample positions include start and end`() {
        val s = Corridor.samplePositions(route, 25_000.0)
        assertEquals(0.0, s.first().first)
        assertEquals(route.line.lengthMeters, s.last().first, 1.0)
        assertTrue(s.size in 10..16)
    }

    @Test
    fun `country timeline answers by position`() {
        val t = CountryTimeline(listOf(0.0 to "DE", 100_000.0 to "DK", 300_000.0 to "SE"))
        assertEquals("DE", t.countryAt(50_000.0))
        assertEquals("DK", t.countryAt(100_000.0))
        assertEquals("SE", t.countryAt(900_000.0))
        assertNull(CountryTimeline(emptyList()).countryAt(1.0))
    }
}

class CarMapHelpersTest {
    private val line = Polyline((0..1000).map { LatLon(50.0 + it * 0.0001, 8.0) }) // ca. 11 km

    @Test
    fun `maps valhalla types to kinds`() {
        assertEquals(ManeuverKind.LEFT, ManeuverKind.fromValhalla(15))
        assertEquals(ManeuverKind.RIGHT, ManeuverKind.fromValhalla(10))
        assertEquals(ManeuverKind.RAMP_RIGHT, ManeuverKind.fromValhalla(18)) // Auffahrt rechts, wie in echter Antwort
        assertEquals(ManeuverKind.ROUNDABOUT, ManeuverKind.fromValhalla(26))
        assertEquals(ManeuverKind.DESTINATION_RIGHT, ManeuverKind.fromValhalla(5))
        assertEquals(ManeuverKind.UNKNOWN, ManeuverKind.fromValhalla(99))
    }

    @Test
    fun `parsed real route exposes maneuver kinds`() {
        val route = ValhallaApi.parseRoute(javaClass.classLoader.getResource("valhalla_route_speed.json")!!.readText())
        assertTrue(route.maneuvers.any { it.kind == ManeuverKind.LEFT })
        assertEquals(ManeuverKind.DESTINATION, route.maneuvers.last().kind)
    }

    @Test
    fun `slice returns the stretch between two positions`() {
        val s = line.slice(1_000.0, 3_000.0)
        assertTrue(s.first().lat in 50.008..50.0095)
        assertTrue(s.last().lat in 50.027..50.0285)
        assertTrue(s.size in 150..200, "size=${s.size}")
    }

    @Test
    fun `slice thins out long stretches but keeps both ends`() {
        val s = line.slice(0.0, 1e9, maxPoints = 50)
        assertEquals(50, s.size)
        assertEquals(line.points.first(), s.first())
        assertEquals(line.points.last(), s.last())
    }

    @Test
    fun `slice at the very end returns a point`() {
        assertEquals(1, line.slice(1e9, 1e9 + 10).size)
    }
}
