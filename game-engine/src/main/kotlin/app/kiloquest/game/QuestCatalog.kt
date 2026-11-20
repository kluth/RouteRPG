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
    private fun t(
        id: String, type: QuestType, ages: Set<AgeBand>, kind: QuestKind = CHECK, options: Int = 0, correct: Int = -1,
        countries: Set<String> = emptySet(), time: TimeNeed = TimeNeed.ANY, minLat: Double? = null,
    ) = QuestTemplate(
        id = id, type = type, ageBands = ages, textKey = "quest_$id", kind = kind,
        optionKeys = (1..options).map { "quest_${id}_opt$it" }, correctIndex = correct,
        points = if (kind == CHOICE) 15 else 10,
        countries = countries, time = time, minLat = minLat,
    )

    private val DAY = TimeNeed.DAY
    private val NIGHT = TimeNeed.NIGHT

    val default: List<QuestTemplate> = listOf(
        t("windmill", SPOTTING, setOf(MINI, EXPLORER, PRO), time = DAY),
        t("cow", SPOTTING, setOf(MINI, EXPLORER), time = DAY),
        t("bridge", SPOTTING, setOf(MINI, EXPLORER, PRO), time = DAY),
        t("church", SPOTTING, setOf(EXPLORER, PRO), time = DAY),
        t("ship", SPOTTING, setOf(MINI, EXPLORER, PRO), time = DAY),
        t("red_cars", SPOTTING, setOf(MINI, EXPLORER), COUNT),
        t("trucks", SPOTTING, setOf(EXPLORER, PRO), COUNT),
        t("plates", SPOTTING, setOf(EXPLORER, PRO), COUNT),
        t("alphabet", ALPHABET, setOf(EXPLORER, PRO), COUNT),
        t("quiz_spider", QUIZ, setOf(MINI, EXPLORER), CHOICE, 3, 1),
        t("quiz_green", QUIZ, setOf(MINI, EXPLORER), CHOICE, 3, 0),
        t("quiz_reindeer", QUIZ, setOf(MINI, EXPLORER, PRO), CHOICE, 3, 0, minLat = 62.0),
        t("quiz_capital_dk", QUIZ, setOf(EXPLORER, PRO), CHOICE, 3, 0, countries = setOf("DK")),
        t("quiz_week", QUIZ, setOf(MINI), CHOICE, 3, 1),
        t("quiz_elephant", QUIZ, setOf(MINI, EXPLORER), CHOICE, 3, 0),
        t("quiz_planet", QUIZ, setOf(EXPLORER, PRO), CHOICE, 3, 0),
        t("quiz_oresund", QUIZ, setOf(PRO), CHOICE, 3, 0, countries = setOf("DK", "SE")),
        // Ort- und tageszeitabhaengig (siehe QuestFit)
        t("night_stars", SPOTTING, setOf(MINI, EXPLORER, PRO), time = NIGHT),
        t("night_tail", SPOTTING, setOf(MINI, EXPLORER), COUNT, time = NIGHT),
        t("day_clouds", SPOTTING, setOf(MINI, EXPLORER, PRO), time = DAY),
        t("quiz_capital_de", QUIZ, setOf(MINI, EXPLORER), CHOICE, 3, 0, countries = setOf("DE")),
        t("quiz_capital_se", QUIZ, setOf(EXPLORER, PRO), CHOICE, 3, 0, countries = setOf("SE")),
        t("flag_dk", SPOTTING, setOf(MINI, EXPLORER, PRO), time = DAY, countries = setOf("DK")),
        t("moose_sign", SPOTTING, setOf(MINI, EXPLORER, PRO), countries = setOf("SE", "NO", "FI"), minLat = 58.0, time = DAY),
        t("quiz_sami", QUIZ, setOf(EXPLORER, PRO), CHOICE, 3, 0, countries = setOf("SE", "NO", "FI"), minLat = 65.0),
        t("break_stretch", BREAK, setOf(MINI, EXPLORER, PRO)),
        t("break_sing", BREAK, setOf(MINI, EXPLORER)),
        t("break_count", BREAK, setOf(EXPLORER, PRO)),
        // Platzhalter: Text wird unterwegs aus OSM-Daten der aktuellen Umgebung bestimmt (siehe RouteFacts)
        QuestTemplate("route_fact", QuestType.ROUTE_FACT, setOf(MINI, EXPLORER, PRO), RouteFacts.PENDING_KEY),
        QuestTemplate("route_fact_2", QuestType.ROUTE_FACT, setOf(MINI, EXPLORER, PRO), RouteFacts.PENDING_KEY),
        // Platzhalter: Geschichte zu einem Ort in der Naehe aus Wikipedia (siehe WikiStories)
        QuestTemplate("story", QuestType.STORY, setOf(MINI, EXPLORER, PRO), WikiStories.PENDING_KEY),
        QuestTemplate("story_2", QuestType.STORY, setOf(MINI, EXPLORER, PRO), WikiStories.PENDING_KEY),
        // Platzhalter: Tiere aus Beobachtungen der Umgebung (siehe NatureSpotting)
        QuestTemplate("nature", QuestType.NATURE, setOf(MINI, EXPLORER, PRO), NatureSpotting.PENDING_KEY),
        QuestTemplate("nature_2", QuestType.NATURE, setOf(MINI, EXPLORER, PRO), NatureSpotting.PENDING_KEY),
        // Platzhalter: Wort-Quiz in der Sprache des aktuellen Landes (siehe LanguageCourses); ohne Kurs Rueckfall-Aufgabe
        // Platzhalter: Himmel (Polarlicht/Mond bei Dunkelheit, sonst Flugzeug ueber euch), siehe Sky.kt
        QuestTemplate("sky", QuestType.SKY, setOf(MINI, EXPLORER, PRO), PlaneSpotting.PENDING_KEY),
        QuestTemplate("sky_2", QuestType.SKY, setOf(MINI, EXPLORER, PRO), PlaneSpotting.PENDING_KEY),
        QuestTemplate("lang", QuestType.LANGUAGE, setOf(EXPLORER, PRO), LanguageCourses.PENDING_KEY),
    )
}
