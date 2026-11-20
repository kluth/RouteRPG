package app.kiloquest.game

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class WikiStoriesTest {
    private fun res(n: String) = javaClass.classLoader.getResource(n)!!.readText()

    @Test
    fun `builds a geosearch url with age dependent length`() {
        val mini = WikiStories.buildUrl("de", 51.43, 6.76, AgeBand.MINI)
        val pro = WikiStories.buildUrl("de", 51.43, 6.76, AgeBand.PRO)
        assertTrue(mini.startsWith("https://de.wikipedia.org/w/api.php?"))
        assertTrue("exsentences=1" in mini && "exsentences=3" in pro)
        assertTrue("ggscoord=51.43%7C6.76" in mini)
    }

    @Test
    fun `unsafe language falls back to english`() {
        assertTrue(WikiStories.buildUrl("../evil", 1.0, 2.0, AgeBand.PRO).startsWith("https://en.wikipedia.org/"))
        assertEquals("sv", WikiStories.safeLang("sv"))
    }

    @Test
    fun `parses real wikipedia response`() {
        val list = WikiStories.parse(res("wikipedia_geosearch_de.json"), AgeBand.EXPLORER)
        assertTrue(list.size >= 4)
        assertTrue(list.all { it.title.isNotBlank() && it.text.length >= 40 })
        assertTrue(list.any { it.title.contains("Rathaus") })
    }

    @Test
    fun `cleaning removes parentheses and cuts at sentence end`() {
        val raw = "Die Burg (auch Schloss genannt, gebaut 1200) ist ein altes Gebäude. Sie steht auf einem Berg. " +
            "Heute ist sie ein Museum mit vielen Ausstellungen und einem großen Garten."
        val c = WikiStories.clean(raw, 100)
        assertTrue("(" !in c)
        assertTrue(c.endsWith("."))
        assertTrue(c.length <= 100)
    }

    @Test
    fun `cleaning cuts long single sentences at a word`() {
        val c = WikiStories.clean("Wort ".repeat(100), 60)
        assertTrue(c.length <= 61 && c.endsWith("…"))
    }

    @Test
    fun `pick prefers places kids can see`() {
        val boring = Story("Volkshochschule X", "Eine Einrichtung der Erwachsenenbildung in der Stadt und Region.")
        val castle = Story("Schloss Y", "Ein Schloss mit Türmen und einem großen Park am Fluss gelegen.")
        repeat(5) { assertEquals(castle, WikiStories.pick(listOf(boring, castle), Random(it))) }
        assertNull(WikiStories.pick(emptyList(), Random(1)))
    }

    @Test
    fun `resolve builds story quest or falls back`() {
        val ph = Quest("story", WikiStories.PENDING_KEY, QuestType.STORY, 5000.0)
        val ok = WikiStories.resolve(ph, Story("Kiruna", "Eine Stadt im Norden."))
        assertEquals(WikiStories.TEXT_KEY, ok.textKey)
        assertEquals(listOf("Kiruna", "Eine Stadt im Norden."), ok.args)
        assertEquals(RouteFacts.FALLBACK_KEY, WikiStories.resolve(ph, null).textKey)
    }
}
