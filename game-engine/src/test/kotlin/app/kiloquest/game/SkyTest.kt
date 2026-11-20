package app.kiloquest.game

import java.time.LocalDateTime
import java.time.ZoneOffset
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SkyTest {
    private fun utc(s: String) = LocalDateTime.parse(s).toEpochSecond(ZoneOffset.UTC)
    private fun res(n: String) = javaClass.classLoader.getResource(n)!!.readText()

    @Test
    fun `sun is high at midsummer noon and low at midnight`() {
        // Duisburg, Sommersonnenwende, ca. Sonnenhoechststand 11:33 UTC: 90 - 51.4 + 23.4 = 62 Grad
        assertEquals(62.0, Sun.elevationDegrees(51.43, 6.76, utc("2026-06-21T11:33:00")), 1.5)
        assertTrue(Sun.elevationDegrees(51.43, 6.76, utc("2026-06-21T23:33:00")) < -10.0)
    }

    @Test
    fun `polar night and midnight sun in Lapland`() {
        // Kiruna: Mittag am 21.12. kaum ueber dem Horizont; nachts im Juni Sonne nicht tief genug fuer Dunkelheit
        assertTrue(Sun.elevationDegrees(67.86, 20.23, utc("2026-12-21T10:00:00")) < 1.0)
        assertTrue(!Sun.isDark(67.86, 20.23, utc("2026-06-21T22:00:00")))
        assertTrue(Sun.isDark(67.86, 20.23, utc("2026-11-20T16:00:00")))
    }

    @Test
    fun `moon cycle is periodic and full moon is half a cycle after new moon`() {
        assertEquals(0.0, Moon.cycle(947182440L), 1e-6)
        assertEquals(MoonPhaseName.NEW, Moon.phase(947182440L))
        assertEquals(0.5, Moon.cycle(utc("2000-01-21T04:40:00")), 0.02) // Vollmond 21.1.2000
        assertEquals(MoonPhaseName.FULL, Moon.phase(utc("2000-01-21T04:40:00")))
        val t = utc("2026-11-20T12:00:00")
        assertEquals(Moon.cycle(t), Moon.cycle(t + (29.530588853 * 86400).toLong()), 0.001)
    }

    @Test
    fun `reads latest kp from the real noaa table`() {
        val kp = Aurora.latestKp(res("noaa_kp_index.json"))
        assertNotNull(kp)
        assertTrue(kp!! in 0.0..9.0)
        assertNull(Aurora.latestKp("""{"x":1}"""))
    }

    @Test
    fun `aurora chance depends on latitude, kp and darkness`() {
        assertEquals(AuroraChance.GOOD, Aurora.chance(68.0, 2.0, true))
        assertEquals(AuroraChance.NONE, Aurora.chance(51.4, 2.0, true))
        assertEquals(AuroraChance.GOOD, Aurora.chance(57.0, 7.0, true)) // starker Sturm bis weit in den Sueden
        assertEquals(AuroraChance.POSSIBLE, Aurora.chance(55.0, 7.0, true))
        assertEquals(AuroraChance.NONE, Aurora.chance(68.0, 5.0, false))
        assertEquals(AuroraChance.NONE, Aurora.chance(68.0, null, true))
    }

    @Test
    fun `parses real adsb response and picks a visible plane`() {
        val planes = PlaneSpotting.parse(res("adsblol_point_duisburg.json"), 51.43, 6.76)
        assertTrue(planes.size >= 3)
        assertTrue(planes.all { it.distanceKm < 60.0 })
        val picked = PlaneSpotting.pick(planes, Random(1))
        if (picked != null) {
            assertTrue(picked.altitudeMeters >= 1_000.0 && picked.distanceKm <= 25.0)
        }
    }

    @Test
    fun `ground aircraft and aircraft without position are ignored`() {
        val body = """{"ac":[{"flight":"A","t":"A320","alt_baro":"ground","lat":51.4,"lon":6.7},
          {"flight":"B","t":"A320","alt_baro":30000},{"flight":"C","t":"B738","alt_baro":30000,"lat":51.5,"lon":6.8}]}"""
        assertEquals(listOf("C"), PlaneSpotting.parse(body, 51.4, 6.7).map { it.callsign })
    }

    @Test
    fun `compass directions and bearings`() {
        assertEquals(Compass8.N, PlaneSpotting.compass(0.0))
        assertEquals(Compass8.N, PlaneSpotting.compass(355.0))
        assertEquals(Compass8.E, PlaneSpotting.compass(92.0))
        assertEquals(Compass8.SW, PlaneSpotting.compass(225.0))
        assertEquals(0.0, PlaneSpotting.bearing(50.0, 8.0, 51.0, 8.0), 0.5)   // genau nach Norden
        assertEquals(90.0, PlaneSpotting.bearing(0.0, 8.0, 0.0, 9.0), 0.5)    // am Aequator nach Osten
        assertEquals(111.2, PlaneSpotting.distanceKm(50.0, 8.0, 51.0, 8.0), 0.5)
    }

    @Test
    fun `plane type names`() {
        assertEquals("Airbus A320", PlaneSpotting.typeName("A320"))
        assertEquals("Boeing 737-800", PlaneSpotting.typeName("B738"))
        assertEquals("XYZ", PlaneSpotting.typeName("XYZ"))
    }
}
