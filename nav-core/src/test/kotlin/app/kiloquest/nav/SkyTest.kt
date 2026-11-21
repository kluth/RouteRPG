package app.kiloquest.nav

import java.time.LocalDateTime
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SkyTest {
    private fun utc(s: String) = LocalDateTime.parse(s).toEpochSecond(ZoneOffset.UTC)
    private fun circ(a: Double, b: Double) = Sky.azimuthDiff(a * 360, b * 360) / 360

    @Test fun `full moon 2025-01-13 and new moon 2025-01-29`() {
        val full = Sky.moon(utc("2025-01-13T22:27:00"), 52.5, 13.4)
        assertTrue(circ(full.cycle, 0.5) < 0.01, "${full.cycle}")
        assertEquals("moon_full", full.phaseKey)
        assertTrue(full.illumination > 0.99)
        val new = Sky.moon(utc("2025-01-29T12:36:00"), 52.5, 13.4)
        assertTrue(circ(new.cycle, 0.0) < 0.01, "${new.cycle}")
        assertEquals("moon_new", new.phaseKey)
        assertTrue(new.illumination < 0.01)
    }

    @Test fun `quarters`() {
        assertEquals("moon_first_quarter", Sky.moon(utc("2025-01-06T23:56:00"), 0.0, 0.0).phaseKey)
        assertEquals("moon_last_quarter", Sky.moon(utc("2025-01-21T20:31:00"), 0.0, 0.0).phaseKey)
        assertEquals("moon_waxing_crescent", Sky.phaseKey(0.12))
        assertEquals("moon_waning_gibbous", Sky.phaseKey(0.62))
        assertEquals("moon_waning_crescent", Sky.phaseKey(0.88))
        assertEquals("moon_waxing_gibbous", Sky.phaseKey(0.38))
        assertEquals("moon_new", Sky.phaseKey(0.99))
    }

    @Test fun `winter full moon stands high in the south around midnight`() {
        // Berlin: Vollmond im Januar kulminiert bei ca. 90 - 52.5 + 22 = 60 Grad
        val p = Sky.moon(utc("2025-01-13T23:00:00"), 52.5, 13.4).position
        assertTrue(p.altitudeDeg in 55.0..66.0, "${p.altitudeDeg}")
        assertTrue(p.azimuthDeg in 165.0..195.0, "${p.azimuthDeg}")
    }

    @Test fun `sun is south and high at noon in midsummer`() {
        // Berlin 2025-06-21, Kulmination ca. 11:08 UTC: 90 - 52.5 + 23.44 = 60.9 Grad
        val p = Sky.sun(utc("2025-06-21T11:08:00"), 52.5, 13.4)
        assertEquals(60.9, p.altitudeDeg, 1.0)
        assertEquals(180.0, p.azimuthDeg, 3.0)
    }

    @Test fun `jupiter is high in the south on a January evening 2025`() {
        // Jupiter (Stier, Dekl. ca. +22, Opposition 7.12.2024) kulminiert in Berlin gegen 21 Uhr MEZ bei ca. 60 Grad
        val p = Sky.planet(Planet.JUPITER, utc("2025-01-13T20:00:00"), 52.5, 13.4)
        assertTrue(p.altitudeDeg in 52.0..66.0, "${p.altitudeDeg}")
        assertTrue(p.azimuthDeg in 150.0..210.0, "${p.azimuthDeg}")
    }

    @Test fun `venus is evening star in the south west in January 2025`() {
        val p = Sky.planet(Planet.VENUS, utc("2025-01-13T16:30:00"), 52.5, 13.4)
        assertTrue(p.altitudeDeg in 15.0..35.0, "${p.altitudeDeg}")
        assertTrue(p.azimuthDeg in 200.0..250.0, "${p.azimuthDeg}")
    }

    @Test fun `tonight list in Berlin January 2025`() {
        val list = Sky.tonight(utc("2025-01-13T12:00:00"), 52.5, 13.4)
        val keys = list.map { it.key }
        assertTrue("planet_jupiter" in keys && "planet_mars" in keys && "planet_venus" in keys, "$keys")
        assertTrue("moon_full" in keys, "$keys")
        assertEquals(list.sortedByDescending { it.position.altitudeDeg }, list)
        assertTrue(list.all { it.position.altitudeDeg > 5.0 })
    }

    @Test fun `nothing is visible in the midnight sun and in daylight`() {
        assertTrue(Sky.tonight(utc("2025-06-21T12:00:00"), 78.2, 15.6).isEmpty())
        assertTrue(Sky.visibleNow(utc("2025-01-13T11:00:00"), 52.5, 13.4).isEmpty())
        assertTrue(Sky.visibleNow(utc("2025-01-13T22:00:00"), 52.5, 13.4).isNotEmpty())
    }
}
