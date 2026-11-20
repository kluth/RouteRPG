package app.kiloquest.game

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class QuestGeneratorTest {
    private val templates = listOf(
        QuestTemplate("windmill", QuestType.SPOTTING, setOf(AgeBand.MINI, AgeBand.EXPLORER), "quest_windmill"),
        QuestTemplate("bridge", QuestType.SPOTTING, setOf(AgeBand.EXPLORER, AgeBand.PRO), "quest_bridge"),
        QuestTemplate("river", QuestType.ROUTE_FACT, setOf(AgeBand.PRO), "quest_river"),
    )
    private val gen = QuestGenerator(templates)

    @Test
    fun `short routes get no quests`() {
        assertTrue(gen.generate(800.0, AgeBand.PRO, seed = 1).isEmpty())
    }

    @Test
    fun `quests respect age band`() {
        val quests = gen.generate(50_000.0, AgeBand.MINI, seed = 1)
        assertTrue(quests.isNotEmpty())
        assertTrue(quests.all { it.templateId == "windmill" })
    }

    @Test
    fun `triggers stay inside margins and are sorted`() {
        val quests = gen.generate(30_000.0, AgeBand.EXPLORER, seed = 7)
        assertTrue(quests.all { it.triggerMeters in 500.0..29_500.0 })
        assertEquals(quests.map { it.triggerMeters }.sorted(), quests.map { it.triggerMeters })
    }

    @Test
    fun `same seed gives same result`() {
        assertEquals(
            gen.generate(40_000.0, AgeBand.PRO, seed = 42),
            gen.generate(40_000.0, AgeBand.PRO, seed = 42),
        )
    }
}
