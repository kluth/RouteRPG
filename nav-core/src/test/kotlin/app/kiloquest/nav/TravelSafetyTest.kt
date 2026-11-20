package app.kiloquest.nav

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TravelSafetyTest {
    // Gerade Linie Richtung Norden, ca. 111 km je Grad
    private fun route(km: Double): Route {
        val line = Polyline(listOf(LatLon(50.0, 10.0), LatLon(50.0 + km / 111.195, 10.0)))
        return Route(line, emptyList(), line.lengthMeters, 3600.0)
    }

    @Test fun tollNoticesAreOrderedDistinctAndSkipFreeCountries() {
        val n = TollGuide.notices(listOf("de", "AT", "AT", "CH", "FI", "xx", "AT"))
        assertEquals(listOf("AT", "CH"), n.map { it.country })
        assertTrue(TollGuide.notices(listOf("DE", "FI")).isEmpty())
        assertEquals(TollType.SPECIAL, TollGuide.rule("se")?.type)
        assertNull(TollGuide.rule(null)); assertNull(TollGuide.rule("ZZ"))
        assertEquals("toll_at", TollGuide.textKey("AT"))
    }

    @Test fun tollUrlsAreHttps() {
        TollGuide.rules.values.forEach { r -> assertTrue(r.url == null || r.url.startsWith("https://"), r.country) }
    }

    @Test fun remoteGapsIncludeStartAndEnd() {
        val gaps = RemoteStretches.find(300_000.0, listOf(10_000.0, 20_000.0, 120_000.0, 130_000.0, 290_000.0))
        assertEquals(listOf(RemoteStretch(20_000.0, 120_000.0), RemoteStretch(130_000.0, 290_000.0)), gaps)
        assertEquals(100_000.0, gaps.first().lengthMeters)
        assertEquals(listOf(RemoteStretch(0.0, 80_000.0)), RemoteStretches.find(80_000.0, emptyList()))
        assertTrue(RemoteStretches.find(50_000.0, emptyList()).isEmpty())
    }

    @Test fun remoteQueryAndParse() {
        val r = route(100.0)
        val q = RemoteStretches.buildQuery(r, 0.0, 100_000.0)
        assertTrue(q.contains("around:6000") && q.contains("place"))
        val body = """{"elements":[{"type":"node","lat":50.3,"lon":10.0},{"type":"node","lat":50.2,"lon":10.01},{"type":"node","lat":50.5,"lon":11.5},{"type":"node","id":1}]}"""
        val places = RemoteStretches.parsePlaces(body, r)
        assertEquals(2, places.size) // der Ort bei lon 11.5 ist zu weit weg, der ohne Koordinaten faellt weg
        assertTrue(places[0] < places[1])
    }

    @Test fun remoteWarnerWarnsOncePerStretch() {
        val w = RemoteWarner(listOf(RemoteStretch(100_000.0, 200_000.0)), 25_000.0)
        assertNull(w.next(50_000.0))
        val (s, ahead) = w.next(80_000.0)!!
        assertEquals(100_000.0, s.startMeters); assertEquals(20_000.0, ahead, 0.1)
        assertNull(w.next(90_000.0))
    }

    @Test fun remoteWarnerStartedInsideAndNearEnd() {
        assertEquals(0.0, RemoteWarner(listOf(RemoteStretch(0.0, 100_000.0))).next(10_000.0)!!.second)
        assertNull(RemoteWarner(listOf(RemoteStretch(0.0, 100_000.0))).next(97_000.0)) // fast vorbei: nicht mehr warnen
    }

    @Test fun emergencyNumbers() {
        assertEquals("112", Emergency.numberFor("SE")); assertEquals("112", Emergency.numberFor(null))
        assertEquals("999", Emergency.numberFor("gb")); assertEquals("911", Emergency.numberFor("US"))
    }

    @Test fun locationTextHasCoordinatesAndLink() {
        val t = Emergency.locationText(LatLon(51.4321, 6.7612), "A40")
        assertTrue(t.startsWith("A40\n51.43210, 6.76120\n")); assertTrue("mlat=51.43210&mlon=6.76120" in t)
        assertTrue(Emergency.locationText(LatLon(1.0, 2.0), null).startsWith("1.00000, 2.00000"))
    }

    @Test fun hospitalsSortedByDistanceNamedOnly() {
        val body = """{"elements":[
            {"type":"node","lat":51.5,"lon":6.8,"tags":{"name":"Fern"}},
            {"type":"way","center":{"lat":51.44,"lon":6.77},"tags":{"name":"Nah"}},
            {"type":"node","lat":51.43,"lon":6.76,"tags":{}},
            {"type":"node","lat":51.44,"lon":6.77,"tags":{"name":"Nah"}}]}"""
        val h = Emergency.hospitalQuery(LatLon(51.43, 6.76)).let { assertTrue("amenity=hospital" in it); Emergency.parseHospitals(body, LatLon(51.43, 6.76)) }
        assertEquals(listOf("Nah", "Fern"), h.map { it.name })
        assertNotNull(h.first().distanceMeters)
    }

    @Test fun safetyVoiceCommands() {
        assertEquals(VoiceCommand.TOLLS, VoiceCommands.parse("Kilo, brauchen wir eine Vignette", true))
        assertEquals(VoiceCommand.TOLLS, VoiceCommands.parse("kilo maut", true))
        assertEquals(VoiceCommand.EMERGENCY, VoiceCommands.parse("Kilo, Notfall", true))
        assertEquals(VoiceCommand.EMERGENCY, VoiceCommands.parse("kilo emergency", true))
        assertNull(VoiceCommands.parse("Kilo, das ist toll", true))
        assertEquals(VoiceCommand.STOPOVER, VoiceCommands.parse("Kilo, ich möchte ein Eis", true))
        assertEquals(VoiceCommand.STOPOVER, VoiceCommands.parse("kilo spielplatz", true))
        assertNull(VoiceCommands.parse("Kilo, wir fahren nach Hamburg", true))
    }
}

