package app.kiloquest.game

/**
 * Adaptive Schwierigkeit ohne Profilbildung: merkt sich nur die letzten Ergebnisse dieser Fahrt (nichts wird gespeichert).
 * Kilo sagt nie "verloren": bei Frust wird es leichter oder es gibt eine ruhige Pause, bei Unterforderung etwas schwerer.
 */
class FrustrationGuard(private val window: Int = 6, private val cooldown: Int = 4) {
    enum class Outcome { RIGHT, WRONG, SKIPPED, DONE }
    enum class Adjust { NONE, EASIER, HARDER, CALM }

    private val recent = ArrayDeque<Outcome>()
    private var sinceAdjust = cooldown

    fun record(o: Outcome): Adjust {
        recent.addLast(o)
        while (recent.size > window) recent.removeFirst()
        sinceAdjust++
        if (sinceAdjust < cooldown) return Adjust.NONE
        val last3 = recent.takeLast(3)
        val bad = recent.count { it == Outcome.WRONG || it == Outcome.SKIPPED }
        val adjust = when {
            recent.size >= 5 && bad >= 5 -> Adjust.CALM
            last3.size == 3 && last3.all { it == Outcome.WRONG || it == Outcome.SKIPPED } -> Adjust.EASIER
            recent.takeLast(4).let { it.size == 4 && it.all { x -> x == Outcome.RIGHT } } -> Adjust.HARDER
            else -> Adjust.NONE
        }
        if (adjust != Adjust.NONE) { sinceAdjust = 0; recent.clear() }
        return adjust
    }

    fun reset() { recent.clear(); sinceAdjust = cooldown }

    companion object {
        fun harder(a: AgeBand): AgeBand = when (a) { AgeBand.MINI -> AgeBand.EXPLORER; AgeBand.EXPLORER -> AgeBand.PRO; AgeBand.PRO -> AgeBand.PRO }
        fun easier(a: AgeBand): AgeBand = when (a) { AgeBand.PRO -> AgeBand.EXPLORER; AgeBand.EXPLORER -> AgeBand.MINI; AgeBand.MINI -> AgeBand.MINI }
    }
}
