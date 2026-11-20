package app.kiloquest

import app.kiloquest.game.KiloSeries
import app.kiloquest.game.StoryRelay
import app.kiloquest.game.TeamGoals
import java.io.File
import org.junit.Test
import org.junit.Assert.assertTrue

/** Alle Texte der Audio-Spiele, Hoerserie und Teamziele muessen in Englisch (Standard) und Deutsch vorhanden sein. */
class GameStringsTest {
    private fun keys(dir: String): Map<String, String> {
        val f = File("src/main/res/$dir/strings.xml")
        return Regex("""<string name="([^"]+)"[^>]*>(.*?)</string>""", RegexOption.DOT_MATCHES_ALL).findAll(f.readText()).associate { it.groupValues[1] to it.groupValues[2] }
    }

    @Test fun everyGameTextExistsInBothLanguages() {
        val needed = buildList {
            for (i in 1..KiloSeries.EPISODES) add(KiloSeries.key(i))
            for (i in 1..StoryRelay.STARTS) add("audio_story_start_$i")
            for (i in 1..StoryRelay.NEXTS) add("audio_story_next_$i")
            add("audio_story_end")
            TeamGoals.MILESTONES.forEach { add("team_goal_$it") }
            addAll(listOf("kilo_adapt_easier", "kilo_adapt_harder", "kilo_adapt_calm", "game_chain_first", "game_chain_kilo", "game_letter_ask", "game_rhyme_ask", "game_cat_animal", "game_cat_food", "game_cat_thing", "game_cat_place"))
        }
        for (dir in listOf("values", "values-de")) {
            val k = keys(dir)
            needed.forEach { assertTrue("$dir fehlt: $it", it in k) }
        }
    }

    @Test fun seriesFirstAndLastMentionThePlace() {
        for (dir in listOf("values", "values-de")) {
            val k = keys(dir)
            assertTrue(dir, "%1\$s" in k.getValue("series_ep1") && "%1\$s" in k.getValue("series_ep6"))
        }
    }
}
