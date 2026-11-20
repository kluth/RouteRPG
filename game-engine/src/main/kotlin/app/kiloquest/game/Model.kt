package app.kiloquest.game

/** Altersstufen 4-6 / 7-10 / 11-14. */
enum class AgeBand { MINI, EXPLORER, PRO }

enum class QuestType { SPOTTING, ROUTE_FACT, ALPHABET, STORY, BREAK }

/**
 * Sprachneutrale Quest-Vorlage. Der sichtbare Text kommt aus den Ressourcen
 * ueber [textKey] (i18n), der Generator kennt keine Sprache.
 */
data class QuestTemplate(
    val id: String,
    val type: QuestType,
    val ageBands: Set<AgeBand>,
    val textKey: String,
)

/** Konkrete Quest auf einer Route, ausgeloest nach [triggerMeters] Strecke. */
data class Quest(
    val templateId: String,
    val textKey: String,
    val type: QuestType,
    val triggerMeters: Double,
)