class KidSpotsTest {
    private val route: Route = Polyline(listOf(LatLon(50.0, 10.0), LatLon(51.0, 10.0))).let { Route(it, emptyList(), it.lengthMeters, 3600.0) }
    private val now = java.time.LocalDateTime.of(2026, 7, 15, 12, 0) // Mittwoch

    private fun el(lat: Double, lon: Double, vararg tags: Pair<String, String>) =
        """{"type":"node","lat":$lat,"lon":$lon,"tags":{${tags.joinToString(",") { "\"${it.first}\":\"${it.second}\"" }}}}"""

    @Test fun parsesKindsAndDropsFarAndClosed() {
        val body = """{"elements":[
            ${el(50.3, 10.005, "leisure" to "playground", "name" to "Spielwiese")},
            ${el(50.5, 10.01, "amenity" to "ice_cream", "name" to "Eis Rossi", "opening_hours" to "Mo-Su 10:00-20:00")},
            ${el(50.6, 10.0, "amenity" to "ice_cream", "name" to "Zu", "opening_hours" to "Mo-Su 18:00-20:00")},
            ${el(50.7, 11.0, "tourism" to "zoo", "name" to "Zoo weit weg")},
            ${el(50.8, 10.0, "historic" to "castle", "name" to "Burg Eltz")},
            ${el(50.9, 10.0, "shop" to "bakery", "name" to "Baecker")}]}"""
        val s = KidSpots.parse(body, route, now)
        assertEquals(listOf(SpotKind.PLAYGROUND, SpotKind.ICE_CREAM, SpotKind.CASTLE), s.map { it.kind })
        assertEquals(OpenState.OPEN, s[1].open)
        assertEquals(OpenState.UNKNOWN, s[0].open)
        assertTrue(s[0].offsetMeters < 600)
    }

    @Test fun detourMinutes() {
        assertEquals(1, KidSpot(SpotKind.ZOO, null, LatLon(0.0, 0.0), 0.0, 100.0).detourMinutes)
        assertEquals(6, KidSpot(SpotKind.ZOO, null, LatLon(0.0, 0.0), 0.0, 2000.0).detourMinutes)
    }

    @Test fun duplicatesAreMerged() {
        val body = """{"elements":[${el(50.3, 10.0, "leisure" to "playground", "name" to "A")},${el(50.3003, 10.0, "leisure" to "playground", "name" to "B")}]}"""
        assertEquals(1, KidSpots.parse(body, route, now).size)
    }

