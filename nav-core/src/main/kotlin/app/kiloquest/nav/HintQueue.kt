package app.kiloquest.nav

/** Art eines Hinweises auf dem Fahr-Bildschirm. Reihenfolge = Prioritaet (kleiner = wichtiger). */
enum class HintKind { CLOSURE, JAM_HEAVY, ACCIDENT, WEATHER, JAM_LIGHT, BREAK }

/**
 * Ein Kandidat fuer das Hinweis-Banner. [aheadMeters] ist die Entfernung auf der Route,
 * [ref] verweist auf die Quelle (z. B. Meldungs-ID), die die Oberflaeche zum Beschriften nutzt.
 */
data class HintCandidate(val kind: HintKind, val aheadMeters: Double, val ref: Any? = null)

/** Ergebnis: ein einziger sichtbarer Hinweis plus Anzahl weiterer ("+N"). */
data class HintDisplay(val top: HintCandidate, val more: Int)

/**
 * Regeln aus dem Designentwurf: genau ein Banner, wichtigster zuerst (bei gleicher Art der naehere),
 * "+N" fuer die uebrigen, hoechstens [maxAheadMeters] weit voraus, und kein Banner, solange das
 * naechste Manoever naeher als [quietBeforeManeuverMeters] ist (dann nur Audio).
 */
object HintQueue {
    const val QUIET_BEFORE_MANEUVER_M = 300.0
    const val MAX_AHEAD_M = 150_000.0

    fun select(
        candidates: List<HintCandidate>,
        nextManeuverMeters: Double?,
        quietBeforeManeuverMeters: Double = QUIET_BEFORE_MANEUVER_M,
        maxAheadMeters: Double = MAX_AHEAD_M,
    ): HintDisplay? {
        if (nextManeuverMeters != null && nextManeuverMeters < quietBeforeManeuverMeters) return null
        val valid = candidates.filter { it.aheadMeters >= -1.0 && it.aheadMeters <= maxAheadMeters }
        if (valid.isEmpty()) return null
        val sorted = valid.sortedWith(compareBy<HintCandidate> { it.kind.ordinal }.thenBy { it.aheadMeters })
        return HintDisplay(sorted.first(), sorted.size - 1)
    }
}
