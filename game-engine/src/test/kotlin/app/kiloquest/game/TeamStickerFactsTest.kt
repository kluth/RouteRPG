package app.kiloquest.game

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TeamStickerFactsTest {
    private fun check(at: Double) = Quest("c", "quest_c", QuestType.SPOTTING, at)

    @Test
    fun `team plays in turns and scores per player`() {
        val s = AdventureSession(listOf(check(1.0), check(2.0), check(3.0)), playerCount = 2)
        s.onProgress(1.0); assertEquals(0, s.currentPlayer); assertEquals(0, s.answer()?.player)
        s.onProgress(2.0); assertEquals(1, s.currentPlayer); assertEquals(1, s.answer()?.player)
        s.onProgress(3.0); assertEquals(0, s.currentPlayer); s.answer()
        assertEquals(20, s.scores[0])
        assertEquals(10, s.scores[1])
        assertEquals(30, s.points)
    }

    @Test
    fun `replaceActive swaps the running quest`() {
        val s = AdventureSession(listOf(check(1.0)))
        val q = s.onProgress(1.0)!!
        s.replaceActive(q.copy(textKey = "fact_river", args = listOf("Rhein")))
        assertEquals(listOf("Rhein"), s.active?.args)
    }

    @Test
    fun `stickers are awarded until the album is full`() {
        val book = StickerBook()
        val rnd = Random(1)
        val got = (1..StickerBook.ALL.size).map { book.award(rnd)!!.id }
        assertEquals(StickerBook.ALL.size, got.toSet().size)
        assertTrue(book.isComplete)
        assertNull(book.award(rnd))
    }

    @Test
    fun `sticker book ignores unknown ids from storage`() {
        val book = StickerBook(setOf("fox", "does_not_exist"))
        assertEquals(setOf("fox"), book.collected)
    }

    @Test
    fun `overpass query has all feature filters`() {
        val q = RouteFacts.buildQuery(57.5, 12.3)
        assertTrue("around:3000,57.5,12.3" in q)
        for (f in listOf("waterway=river", "historic=castle", "natural=peak", "man_made=bridge")) assertTrue(f in q, f)
    }

    @Test
    fun `parses overpass response into facts`() {
        val body = """{"elements":[
          {"tags":{"waterway":"river","name":"Rhein"}},
          {"tags":{"natural":"water","water":"lake","name":"Vänern"}},
          {"tags":{"historic":"castle","name":"Schloss Heidelberg"}},
          {"tags":{"highway":"primary","name":"Hauptstraße"}},
          {"tags":{"waterway":"river"}}]}"""
        val facts = RouteFacts.parse(body)
        assertEquals(3, facts.size)
        assertEquals(RouteFact(FactKind.RIVER, "Rhein"), facts[0])
        assertEquals(FactKind.CASTLE, RouteFacts.pick(facts, Random(1))?.kind) // selten bevorzugt
    }

    @Test
    fun `resolve uses fact or falls back`() {
        val ph = Quest("route_fact", RouteFacts.PENDING_KEY, QuestType.ROUTE_FACT, 5000.0)
        val ok = RouteFacts.resolve(ph, RouteFact(FactKind.PEAK, "Kebnekaise"))
        assertEquals("fact_peak", ok.textKey)
        assertEquals(listOf("Kebnekaise"), ok.args)
        val fb = RouteFacts.resolve(ph, null)
        assertEquals(RouteFacts.FALLBACK_KEY, fb.textKey)
        assertNotNull(fb)
    }

    @Test
    fun `empty overpass result gives no fact`() {
        assertTrue(RouteFacts.parse("""{"elements":[]}""").isEmpty())
        assertNull(RouteFacts.pick(emptyList(), Random(1)))
    }
}