    @Test fun aheadFiltersAndPrefersOpenAndShortDetour() {
        fun s(kind: SpotKind, along: Double, off: Double, open: OpenState = OpenState.UNKNOWN) = KidSpot(kind, "x", LatLon(0.0, 0.0), along, off, open)
        val spots = listOf(
            s(SpotKind.PLAYGROUND, 500.0, 100.0), // zu nah/hinter uns
            s(SpotKind.PLAYGROUND, 10_000.0, 2500.0), s(SpotKind.PLAYGROUND, 20_000.0, 200.0),
            s(SpotKind.ICE_CREAM, 15_000.0, 300.0, OpenState.OPEN), s(SpotKind.ZOO, 200_000.0, 100.0),
        )
        val r = KidSpots.ahead(spots, 0.0)
        assertEquals(listOf(10_000.0, 15_000.0, 20_000.0), r.map { it.alongMeters })
        assertEquals(listOf(SpotKind.PLAYGROUND), KidSpots.ahead(spots, 0.0, setOf(SpotKind.PLAYGROUND)).map { it.kind }.distinct())
        assertEquals(1, KidSpots.ahead(spots, 0.0, limit = 1).size)
        assertTrue(KidSpots.buildQuery(Polyline(listOf(LatLon(50.0, 10.0), LatLon(51.0, 10.0))).let { Route(it, emptyList(), it.lengthMeters, 1.0) }, 0.0, 50_000.0).contains("tourism=zoo"))
    }
}

class MemoryBookTest {
    private fun e(id: String, at: Long, day: Int, kind: DiaryKind, text: String = "", lat: Double? = null, lon: Double? = null) =
        DiaryEntry(id, at, day, kind.name, "t$id", text, lat = lat, lon = lon)

    @Test fun daysSeparatePhotosFromText() {
        val d = MemoryBook.days(listOf(
            e("1", 1, 10, DiaryKind.TRIP_START), e("2", 2, 10, DiaryKind.PHOTO, "a-back.jpg|a-front.jpg"),
            e("3", 3, 11, DiaryKind.PHOTO, "b.jpg"), e("4", 4, 12, DiaryKind.COUNTRY),
        ))
        assertEquals(listOf(10, 11, 12), d.map { it.day })
        assertEquals(listOf("a-back.jpg", "a-front.jpg"), d[0].photoNames)
        assertEquals(1, d[0].entries.size)
        assertTrue(d[1].entries.isEmpty())
    }

    @Test fun photoPagesKeepOrder() {
        assertEquals(listOf(listOf("a", "b", "c", "d"), listOf("e")), MemoryBook.photoPages(listOf("a", "b", "c", "d", "e")))
        assertTrue(MemoryBook.photoPages(emptyList()).isEmpty())
    }

    @Test fun routePointsAreOrderedDedupedAndThinned() {
        val list = (0 until 500).map { e("$it", it.toLong(), 1, DiaryKind.QUEST, lat = 50.0 + it * 0.01, lon = 10.0) } + e("x", 999, 1, DiaryKind.QUEST)
        val pts = MemoryBook.routePoints(list, 100)
        assertTrue(pts.size in 100..101)
        assertEquals(50.0, pts.first().lat); assertEquals(50.0 + 499 * 0.01, pts.last().lat, 1e-9)
        val same = listOf(e("a", 1, 1, DiaryKind.QUEST, lat = 1.0, lon = 1.0), e("b", 2, 1, DiaryKind.QUEST, lat = 1.0, lon = 1.0))
        assertEquals(1, MemoryBook.routePoints(same).size)
    }

    @Test fun projectKeepsNorthUpAndFitsTheBox() {
        val pr = MemoryBook.project(listOf(LatLon(50.0, 10.0), LatLon(52.0, 10.5)), 400f, 300f, 20f)
        assertEquals(2, pr.size)
        assertTrue(pr[0].second > pr[1].second) // Sueden (kleinere Breite) liegt weiter unten
        pr.forEach { (x, y) -> assertTrue(x in 0f..400f && y in 0f..300f) }
        assertTrue(MemoryBook.project(listOf(LatLon(1.0, 1.0)), 10f, 10f).isEmpty())
    }
}
