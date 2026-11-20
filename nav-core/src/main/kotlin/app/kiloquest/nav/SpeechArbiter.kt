package app.kiloquest.nav

/**
 * Entscheidet, wann Quest-Ansagen gesprochen werden duerfen. Navigationsansagen haben Vorrang:
 * Eine Quest wird erst gesprochen, wenn gerade nichts gesprochen wird und die letzte
 * Navigationsansage mindestens [quietAfterNavMs] zurueckliegt. Bis dahin wartet sie (hoechstens
 * [maxWaitMs], danach verfaellt der Sprachtext; die Quest bleibt in der Kinderansicht sichtbar).
 */
class SpeechArbiter(
    private val quietAfterNavMs: Long = 8_000,
    private val maxWaitMs: Long = 60_000,
) {
    private var lastNavAt = Long.MIN_VALUE / 2
    private var pending: String? = null
    private var pendingSince = 0L

    fun navSpoken(nowMs: Long) { lastNavAt = nowMs }

    /** Quest-Text anbieten. Gibt den Text zurueck, wenn er sofort gesprochen werden darf. */
    fun offerQuest(text: String, nowMs: Long, speaking: Boolean): String? {
        pending = text
        pendingSince = nowMs
        return poll(nowMs, speaking)
    }

    /** Regelmaessig aufrufen (z. B. bei jedem Positions-Update). */
    fun poll(nowMs: Long, speaking: Boolean): String? {
        val text = pending ?: return null
        if (nowMs - pendingSince > maxWaitMs) { pending = null; return null }
        if (speaking || nowMs - lastNavAt < quietAfterNavMs) return null
        pending = null
        return text
    }

    fun cancelQuest() { pending = null }
}
