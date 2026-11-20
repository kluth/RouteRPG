package app.kiloquest.game

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AdventureSessionTest {
    private fun check(at: Double) = Quest("c", "quest_c", QuestType.SPOTTING, at)
    private fun choice(at: Double) = Quest("q", "quest_q", QuestType.QUIZ, at, QuestKind.CHOICE, listOf("a", "b", "c"), 1, 15)

    @Test
    fun `quest triggers once position is reached`() {
        val s = AdventureSession(listOf(check(1000.0)))
        assertNull(s.onProgress(500.0))
        assertNotNull(s.onProgress(1000.0))
        assertNull(s.onProgress(1100.0))
    }

    @Test
    fun `check quest awards points and counts`() {
        val s = AdventureSession(listOf(check(100.0)))
        s.onProgress(100.0)
        val r = s.answer()
        assertEquals(10, r?.pointsAwarded)
        assertEquals(10, s.points)
        assertEquals(1, s.completed)
        assertNull(s.active)
    }

    @Test
    fun `choice quest right and wrong`() {
        val s = AdventureSession(listOf(choice(100.0), choice(200.0)))
        s.onProgress(100.0)
        val ok = s.answer(1)
        assertTrue(ok!!.correct)
        assertEquals(15, s.points)
        s.onProgress(200.0)
        val bad = s.answer(0)
        assertTrue(!bad!!.correct)
        assertEquals(1, bad.correctIndex)
        assertEquals(15, s.points)
        assertEquals(2, s.completed)
    }

    @Test
    fun `unanswered quest expires when next is due`() {
        val s = AdventureSession(listOf(check(100.0), check(200.0)))
        s.onProgress(100.0)
        val next = s.onProgress(200.0)
        assertEquals(200.0, next?.triggerMeters)
        assertEquals(1, s.skipped)
    }

    @Test
    fun `sticker every three completed quests`() {
        val s = AdventureSession(listOf(check(1.0), check(2.0), check(3.0), check(4.0)))
        for (m in listOf(1.0, 2.0, 3.0, 4.0)) { s.onProgress(m); s.answer() }
        assertEquals(1, s.stickers)
        assertEquals(4, s.completed)
    }

    @Test
    fun `answer without active quest is null`() {
        assertNull(AdventureSession(emptyList()).answer())
    }
}
