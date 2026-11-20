package app.kiloquest.nav

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class SpeechArbiterTest {
    @Test
    fun `quest speaks immediately when nothing else is going on`() {
        val a = SpeechArbiter()
        assertEquals("Quest", a.offerQuest("Quest", nowMs = 100_000, speaking = false))
    }

    @Test
    fun `quest waits after a navigation announcement`() {
        val a = SpeechArbiter(quietAfterNavMs = 8_000)
        a.navSpoken(100_000)
        assertNull(a.offerQuest("Quest", 101_000, speaking = false))
        assertNull(a.poll(105_000, speaking = false))
        assertEquals("Quest", a.poll(108_500, speaking = false))
        assertNull(a.poll(109_000, speaking = false)) // nur einmal
    }

    @Test
    fun `quest waits while tts is speaking`() {
        val a = SpeechArbiter()
        assertNull(a.offerQuest("Quest", 100_000, speaking = true))
        assertEquals("Quest", a.poll(101_000, speaking = false))
    }

    @Test
    fun `stale quest speech expires`() {
        val a = SpeechArbiter(maxWaitMs = 30_000)
        a.navSpoken(100_000)
        a.offerQuest("Quest", 100_000, speaking = true)
        assertNull(a.poll(140_000, speaking = false))
    }
}
