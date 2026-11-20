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
    }
}
