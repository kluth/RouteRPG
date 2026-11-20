package app.kiloquest.game

import kotlin.random.Random

/**
 * Plant ZUSAETZLICHE Live-Aufgaben waehrend der Fahrt (neben den vorab geplanten):
 * alle [everyMeters] eine Platzhalter-Quest aus [pool], die erst jetzt aus Daten der aktuellen
 * Umgebung bestimmt wird. Sicherheit/Ruhe: nur wenn keine Aufgabe aktiv ist, die naechste geplante
 * Aufgabe weit genug entfernt ist und kein Manoever bevorsteht.
 */
class BonusPlanner(
    private val pool: List<QuestTemplate>,
    seed: Long,
    private val everyMeters: Double = 15_000.0,
    private val minToScheduledMeters: Double = 1_500.0,
    private val minSinceLastTriggerMeters: Double = 1_000.0,
    private val minToManeuverMeters: Double = 600.0,
) {
    private val rng = Random(seed)
    private var order: List<QuestTemplate> = emptyList()
    private var index = 0
    private var lastBonusAlong = 0.0

    fun reset(alongMeters: Double) { lastBonusAlong = alongMeters; index = 0; order = emptyList() }

    fun next(
        alongMeters: Double,
        hasActive: Boolean,
        lastTriggerMeters: Double,
        nextScheduledMeters: Double?,
        metersToNextManeuver: Double?,
    ): Quest? {
        if (pool.isEmpty() || hasActive) return null
        if (alongMeters - lastBonusAlong < everyMeters) return null
        if (alongMeters - lastTriggerMeters < minSinceLastTriggerMeters) return null
        if (nextScheduledMeters != null && nextScheduledMeters - alongMeters < minToScheduledMeters) return null
        if (metersToNextManeuver != null && metersToNextManeuver < minToManeuverMeters) return null
        if (index >= order.size) { order = pool.shuffled(rng); index = 0 }
        val t = order[index++]
        lastBonusAlong = alongMeters
        return t.toQuest(alongMeters).copy(templateId = "bonus_" + t.id)
    }
}
