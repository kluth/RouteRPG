package app.kiloquest.game

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class WildlifeQuizTest {
    @Test fun `license filter allows only CC0 and CC BY`() {
        for (ok in listOf("CC0_1_0", "CC0 1.0", "CC_BY_4_0", "CC BY 4.0", "cc-by", "https://creativecommons.org/licenses/by/4.0/", "http://creativecommons.org/publicdomain/zero/1.0/"))
            assertTrue(WildlifeQuiz.isAllowedLicense(ok), ok)
        for (bad in listOf("CC_BY_NC_4_0", "CC BY-SA 4.0", "CC_BY_ND_4_0", "UNSPECIFIED", "", "All rights reserved", "https://creativecommons.org/licenses/by-nc/4.0/"))
            assertFalse(WildlifeQuiz.isAllowedLicense(bad), bad)
        assertFalse(WildlifeQuiz.isAllowedLicense(null))
    }

    @Test fun `coordinates are rounded to a tenth of a degree`() {
        assertEquals(51.4, WildlifeQuiz.roundCoord(51.4318), 1e-9)
        assertEquals(6.8, WildlifeQuiz.roundCoord(6.76), 1e-9)
        assertEquals(-0.1, WildlifeQuiz.roundCoord(-0.0501), 1e-9)
    }

    @Test fun `species key`() {
        assertEquals("vulpes_vulpes", WildlifeSpecies("Vulpes vulpes").key)
        assertEquals("species_vulpes_vulpes", WildlifeSpecies(" Vulpes  vulpes ").nameKey)
    }

    @Test fun `fallback has about twenty distinct species`() {
        assertEquals(20, WildlifeQuiz.FALLBACK.map { it.key }.distinct().size)
    }

    @Test fun `questions are deterministic with three distinct options`() {
        val seen = listOf(
            WildlifeSpecies("Lynx lynx", license = "CC_BY_4_0"),
            WildlifeSpecies("Canis lupus", license = "CC_BY_NC_4_0"),
            WildlifeSpecies("Ursus arctos", license = "CC0_1_0"),
        )
        val a = WildlifeQuiz.generate(seen, 5, 5)
        assertEquals(a, WildlifeQuiz.generate(seen, 5, 5))
        assertEquals(2, a.size) // Wolf (NC) herausgefiltert
        for (q in a) {
            assertEquals(WildlifeQuiz.QUESTION_KEY, q.textKey)
            assertEquals(3, q.options.size)
            assertEquals(3, q.options.map { it.key }.distinct().size)
            assertTrue(q.correct.key in setOf("lynx_lynx", "ursus_arctos"))
            assertTrue(q.isCorrect(q.correctIndex))
        }
        assertTrue(a.none { q -> q.options.any { it.key == "canis_lupus" } })
    }

    @Test fun `without observations questions come from fallback`() {
        val qs = WildlifeQuiz.generate(emptyList(), 1, 4)
        assertEquals(4, qs.size)
        assertTrue(qs.all { q -> q.options.all { o -> WildlifeQuiz.FALLBACK.any { it.key == o.key } } })
    }
}
