package app.kiloquest.game

/** Altersstufen 4-6 / 7-10 / 11-14. */
enum class AgeBand { MINI, EXPLORER, PRO }

enum class QuestType { SPOTTING, ROUTE_FACT, ALPHABET, STORY, BREAK, QUIZ, NATURE, LANGUAGE, SKY }

/** Wie wird eine Quest abgeschlossen? */
enum class QuestKind {
    /** Einmal antippen, wenn gefunden/erledigt. */
    CHECK,
    /** Wie CHECK, Zaehl-/Mitmachaufgabe (Ergebnis wird nicht geprueft). */
    COUNT,
    /** Multiple Choice mit genau einer richtigen Antwort. */
    CHOICE,
}

/**
 * Sprachneutrale Quest-Vorlage. Der sichtbare Text kommt aus den Ressourcen
 * ueber [textKey] bzw. [optionKeys] (i18n); der Generator kennt keine Sprache.
 */
data class QuestTemplate(
    val id: String,
    val type: QuestType,
    val ageBands: Set<AgeBand>,
    val textKey: String,
    val kind: QuestKind = QuestKind.CHECK,
    val optionKeys: List<String> = emptyList(),
    val correctIndex: Int = -1,
    val points: Int = 10,
    /** Nur in diesen Laendern (ISO-Code, z. B. "DK"); leer = ueberall. */
    val countries: Set<String> = emptySet(),
    /** Tageszeit, zu der die Aufgabe Sinn ergibt. */
    val time: TimeNeed = TimeNeed.ANY,
    /** Nur noerdlich dieser Breite (z. B. Lappland). */
    val minLat: Double? = null,
) {
    /** Platzhalter, die unterwegs aus Live-Daten gefuellt werden (nie zweimal gleich, siehe Wiederholungsbremse). */
    val dynamic: Boolean get() = textKey.endsWith("_pending")
}

enum class TimeNeed { ANY, DAY, NIGHT }

/** Wo und wann wird eine Aufgabe voraussichtlich ausgeloest? [country] ist null, wenn unbekannt. */
data class QuestContext(val lat: Double, val lon: Double, val country: String?, val dark: Boolean)

/** Konkrete Quest auf einer Route, ausgeloest nach [triggerMeters] Strecke. */
data class Quest(
    val templateId: String,
    val textKey: String,
    val type: QuestType,
    val triggerMeters: Double,
    val kind: QuestKind = QuestKind.CHECK,
    val optionKeys: List<String> = emptyList(),
    val correctIndex: Int = -1,
    val points: Int = 10,
    /** Platzhalter fuer den Text (z. B. Ortsname), in Reihenfolge %1$s, %2$s ... */
    val args: List<String> = emptyList(),
)

/** Konkrete Quest aus einer Vorlage. */
fun QuestTemplate.toQuest(triggerMeters: Double) = Quest(
    templateId = id, textKey = textKey, type = type, triggerMeters = triggerMeters,
    kind = kind, optionKeys = optionKeys, correctIndex = correctIndex, points = points,
)
