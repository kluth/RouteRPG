package app.kiloquest.game

import app.kiloquest.game.AgeBand.EXPLORER
import app.kiloquest.game.AgeBand.MINI
import app.kiloquest.game.AgeBand.PRO
import app.kiloquest.game.QuestKind.CHECK
import app.kiloquest.game.QuestKind.CHOICE
import app.kiloquest.game.QuestKind.COUNT
import app.kiloquest.game.QuestType.ALPHABET
import app.kiloquest.game.QuestType.BREAK
import app.kiloquest.game.QuestType.QUIZ
import app.kiloquest.game.QuestType.SPOTTING

/**
 * Start-Katalog. Alle Texte stehen in den Android-String-Ressourcen unter
 * `quest_<id>` und `quest_<id>_opt1..n` (EN Standard, DE). Ein Test prueft die Vollstaendigkeit.
 */
object QuestCatalog {
    private fun t(id: String, type: QuestType, ages: Set<AgeBand>, kind: QuestKind = CHECK, options: Int = 0, correct: Int = -1) =
        QuestTemplate(
            id = id, type = type, ageBands = ages, textKey = "quest_$id", kind = kind,
            optionKeys = (1..options).map { "quest_${id}_opt$it" }, correctIndex = correct,
            points = if (kind == CHOICE) 15 else 10,
        )

    val default: List<QuestTemplate> = listOf(
        t("windmill", SPOTTING, setOf(MINI, EXPLORER, PRO)),
        t("cow", SPOTTING, setOf(MINI, EXPLORER)),
        t("bridge", SPOTTING, setOf(MINI, EXPLORER, PRO)),
        t("church", SPOTTING, setOf(EXPLORER, PRO)),
        t("ship", SPOTTING, setOf(MINI, EXPLORER, PRO)),
        t("red_cars", SPOTTING, setOf(MINI, EXPLORER), COUNT),
        t("trucks", SPOTTING, setOf(EXPLORER, PRO), COUNT),
        t("plates", SPOTTING, setOf(EXPLORER, PRO), COUNT),
        t("alphabet", ALPHABET, setOf(EXPLORER, PRO), COUNT),
        t("quiz_spider", QUIZ, setOf(MINI, EXPLORER), CHOICE, 3, 1),
        t("quiz_green", QUIZ, setOf(MINI, EXPLORER), CHOICE, 3, 0),
        t("quiz_reindeer", QUIZ, setOf(MINI, EXPLORER, PRO), CHOICE, 3, 0),
        t("quiz_capital_dk", QUIZ, setOf(EXPLORER, PRO), CHOICE, 3, 0),
        t("quiz_week", QUIZ, setOf(MINI), CHOICE, 3, 1),
        t("quiz_elephant", QUIZ, setOf(MINI, EXPLORER), CHOICE, 3, 0),
        t("quiz_planet", QUIZ, setOf(EXPLORER, PRO), CHOICE, 3, 0),
        t("quiz_oresund", QUIZ, setOf(PRO), CHOICE, 3, 0),
        t("break_stretch", BREAK, setOf(MINI, EXPLORER, PRO)),
        t("break_sing", BREAK, setOf(MINI, EXPLORER)),
        t("break_count", BREAK, setOf(EXPLORER, PRO)),
        // Platzhalter: Text wird unterwegs aus OSM-Daten der aktuellen Umgebung bestimmt (siehe RouteFacts)
        QuestTemplate("route_fact", QuestType.ROUTE_FACT, setOf(MINI, EXPLORER, PRO), RouteFacts.PENDING_KEY),
        QuestTemplate("route_fact_2", QuestType.ROUTE_FACT, setOf(MINI, EXPLORER, PRO), RouteFacts.PENDING_KEY),
        // Platzhalter: Geschichte zu einem Ort in der Naehe aus Wikipedia (siehe WikiStories)
        QuestTemplate("story", QuestType.STORY, setOf(MINI, EXPLORER, PRO), WikiStories.PENDING_KEY),
        QuestTemplate("story_2", QuestType.STORY, setOf(MINI, EXPLORER, PRO), WikiStories.PENDING_KEY),
    )
}
