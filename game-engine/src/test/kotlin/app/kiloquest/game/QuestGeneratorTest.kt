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

    @Test
    fun `no quest is triggered near a maneuver`() {
        val avoid = listOf(2_750.0, 7_750.0, 12_750.0, 17_750.0)
        val quests = gen.generate(20_000.0, AgeBand.EXPLORER, seed = 3, avoidMeters = avoid)
        assertTrue(quests.isNotEmpty())
        for (q in quests) {
            assertTrue(avoid.none { kotlin.math.abs(it - q.triggerMeters) < 300.0 }, "quest at ${q.triggerMeters} too close to maneuver")
        }
    }

    @Test
    fun `catalog covers every age band with quizzes and breaks`() {
        val gen = QuestGenerator(QuestCatalog.default)
        for (band in AgeBand.entries) {
            val quests = gen.generate(300_000.0, band, seed = 5)
            assertTrue(quests.any { it.type == QuestType.QUIZ }, "$band needs quizzes")
            assertTrue(quests.any { it.type == QuestType.BREAK }, "$band needs breaks")
        }
    }

    @Test
    fun `catalog choice quests are consistent`() {
        for (t in QuestCatalog.default.filter { it.kind == QuestKind.CHOICE }) {
            assertEquals(3, t.optionKeys.size, t.id)
            assertTrue(t.correctIndex in t.optionKeys.indices, t.id)
        }
    }
}
