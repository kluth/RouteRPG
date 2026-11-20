package app.kiloquest.game

data class AnswerResult(
    val correct: Boolean,
    val pointsAwarded: Int,
    /** Index der richtigen Option (nur bei CHOICE), sonst -1. */
    val correctIndex: Int,
    /** Welches Teammitglied hat geantwortet (0-basiert). */
    val player: Int = 0,
)

/**
 * Spielstand einer Fahrt: aktive Quest, Punkte, Sticker.
 * Team-Modus: bei [playerCount] > 1 sind die Kinder reihum dran (Quest 1 -> Kind 1, Quest 2 -> Kind 2 ...),
 * Punkte werden pro Kind gefuehrt, Sticker gehoeren dem ganzen Team.
 */
class AdventureSession(
    initialQuests: List<Quest>,
    private val questsPerSticker: Int = 3,
    val playerCount: Int = 1,
) {
    init { require(playerCount >= 1) { "playerCount must be >= 1" } }

    private val quests: MutableList<Quest> = initialQuests.toMutableList()

    private var nextIndex = 0
    val scores = IntArray(playerCount)

    var active: Quest? = null
        private set
    /** Wer ist bei der aktiven Quest dran? */
    var currentPlayer: Int = 0
        private set
    var completed: Int = 0
        private set
    var skipped: Int = 0
        private set

    val points: Int get() = scores.sum()
    val total: Int get() = quests.size

    /** Strecke (inkl. Offset) der zuletzt ausgeloesten Quest; fuer die Planung von Live-Aufgaben. */
    var lastTriggerMeters: Double = Double.NEGATIVE_INFINITY
        private set

    /** Noch nicht ausgeloeste Quests in Reihenfolge (fuer die Vorschau). */
    fun upcoming(): List<Quest> = quests.drop(nextIndex)

    /** Ersetzt eine noch nicht ausgeloeste Quest (z. B. Platzhalter durch vorab geladenen Text). */
    fun replaceUpcoming(old: Quest, new: Quest): Boolean {
        val i = quests.indexOfFirst { it === old || it == old }
        if (i < nextIndex) return false
        quests[i] = new
        return true
    }

    /** Fuegt eine zusaetzliche Live-Quest als naechste ein (wird beim naechsten [onProgress] ausgeloest). */
    fun insertBonus(quest: Quest) {
        quests.add(nextIndex, quest)
    }
    val stickers: Int get() = completed / questsPerSticker

    /**
     * Mit der aktuellen Streckenposition aufrufen. Gibt die neu ausgeloeste Quest zurueck
     * (nur eine aktive Quest zugleich; eine unbeantwortete verfaellt, wenn die naechste faellig ist).
     */
    fun onProgress(alongMeters: Double): Quest? {
        val next = quests.getOrNull(nextIndex) ?: return null
        if (alongMeters < next.triggerMeters) return null
        if (active != null) skipped++
        currentPlayer = nextIndex % playerCount
        nextIndex++
        active = next
        lastTriggerMeters = next.triggerMeters
        return next
    }

    /** Setzt eine Quest ausser der Reihe aktiv (z. B. Bewegungsaufgabe bei der Pause). Die geplante Reihenfolge bleibt. */
    fun forceQuest(quest: Quest): Quest {
        if (active != null) skipped++
        currentPlayer = nextIndex % playerCount
        active = quest
        return quest
    }

    /** Ersetzt die aktive Quest (z. B. Streckenfakt-Platzhalter durch konkreten Text). */
    fun replaceActive(quest: Quest) {
        if (active != null) active = quest
    }

    /** [choice] nur bei CHOICE-Quests. Gibt null zurueck, wenn keine Quest aktiv ist. */
    fun answer(choice: Int? = null): AnswerResult? {
        val q = active ?: return null
        val result = when (q.kind) {
            QuestKind.CHOICE -> {
                val ok = choice != null && choice == q.correctIndex
                AnswerResult(ok, if (ok) q.points else 0, q.correctIndex, currentPlayer)
            }
            else -> AnswerResult(true, q.points, -1, currentPlayer)
        }
        scores[currentPlayer] += result.pointsAwarded
        completed++
        active = null
        return result
    }

    fun skip() {
        if (active != null) {
            skipped++
            active = null
        }
    }
}
