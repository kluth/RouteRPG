package app.kiloquest.game

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class PlateBingoTest {
    @Test fun `card is deterministic and has nine distinct common countries`() {
        val a = PlateBingoCard.generate(42)
        assertEquals(a, PlateBingoCard.generate(42))
        assertEquals(9, a.cells.distinct().size)
        assertTrue(a.cells.all { it in PlateCountries.COMMON })
        assertTrue(PlateBingoCard.generate(43) != a)
    }

    @Test fun `flags and keys`() {
        assertEquals("🇩🇪", PlateCountries.byCode("d")!!.flag)
        assertEquals("country_slo", PlateCountries.byCode("SLO")!!.nameKey)
        assertEquals(PlateCountries.ALL.size, PlateCountries.ALL.map { it.code }.distinct().size)
    }

    @Test fun `row column and diagonal wins`() {
        fun play(idx: List<Int>): PlateBingoGame {
            var g = PlateBingoGame.start(1)
            for (i in idx) g = g.spot(g.card.cells[i].code).game
            return g
        }
        assertFalse(play(listOf(0, 1)).hasBingo)
        assertTrue(play(listOf(3, 4, 5)).hasBingo)
        assertTrue(play(listOf(1, 4, 7)).hasBingo)
        assertTrue(play(listOf(2, 4, 6)).hasBingo)
        assertFalse(play(listOf(0, 1, 5, 6)).hasBingo)
        assertTrue(play((0..8).toList()).isFull)
    }

    @Test fun `points and collection`() {
        var g = PlateBingoGame.start(7)
        val c = g.card.cells
        val r1 = g.spot(c[0].code)
        assertTrue(r1.newCountry && r1.onCard)
        assertEquals(8, r1.points)
        g = r1.game
        assertEquals(0, g.spot(c[0].code).points) // doppelt
        g = g.spot(c[1].code).game
        val r3 = g.spot(c[2].code)
        assertEquals(1, r3.newLines)
        assertEquals(8 + 10, r3.points)
        assertEquals(1, r3.game.collectionCount - 2)
        assertEquals(0, r3.game.spot("XX").points)
        assertEquals(PlateCountries.ALL.size, r3.game.collectionTotal)
    }

    @Test fun `off card country counts for collection only and survives next card`() {
        val g = PlateBingoGame.start(3)
        val off = PlateCountries.COMMON.first { it !in g.card.cells }
        val r = g.spot(off.code)
        assertFalse(r.onCard)
        assertEquals(PlateBingoGame.POINTS_NEW_COUNTRY, r.points)
        val n = r.game.nextCard(4)
        assertTrue(n.marked.isEmpty())
        assertTrue(off.code in n.collection)
        assertEquals(r.game.points, n.points)
        assertNotNull(n.card)
    }
}
