package app.kiloquest.game

import kotlin.random.Random

/**
 * Erzeugt deterministisch (per [seed]) ein Abenteuer fuer eine Route.
 * Quests werden gleichmaessig verteilt; Start und Ende bleiben frei.
 * Sicherheit: Rund um [avoidMeters] (Abbiegungen, Ausfahrten) werden keine Quests
 * ausgeloest ([avoidRadiusMeters]); betroffene Quests werden dahinter verschoben oder verworfen.
 */
class QuestGenerator(private val templates: List<QuestTemplate>) {

    fun generate(
        routeLengthMeters: Double,
        ageBand: AgeBand,
        seed: Long,
        metersPerQuest: Double = 5_000.0,
        marginMeters: Double = 500.0,
        avoidMeters: List<Double> = emptyList(),
        avoidRadiusMeters: Double = 300.0,
        /** Ort, Land und Tageszeit am Ausloesepunkt; mit dieser Funktion wird passend zu Ort/Zeit gewaehlt. */
        contextAt: ((Double) -> QuestContext)? = null,
        /** Wie oft jede feste Aufgabe in frueheren Fahrten vorkam (seltener waehlen). */
        usage: Map<String, Int> = emptyMap(),
    ): List<Quest> {
        require(routeLengthMeters >= 0) { "routeLengthMeters must be >= 0" }
        require(metersPerQuest > 0) { "metersPerQuest must be > 0" }

        val usable = routeLengthMeters - 2 * marginMeters
        if (usable <= 0) return emptyList()
        val pool = templates.filter { ageBand in it.ageBands }
        if (pool.isEmpty()) return emptyList()

        val count = (usable / metersPerQuest).toInt().coerceAtLeast(1)
        val step = usable / count
        val rng = Random(seed)
        if (contextAt != null) return generateContextual(pool, count, step, marginMeters, routeLengthMeters, rng, avoidMeters, avoidRadiusMeters, contextAt, usage)
        val order = buildList {
            // Pool wird mehrfach gemischt aneinandergehaengt: erst alle Vorlagen, dann wieder
            while (size < count) addAll(pool.shuffled(rng))
        }

        val result = ArrayList<Quest>()
        var lastTrigger = -1.0
        for (i in 0 until count) {
            val t = order[i]
            var trigger = marginMeters + step * (i + 0.5)
            val blocker = avoidMeters.firstOrNull { kotlin.math.abs(it - trigger) < avoidRadiusMeters }
            if (blocker != null) {
                // hinter die Abbiegung schieben, sofern noch Platz bleibt
                trigger = blocker + avoidRadiusMeters
                if (avoidMeters.any { kotlin.math.abs(it - trigger) < avoidRadiusMeters }) continue
            }
            if (trigger > routeLengthMeters - marginMeters || trigger <= lastTrigger) continue
            lastTrigger = trigger
            result += Quest(
                templateId = t.id,
                textKey = t.textKey,
                type = t.type,
                triggerMeters = trigger,
                kind = t.kind,
                optionKeys = t.optionKeys,
                correctIndex = t.correctIndex,
                points = t.points,
            )
        }
        return result
    }

    private fun generateContextual(
        pool: List<QuestTemplate>, count: Int, step: Double, margin: Double, routeLength: Double, rng: Random,
        avoid: List<Double>, avoidRadius: Double, contextAt: (Double) -> QuestContext, usage: Map<String, Int>,
    ): List<Quest> {
        val result = ArrayList<Quest>()
        val usedThisTrip = HashMap<String, Int>()
        var lastTrigger = -1.0
        for (i in 0 until count) {
            var trigger = margin + step * (i + 0.5)
            val blocker = avoid.firstOrNull { kotlin.math.abs(it - trigger) < avoidRadius }
            if (blocker != null) {
                trigger = blocker + avoidRadius
                if (avoid.any { kotlin.math.abs(it - trigger) < avoidRadius }) continue
            }
            if (trigger > routeLength - margin || trigger <= lastTrigger) continue
            val t = QuestFit.choose(pool, contextAt(trigger), usage, usedThisTrip, rng) ?: continue
            usedThisTrip.merge(t.id, 1, Int::plus)
            lastTrigger = trigger
            result += t.toQuest(trigger)
        }
        return result
    }
}
