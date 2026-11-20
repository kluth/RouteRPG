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
}
