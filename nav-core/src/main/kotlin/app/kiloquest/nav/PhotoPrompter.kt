package app.kiloquest.nav

import kotlin.random.Random

enum class PhotoReason { SCENIC, RANDOM, MANUAL }

data class PhotoPrompt(val reason: PhotoReason, val place: String? = null)

/**
 * Entscheidet, wann der Beifahrer um ein Roadtrip-Foto gebeten wird (nie der Fahrer; die Bitte geht nur an das Beifahrer-Geraet).
 * Bei schoener Gegend ([scenicScore] >= [scenicThreshold]) frueher, sonst zufaellig alle [randomMinMs]..[randomMaxMs]. Dazu: Aufwaermzeit
 * nach Fahrtbeginn, Mindestabstand, Tageslimit, Pause nach "Spaeter" und keine Bitte, solange [busy] (Abbiegung, Klo-Alarm, Umfahrung ...).
 * Zeit und Zufall werden uebergeben, damit alles deterministisch testbar ist.
 */
class PhotoPrompter(
    seed: Long = 0L,
    private val warmupMs: Long = 5 * 60_000L,
    private val minGapMs: Long = 30 * 60_000L,
    private val scenicGapMs: Long = 15 * 60_000L,
    private val randomMinMs: Long = 45 * 60_000L,
    private val randomMaxMs: Long = 90 * 60_000L,
    private val maxPerDay: Int = 8,
    private val snoozeMs: Long = 20 * 60_000L,
    private val scenicThreshold: Int = 3,
) {
    private val random = Random(seed)
    private var started = false
    private var tripStart = 0L
    private var lastPromptAt: Long? = null
    private var nextRandomAt = 0L
    private var snoozeUntil = 0L
    private var day = Int.MIN_VALUE
    private var countToday = 0

    /** Neue Fahrt: Aufwaermzeit und Zufallstermin neu setzen. Das Tageslimit bleibt (pro Kalendertag). */
    fun start(nowMs: Long) {
        started = true
        tripStart = nowMs
        nextRandomAt = nowMs + warmupMs + randomInterval()
    }

    fun stop() { started = false }

    private fun randomInterval(): Long = randomMinMs + (random.nextDouble() * (randomMaxMs - randomMinMs)).toLong()

    private fun rollDay(epochDay: Int) {
        if (epochDay != day) { day = epochDay; countToday = 0 }
    }

    /** Regelmaessig aufrufen (z. B. alle paar Sekunden). Gibt eine Bitte zurueck oder null. */
    fun onTick(nowMs: Long, epochDay: Int, scenicScore: Int, scenicPlace: String?, busy: Boolean): PhotoPrompt? {
        if (!started) return null
        rollDay(epochDay)
        if (busy || countToday >= maxPerDay || nowMs - tripStart < warmupMs || nowMs < snoozeUntil) return null
        val since = lastPromptAt?.let { nowMs - it } ?: Long.MAX_VALUE
        if (scenicScore >= scenicThreshold && since >= scenicGapMs) return emit(nowMs, PhotoReason.SCENIC, scenicPlace)
        if (nowMs >= nextRandomAt && since >= minGapMs) return emit(nowMs, PhotoReason.RANDOM, null)
        return null
    }

    /** Beifahrer drueckt selbst "Foto-Moment": immer erlaubt, zaehlt aber zum Tageslimit. */
    fun manual(nowMs: Long, epochDay: Int): PhotoPrompt {
        rollDay(epochDay)
        return emit(nowMs, PhotoReason.MANUAL, null)
    }

    /** Antwort des Beifahrers: bei "Spaeter" (taken = false) gibt es eine Pause, bei einem Foto nicht. */
    fun onAnswered(nowMs: Long, taken: Boolean) {
        if (!taken) snoozeUntil = nowMs + snoozeMs
    }

    private fun emit(nowMs: Long, reason: PhotoReason, place: String?): PhotoPrompt {
        lastPromptAt = nowMs
        countToday++
        nextRandomAt = nowMs + randomInterval()
        return PhotoPrompt(reason, place)
    }
}
