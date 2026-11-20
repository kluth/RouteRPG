package app.kiloquest.game

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AnswerMatcherTest {
    private val options = listOf("Elefant", "Giraffe", "Bär")
    private val ordinals = listOf(listOf("eins", "erste", "1"), listOf("zwei", "zweite", "2"), listOf("drei", "dritte", "3"))

    @Test
    fun `normalizes umlauts case and punctuation`() {
        assertEquals("bar", AnswerMatcher.normalize("Bär!"))
        assertEquals("strasse", AnswerMatcher.normalize("STRAßE"))
        assertEquals("hej da", AnswerMatcher.normalize("Hej då."))
    }

    @Test
    fun `matches an option mentioned in a sentence`() {
        assertEquals(0, AnswerMatcher.matchOption(listOf("ich glaube es ist der elefant"), options))
        assertEquals(2, AnswerMatcher.matchOption(listOf("bar"), options)) // Umlaut egal
    }

    @Test
    fun `tolerates small recognition errors`() {
        assertEquals(1, AnswerMatcher.matchOption(listOf("giraffee"), options))
        assertEquals(0, AnswerMatcher.matchOption(listOf("elefand"), options))
    }

    @Test
    fun `uses the best of several hypotheses`() {
        assertEquals(1, AnswerMatcher.matchOption(listOf("graffe", "giraffe", "gira fee"), options))
    }

    @Test
    fun `ordinal words select by position`() {
        assertEquals(1, AnswerMatcher.matchOption(listOf("die zweite bitte"), options, ordinals))
        assertEquals(2, AnswerMatcher.matchOption(listOf("drei"), options, ordinals))
    }

    @Test
    fun `no match and ambiguous answers give null`() {
        assertNull(AnswerMatcher.matchOption(listOf("keine ahnung"), options))
        assertNull(AnswerMatcher.matchOption(emptyList(), options))
        assertNull(AnswerMatcher.matchOption(listOf("elefant oder giraffe"), options))
    }

    @Test
    fun `multi word options need all words`() {
        val o = listOf("Hej då", "Tack så mycket", "Ja")
        assertEquals(1, AnswerMatcher.matchOption(listOf("tack så mycket"), o))
        assertEquals(0, AnswerMatcher.matchOption(listOf("hej da"), o))
    }

    @Test
    fun `done words are recognised`() {
        val done = listOf("gefunden", "fertig", "ja", "geschafft")
        assertTrue(AnswerMatcher.isDone(listOf("ja gefunden"), done))
        assertTrue(!AnswerMatcher.isDone(listOf("nein nicht"), done))
        assertTrue(!AnswerMatcher.isDone(listOf("jagd"), done)) // Wort, nicht Teilstring
    }

    @Test
    fun `similarity basics`() {
        assertEquals(1.0, AnswerMatcher.similarity("abc", "abc"))
        assertTrue(AnswerMatcher.similarity("abcd", "abce") > 0.7)
        assertEquals(0.0, AnswerMatcher.similarity("", "abc"))
    }
}
