package app.kiloquest.game

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class BonusPlannerTest {
    private val pool = QuestCatalog.default.filter { it.textKey in setOf(WikiStories.PENDING_KEY, NatureSpotting.PENDING_KEY, PlaneSpotting.PENDING_KEY, RouteFacts.PENDING_KEY) }
    private fun planner() = BonusPlanner(pool, seed = 1)

    @Test fun notBeforeInterval() {
        assertNull(planner().next(10_000.0, false, 8_000.0, null, null))
    }

    @Test fun firesAfterIntervalAsPlaceholder() {
        val q = assertNotNull(planner().next(16_000.0, false, 12_000.0, null, null))
        assertTrue(q.templateId.startsWith("bonus_"))
        assertEquals(16_000.0, q.triggerMeters)
        assertTrue(q.textKey.endsWith("_pending"))
    }

    @Test fun quietConditions() {
        val p = planner()
        assertNull(p.next(16_000.0, true, 0.0, null, null)) // Aufgabe aktiv
        assertNull(p.next(16_000.0, false, 15_500.0, null, null)) // gerade erst eine Aufgabe
        assertNull(p.next(16_000.0, false, 12_000.0, 16_800.0, null)) // geplante Aufgabe gleich dahinter
        assertNull(p.next(16_000.0, false, 12_000.0, null, 400.0)) // Manoever naht
        assertNotNull(p.next(16_000.0, false, 12_000.0, 19_000.0, 2_000.0))
    }

    @Test fun nextOnlyAfterAnotherInterval() {
        val p = planner()
        assertNotNull(p.next(16_000.0, false, 12_000.0, null, null))
        assertNull(p.next(20_000.0, false, 12_000.0, null, null))
        assertNotNull(p.next(31_500.0, false, 12_000.0, null, null))
    }

    @Test fun cyclesThroughAllKindsBeforeRepeating() {
        val p = planner()
        val kinds = (1..pool.size).map { i -> p.next(16_000.0 * i, false, 0.0, null, null)!!.textKey }
        assertEquals(setOf(WikiStories.PENDING_KEY, NatureSpotting.PENDING_KEY, PlaneSpotting.PENDING_KEY, RouteFacts.PENDING_KEY), kinds.toSet())
    }
}

class AdventureSessionPlanTest {
    private fun q(m: Double, t: String = "a") = Quest(t, "k", QuestType.QUIZ, m)

    @Test fun upcomingReplaceAndBonus() {
        val a = q(1000.0, "a"); val b = q(2000.0, "b")
        val s = AdventureSession(listOf(a, b))
        assertEquals(listOf(a, b), s.upcoming())
        val b2 = b.copy(textKey = "resolved")
        assertTrue(s.replaceUpcoming(b, b2))
        s.onProgress(1500.0)
        assertEquals(listOf(b2), s.upcoming())
        assertTrue(!s.replaceUpcoming(a, a.copy(textKey = "x"))) // schon ausgeloest
        val bonus = q(1600.0, "bonus")
        s.insertBonus(bonus)
        assertEquals(3, s.total)
        assertEquals(listOf(bonus, b2), s.upcoming())
        assertEquals(bonus, s.onProgress(1600.0))
        assertEquals(1600.0, s.lastTriggerMeters)
    }
}
