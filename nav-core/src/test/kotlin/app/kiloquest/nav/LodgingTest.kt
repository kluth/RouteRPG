package app.kiloquest.nav

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class LodgingTest {
    private val body = """{"elements":[
      {"type":"node","lat":52.1,"lon":9.1,"tags":{"tourism":"hotel","name":"Hotel Eins","stars":"3","website":"https://e.example"}},
      {"type":"way","center":{"lat":52.11,"lon":9.1},"tags":{"tourism":"apartment","name":"Ferienwohnung Zwei"}},
      {"type":"node","lat":52.1,"lon":9.1,"tags":{"tourism":"hotel"}},
      {"type":"node","lat":52.1,"lon":9.1,"tags":{"tourism":"museum","name":"Museum"}},
      {"type":"node","lat":52.12,"lon":9.1,"tags":{"tourism":"camp_site","name":"Camp Drei"}},
      {"type":"node","lat":52.1001,"lon":9.1,"tags":{"tourism":"hotel","name":"hotel eins"}}
    ]}"""

    @Test fun parsesKindsAndSkipsUnnamedOrOther() {
        val r = LodgingFinder.parse(body)
        assertEquals(setOf("Hotel Eins", "Ferienwohnung Zwei", "Camp Drei", "hotel eins"), r.map { it.name }.toSet())
        assertEquals(3, r.first { it.name == "Hotel Eins" }.stars)
        assertEquals(LodgingKind.APARTMENT, r.first { it.name.startsWith("Ferien") }.kind)
    }

    @Test fun rankPrefersFamilyKindsAndRemovesDuplicates() {
        val ranked = LodgingFinder.rank(LodgingFinder.parse(body), LatLon(52.1, 9.1))
        assertEquals(3, ranked.size) // Dublette "hotel eins" entfernt
        assertEquals("Hotel Eins", ranked.first().name.let { if (it.equals("Hotel Eins", true)) "Hotel Eins" else it })
    }

    @Test fun queryContainsCampingOnlyOnRequest() {
        val c = LatLon(50.0, 8.0)
        assertTrue("camp_site" !in LodgingFinder.buildQuery(c, 8000, false))
        assertTrue("camp_site" in LodgingFinder.buildQuery(c, 8000, true))
        assertTrue("around:8000,50.00000,8.00000" in LodgingFinder.buildQuery(c, 8000, false))
    }

    @Test fun invalidStarsAreIgnored() {
        val b = """{"elements":[{"type":"node","lat":1.0,"lon":1.0,"tags":{"tourism":"hotel","name":"X","stars":"9"}}]}"""
        assertEquals(null, LodgingFinder.parse(b).single().stars)
    }
}
