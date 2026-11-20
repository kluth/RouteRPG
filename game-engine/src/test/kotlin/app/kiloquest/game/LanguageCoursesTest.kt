package app.kiloquest.game

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class LanguageCoursesTest {
    @Test
    fun `every course has every phrase and codes are unique`() {
        for (c in LanguageCourses.all) {
            assertEquals(Phrase.entries.toSet(), c.words.keys, c.country)
            assertTrue(c.words.values.all { it.isNotBlank() }, c.country)
        }
        assertEquals(LanguageCourses.all.size, LanguageCourses.all.map { it.country }.toSet().size)
    }

    @Test
    fun `lookup by country code ignores case and unknown gives null`() {
        assertEquals("sv", LanguageCourses.forCountry("se")?.language)
        assertNull(LanguageCourses.forCountry("DE"))
        assertNull(LanguageCourses.forCountry(null))
    }

    @Test
    fun `quiz has three distinct options and the right answer`() {
        val c = LanguageCourses.forCountry("SE")!!
        repeat(30) { seed ->
            val q = LanguageCourses.quiz(c, emptySet(), Random(seed))
            assertEquals(3, q.options.size)
            assertEquals(3, q.options.toSet().size)
            assertEquals(c.words.getValue(q.phrase), q.options[q.correctIndex])
        }
    }

    @Test
    fun `quiz avoids already taught phrases until all are taught`() {
        val c = LanguageCourses.forCountry("DK")!!
        val taught = Phrase.entries.filter { it != Phrase.WATER }.toSet()
        repeat(10) { assertEquals(Phrase.WATER, LanguageCourses.quiz(c, taught, Random(it)).phrase) }
        assertNotNull(LanguageCourses.quiz(c, Phrase.entries.toSet(), Random(1)))
    }

    @Test
    fun `quiz is deterministic for a seed`() {
        val c = LanguageCourses.forCountry("FI")!!
        assertEquals(LanguageCourses.quiz(c, emptySet(), Random(9)), LanguageCourses.quiz(c, emptySet(), Random(9)))
    }
}
