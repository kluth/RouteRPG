package app.kiloquest.nav

import kotlin.math.abs
import kotlin.math.asin
import kotlin.math.sqrt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class IssPassesTest {
    // Beispiel-TLE aus dem Spacetrack-Report Nr. 3 (ISS, Epoche 2008-09-20 12:25 UTC)
    private val text = """
        ISS (ZARYA)
        1 25544U 98067A   08264.51782528 -.00002182  00000-0 -11606-4 0  2927
        2 25544  51.6416 247.4627 0006703 130.5360 325.0288 15.72125391563537
    """.trimIndent()

    private val tle = Tle.parse(text)!!

    @Test fun parsesEpochAndElements() {
        val epoch = java.time.LocalDate.of(2008, 9, 20).toEpochDay() * 86400.0 + 0.51782528 * 86400.0
        assertEquals(epoch, tle.epochSec, 1.0)
        assertEquals(51.6416, tle.inclinationDeg, 1e-6)
        assertEquals(15.72125391, tle.revPerDay, 1e-6)
    }

    @Test fun rejectsBrokenChecksumAndGarbage() {
        assertNull(Tle.parse(text.replace("2927", "2928")))
        assertNull(Tle.parse("hello"))
        assertNull(Tle.parse(""))
    }

    @Test fun orbitIsPlausible() {
        var maxLat = 0.0
        var t = tle.epochSec
        repeat(600) {
            val r = IssPasses.positionEci(tle, t)
            val radius = sqrt(r[0] * r[0] + r[1] * r[1] + r[2] * r[2])
            assertTrue(radius in 6700.0..6850.0, "Radius $radius")
            maxLat = maxOf(maxLat, abs(Math.toDegrees(asin(r[2] / radius))))
            t += 60.0
        }
        assertTrue(maxLat in 50.5..51.8, "Max. Breite $maxLat")
    }

    @Test fun periodIsAbout92Minutes() {
        // Nach einer Periode (86400 / 15.72125 s) kehrt die Position nahezu zurueck (J2-Drift klein)
        val period = 86400.0 / tle.revPerDay
        val a = IssPasses.positionEci(tle, tle.epochSec)
        val b = IssPasses.positionEci(tle, tle.epochSec + period)
        val d = sqrt((a[0] - b[0]).let { it * it } + (a[1] - b[1]).let { it * it } + (a[2] - b[2]).let { it * it })
        assertTrue(period / 60 in 91.0..94.0, "Periode ${period / 60} min")
        assertTrue(d < 150.0, "Abstand nach einer Periode $d km")
    }

    @Test fun geometricPassesAreFrequentAndSane() {
        val from = tle.epochSec.toLong()
        val passes = IssPasses.next(tle, from, 48.14, 11.58, days = 2, requireDark = false)
        assertTrue(passes.size >= 6, "Ueberfluege ${passes.size}")
        passes.forEach { p ->
            assertTrue(p.maxAltitudeDeg > 10.0)
            assertTrue(p.startSec <= p.maxSec && p.maxSec <= p.endSec)
            assertTrue(p.durationSec in 0..700)
        }
        assertEquals(passes.sortedBy { it.startSec }, passes)
    }

    @Test fun visiblePassesNeedDarknessAndSunlitIss() {
        val from = tle.epochSec.toLong()
        val all = IssPasses.next(tle, from, 48.14, 11.58, days = 3, requireDark = false)
        val visible = IssPasses.next(tle, from, 48.14, 11.58, days = 3)
        assertTrue(visible.size < all.size, "sichtbare ${visible.size} von ${all.size}")
        visible.forEach { p ->
            assertTrue(Sky.sun(p.startSec, 48.14, 11.58).altitudeDeg < -6.0)
            assertTrue(!IssPasses.inEarthShadow(IssPasses.positionEci(tle, p.maxSec.toDouble()), IssPasses.sunDirection(p.maxSec.toDouble())))
        }
    }

    @Test fun oldDataGivesNoPasses() {
        val later = (tle.epochSec + 10 * 86400).toLong()
        assertTrue(IssPasses.next(tle, later, 48.14, 11.58).isEmpty())
    }

    @Test fun shadowModel() {
        val sun = doubleArrayOf(1.0, 0.0, 0.0)
        assertTrue(IssPasses.inEarthShadow(doubleArrayOf(-6800.0, 0.0, 0.0), sun))
        assertTrue(!IssPasses.inEarthShadow(doubleArrayOf(6800.0, 0.0, 0.0), sun))
        assertTrue(!IssPasses.inEarthShadow(doubleArrayOf(-6800.0, 7000.0, 0.0), sun))
    }
}
