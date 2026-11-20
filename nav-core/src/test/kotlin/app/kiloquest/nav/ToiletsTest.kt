package app.kiloquest.nav

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ToiletsTest {
    private val pos = LatLon(51.0, 7.0)
    private fun stop(kind: ToiletKind, dLat: Double, name: String? = null) = ToiletStop(kind, name, LatLon(pos.lat + dLat, pos.lon))

    @Test fun parsesKindsCentersAndSkipsPrivate() {
        val body = """{"elements":[
          {"type":"node","lat":51.001,"lon":7.0,"tags":{"amenity":"toilets","name":"WC Park"}},
          {"type":"way","center":{"lat":51.002,"lon":7.001},"tags":{"highway":"services","name":"Raststaette Nord"}},
          {"type":"node","lat":51.003,"lon":7.0,"tags":{"amenity":"toilets","access":"customers"}},
          {"type":"node","lat":51.004,"lon":7.0,"tags":{"amenity":"fuel","name":"Aral"}},
          {"type":"node","lat":51.005,"lon":7.0,"tags":{"shop":"bakery"}}]}"""
        val r = ToiletFinder.parse(body)
        assertEquals(listOf(ToiletKind.TOILETS, ToiletKind.SERVICES, ToiletKind.FUEL), r.map { it.kind })
        assertEquals(51.002, r[1].position.lat)
        assertEquals("WC Park", r[0].name)
    }

    @Test fun queryContainsAllKinds() {
        val q = ToiletFinder.buildQuery(pos, 8000)
        assertTrue("amenity=toilets" in q && "highway=rest_area" in q && "highway=services" in q && "amenity=fuel" in q && "around:8000" in q)
    }

    @Test fun nearestDedupesAndLimits() {
        val list = listOf(stop(ToiletKind.TOILETS, 0.0001), stop(ToiletKind.TOILETS, 0.00011), stop(ToiletKind.REST_AREA, 0.01), stop(ToiletKind.FUEL, 0.02))
        val n = ToiletFinder.nearest(list, pos, 2)
        assertEquals(2, n.size)
        assertEquals(ToiletKind.TOILETS, n[0].kind)
        assertEquals(ToiletKind.REST_AREA, n[1].kind)
    }

    @Test fun matrixRoundTrip() {
        val targets = listOf(stop(ToiletKind.TOILETS, 0.01), stop(ToiletKind.FUEL, 0.02), stop(ToiletKind.SERVICES, 0.03))
        val req = ToiletFinder.matrixRequest(pos, targets)
        assertTrue("\"sources\"" in req && "\"targets\"" in req && req.count { it == '{' } >= 5)
        val body = """{"sources_to_targets":[[{"distance":1.0,"time":300,"to_index":0},{"distance":2.0,"time":null,"to_index":1},{"distance":3.0,"time":120.5,"to_index":2}]]}"""
        assertEquals(listOf(300.0, null, 120.5), ToiletFinder.parseMatrix(body, 3))
        assertEquals(listOf(null, null), ToiletFinder.parseMatrix("""{"sources_to_targets":[]}""", 2))
    }

    @Test fun choosesByDriveTimeNotDistance() {
        // Naechste Luftlinie liegt hinter uns (Autobahn, lange Umwege), die zweite ist schneller erreichbar
        val a = stop(ToiletKind.TOILETS, 0.001)
        val b = stop(ToiletKind.SERVICES, 0.03)
        val c = ToiletFinder.choose(pos, listOf(a, b), listOf(900.0, 240.0))
        assertEquals(b, c!!.stop)
        assertEquals(240.0, c.seconds)
    }

    @Test fun fuelNeedsToBeClearlyFaster() {
        val toilets = stop(ToiletKind.TOILETS, 0.01)
        val fuel = stop(ToiletKind.FUEL, 0.005)
        assertEquals(toilets, ToiletFinder.choose(pos, listOf(toilets, fuel), listOf(200.0, 150.0))!!.stop) // 150+90 > 200
        assertEquals(fuel, ToiletFinder.choose(pos, listOf(toilets, fuel), listOf(400.0, 100.0))!!.stop)
    }

    @Test fun unreachableIsSkippedAndOfflineFallsBackToDistance() {
        val near = stop(ToiletKind.TOILETS, 0.001)
        val far = stop(ToiletKind.TOILETS, 0.02)
        assertEquals(far, ToiletFinder.choose(pos, listOf(near, far), listOf(null, 500.0))!!.stop)
        assertNull(ToiletFinder.choose(pos, listOf(near), listOf(null)))
        val offline = ToiletFinder.choose(pos, listOf(far, near), null)
        assertEquals(near, offline!!.stop)
        assertNull(offline.seconds)
        assertNotNull(ToiletFinder.choose(pos, listOf(near), emptyList()))
    }

    // Gerade Route von Sueden nach Norden, ca. 22 km lang (0,2 Grad Breite)
    private val route = Route(Polyline((0..20).map { LatLon(51.0 + it * 0.01, 7.0) }), emptyList(), 22_000.0, 1_000.0)
    private fun at(kind: ToiletKind, lat: Double, lonOffsetMeters: Double = 0.0) =
        ToiletStop(kind, null, LatLon(lat, 7.0 + lonOffsetMeters / (111_320.0 * Math.cos(Math.toRadians(lat)))))

    @Test fun routeQueryCoversTheRouteAheadWithCorridor() {
        val q = ToiletFinder.buildRouteQuery(route, 5_000.0, 15_000.0, 2000)
        assertTrue("around:2000" in q && "amenity=toilets" in q && "highway=services" in q)
        assertTrue("51.04" in q && "51.13" in q, "points along the window: $q")
        assertTrue("51.00" !in q.substringAfter("around:2000,").substringBefore(")"), "nothing behind the window start")
    }

    @Test fun onlyStopsAheadWithinWindowAndCorridorCount() {
        val behind = at(ToiletKind.TOILETS, 51.02)        // ca. 2,2 km vom Start, wir sind bei 5 km
        val near = at(ToiletKind.TOILETS, 51.06)          // vorn
        val farOff = at(ToiletKind.REST_AREA, 51.08, 3500.0) // zu weit von der Route
        val tooFar = at(ToiletKind.SERVICES, 51.19)       // ausserhalb des Fensters
        val list = ToiletFinder.ahead(listOf(behind, near, farOff, tooFar), route, 5_000.0, 12_000.0, 2_000.0)
        assertEquals(listOf(near), list.map { it.stop })
        assertTrue(list[0].aheadMeters in 1_200.0..2_200.0, "ahead=${list[0].aheadMeters}")
    }

    @Test fun nearerAheadWinsAndBigDetoursRankLower() {
        val a = at(ToiletKind.TOILETS, 51.07, 0.0)        // ca. 7,8 km, auf der Route
        val b = at(ToiletKind.TOILETS, 51.065, 1800.0)    // etwas frueher, aber 1,8 km Umweg (zaehlt doppelt)
        val c = at(ToiletKind.TOILETS, 51.10, 0.0)        // spaeter
        val ranked = ToiletFinder.ahead(listOf(c, b, a), route, 5_000.0, 20_000.0, 2_500.0)
        assertEquals(listOf(a.position, b.position, c.position), ranked.map { it.stop.position })
        assertTrue(ranked[1].offsetMeters in 1_500.0..2_100.0)
    }

    @Test fun duplicatesAreMerged() {
        val x = at(ToiletKind.TOILETS, 51.06); val y = at(ToiletKind.TOILETS, 51.06005)
        assertEquals(1, ToiletFinder.ahead(listOf(x, y), route, 0.0, 20_000.0, 1_000.0).size)
    }
}
