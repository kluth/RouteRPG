package app.kiloquest.nav

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class OverspeedAlerterTest {
    private fun alerter() = OverspeedAlerter(toleranceKmh = 5, holdMs = 4_000, repeatMs = 90_000, clearAfterBelowMs = 10_000)

    @Test fun noAlertWithinToleranceOrWithoutData() {
        val a = alerter()
        assertNull(a.onTick(0, 84.0, 80))        // 4 km/h drueber = innerhalb der Toleranz
        assertNull(a.onTick(10_000, 84.0, 80))
        assertNull(a.onTick(20_000, null, 80))
        assertNull(a.onTick(30_000, 120.0, null))
        assertNull(a.onTick(40_000, 200.0, SpeedProfile.UNLIMITED))
    }

    @Test fun alertOnlyAfterHoldTime() {
        val a = alerter()
        assertNull(a.onTick(0, 95.0, 80))
        assertNull(a.onTick(3_000, 95.0, 80))
        val al = a.onTick(4_000, 95.0, 80)
        assertNotNull(al)
        assertEquals(80, al.limitKmh)
        assertEquals(95, al.speedKmh)
    }

    @Test fun shortSpikeIsIgnored() {
        val a = alerter()
        assertNull(a.onTick(0, 100.0, 80))
        assertNull(a.onTick(2_000, 70.0, 80)) // wieder im Limit: Zaehler zurueck
        assertNull(a.onTick(3_000, 100.0, 80))
        assertNull(a.onTick(6_000, 100.0, 80)) // erst 3 s seit neuem Start
        assertNotNull(a.onTick(7_000, 100.0, 80))
    }

    @Test fun repeatsOnlyAfterCooldown() {
        val a = alerter()
        a.onTick(0, 100.0, 80)
        assertNotNull(a.onTick(4_000, 100.0, 80))
        assertNull(a.onTick(30_000, 100.0, 80))
        assertNull(a.onTick(93_000, 100.0, 80)) // 89 s nach der Warnung
        assertNotNull(a.onTick(94_000, 100.0, 80))
    }

    @Test fun lowerLimitWarnsAgainImmediately() {
        val a = alerter()
        a.onTick(0, 100.0, 80)
        assertNotNull(a.onTick(4_000, 100.0, 80))
        assertNull(a.onTick(5_000, 100.0, 80))
        assertNotNull(a.onTick(6_000, 100.0, 50)) // neues, niedrigeres Limit
    }

    @Test fun busyDefersWithoutConsumingTheAlert() {
        val a = alerter()
        a.onTick(0, 100.0, 80)
        assertNull(a.onTick(5_000, 100.0, 80, busy = true))
        assertNotNull(a.onTick(6_000, 100.0, 80, busy = false))
    }

    @Test fun backWithinLimitForTenSecondsClearsCooldown() {
        val a = alerter()
        a.onTick(0, 100.0, 80)
        assertNotNull(a.onTick(4_000, 100.0, 80))
        assertNull(a.onTick(10_000, 70.0, 80))
        assertNull(a.onTick(21_000, 70.0, 80)) // 11 s unter Limit -> Cooldown geloescht
        assertNull(a.onTick(22_000, 100.0, 80))
        assertNotNull(a.onTick(26_000, 100.0, 80))
    }

    @Test fun resetForgetsEverything() {
        val a = alerter()
        a.onTick(0, 100.0, 80)
        a.onTick(4_000, 100.0, 80)
        a.reset()
        a.onTick(5_000, 100.0, 80)
        assertNotNull(a.onTick(9_000, 100.0, 80))
    }
}
