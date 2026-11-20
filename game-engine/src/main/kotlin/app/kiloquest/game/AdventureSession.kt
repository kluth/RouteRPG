package app.kiloquest.game

data class AnswerResult(
    val correct: Boolean,
    val pointsAwarded: Int,
    /** Index der richtigen Option (nur bei CHOICE), sonst -1. */
    val correctIndex: Int,
)

/** Spielstand einer Fahrt: welche Quest ist aktiv, Punkte, Sticker. */
class AdventureSession(private val quests: List<Quest>, private val questsPerSticker: Int = 3) {
    private var nextIndex = 0
    var active: Quest? = null
        private set
    var points: Int = 0
        private set
    var completed: Int = 0
        private set
    var skipped: Int = 0
        private set

    val total: Int get() = quests.size
    val stickers: Int get() = completed / questsPerSticker

    /**
     * Mit der aktuellen Streckenposition aufrufen. Gibt die neu ausgeloeste Quest zurueck
     * (nur eine aktive Quest zugleich; eine unbeantwortete verfaellt, wenn die naechste faellig ist).
     */
    fun onProgress(alongMeters: Double): Quest? {
        val next = quests.getOrNull(nextIndex) ?: return null
        if (alongMeters < next.triggerMeters) return null
        if (active != null) skipped++
        nextIndex++
        active = next
        return next
    }

    /** [choice] nur bei CHOICE-Quests. Gibt null zurueck, wenn keine Quest aktiv ist. */
    fun answer(choice: Int? = null): AnswerResult? {
        val q = active ?: return null
        val result = when (q.kind) {
            QuestKind.CHOICE -> {
                val ok = choice != null && choice == q.correctIndex
                AnswerResult(ok, if (ok) q.points else 0, q.correctIndex)
            }
            else -> AnswerResult(true, q.points, -1)
        }
        points += result.pointsAwarded
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
