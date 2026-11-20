package app.kiloquest.nav

/** Ansage "Tempolimit N" bei Tempoueberschreitung. */
data class OverspeedAlert(val limitKmh: Int, val speedKmh: Int)

/**
 * Entscheidet, wann bei zu hoher Geschwindigkeit gewarnt wird (nur Audio, nie Bedienaufgaben). Warnt erst, wenn das Limit um mehr als
 * [toleranceKmh] dauerhaft ([holdMs]) ueberschritten wird (GPS-Ausreisser), wiederholt hoechstens alle [repeatMs] und sofort erneut,
 * wenn ein niedrigeres Limit beginnt. Navigationsansagen haben Vorrang: bei [busy] wird nichts verbraucht, die Warnung kommt spaeter.
 * Zeit wird uebergeben, damit die Logik deterministisch testbar bleibt.
 */
class OverspeedAlerter(
    private val toleranceKmh: Int = 5,
    private val holdMs: Long = 4_000,
    private val repeatMs: Long = 90_000,
    private val clearAfterBelowMs: Long = 10_000,
) {
    private var overSince: Long? = null
    private var belowSince: Long? = null
    private var lastAlertAt: Long? = null
    private var lastAlertLimit: Int? = null

    fun onTick(nowMs: Long, speedKmh: Double?, limitKmh: Int?, busy: Boolean = false): OverspeedAlert? {
        if (speedKmh == null || limitKmh == null || !SpeedProfile.isOverspeed(speedKmh, limitKmh, toleranceKmh)) {
            overSince = null
            if (belowSince == null) belowSince = nowMs
            // Laenger wieder im Limit: die naechste Ueberschreitung wird sofort wieder gemeldet
            if (nowMs - (belowSince ?: nowMs) >= clearAfterBelowMs) { lastAlertAt = null; lastAlertLimit = null }
            return null
        }
        belowSince = null
        val since = overSince ?: nowMs.also { overSince = it }
        if (nowMs - since < holdMs || busy) return null
        val last = lastAlertAt
        val lowerLimit = lastAlertLimit?.let { limitKmh < it } == true
        if (last != null && nowMs - last < repeatMs && !lowerLimit) return null
        lastAlertAt = nowMs
        lastAlertLimit = limitKmh
        return OverspeedAlert(limitKmh, speedKmh.toInt())
    }

    fun reset() { overSince = null; belowSince = null; lastAlertAt = null; lastAlertLimit = null }
}
