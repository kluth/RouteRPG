package app.kiloquest.game

import kotlin.random.Random

/**
 * Erzeugt deterministisch (per [seed]) ein Abenteuer fuer eine Route.
 * Quests werden gleichmaessig verteilt; Start und Ende bleiben frei,
 * damit nie waehrend Einfaedeln oder Ankunft Aufgaben kommen.
 */
class QuestGenerator(private val templates: List<QuestTemplate>) {

    fun generate(
        routeLengthMeters: Double,
        ageBand: AgeBand,
        seed: Long,
        metersPerQuest: Double = 5_000.0,
        marginMeters: Double = 500.0,
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
        val order = pool.shuffled(rng)

        return (0 until count).map { i ->
            val t = order[i % order.size]
            Quest(
                templateId = t.id,
                textKey = t.textKey,
                type = t.type,
                triggerMeters = marginMeters + step * (i + 0.5),
            )
        }
    }
}
