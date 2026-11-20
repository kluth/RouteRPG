package app.kiloquest.nav

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class HintQueueTest {
    private fun c(k: HintKind, m: Double) = HintCandidate(k, m)

    @Test fun emptyGivesNull() = assertNull(HintQueue.select(emptyList(), null))

    @Test fun mostImportantWinsAndOthersCounted() {
        val r = HintQueue.select(listOf(c(HintKind.BREAK, 1000.0), c(HintKind.CLOSURE, 9000.0), c(HintKind.JAM_LIGHT, 500.0)), null)!!
        assertEquals(HintKind.CLOSURE, r.top.kind)
        assertEquals(2, r.more)
    }

    @Test fun sameKindNearerFirst() {
        val r = HintQueue.select(listOf(c(HintKind.JAM_HEAVY, 8000.0), c(HintKind.JAM_HEAVY, 4000.0)), null)!!
        assertEquals(4000.0, r.top.aheadMeters)
        assertEquals(1, r.more)
    }

    @Test fun quietBeforeManeuver() {
        assertNull(HintQueue.select(listOf(c(HintKind.CLOSURE, 5000.0)), nextManeuverMeters = 250.0))
        assertEquals(HintKind.CLOSURE, HintQueue.select(listOf(c(HintKind.CLOSURE, 5000.0)), nextManeuverMeters = 300.0)!!.top.kind)
    }

    @Test fun passedAndTooFarAreDropped() {
        assertNull(HintQueue.select(listOf(c(HintKind.CLOSURE, -500.0), c(HintKind.WEATHER, 200_000.0)), null))
    }
}
