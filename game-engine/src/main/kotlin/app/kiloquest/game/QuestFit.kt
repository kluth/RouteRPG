package app.kiloquest.game

import kotlin.random.Random

/** Passt eine Vorlage zu Ort und Tageszeit? Und wie stark soll sie bevorzugt werden? */
object QuestFit {
    fun fits(t: QuestTemplate, ctx: QuestContext?): Boolean {
        if (ctx == null) return t.countries.isEmpty() && t.minLat == null && t.time == TimeNeed.ANY
        if (t.countries.isNotEmpty() && (ctx.country == null || ctx.country !in t.countries)) return false
        if (t.minLat != null && ctx.lat < t.minLat) return false
        if (t.time == TimeNeed.DAY && ctx.dark) return false
        if (t.time == TimeNeed.NIGHT && !ctx.dark) return false
        return true
    }

    /**
     * Gewichtete Auswahl unter den passenden Vorlagen. Orts- und tageszeitspezifische werden bevorzugt,
     * Live-Aufgaben (dynamisch) zaehlen doppelt, bereits oft genutzte feste Aufgaben seltener.
     * [usage] = Nutzung ueber fruehere Fahrten, [usedThisTrip] = in dieser Fahrt; feste Aufgaben gibt es
     * hoechstens [maxStaticPerTrip]-mal pro Fahrt.
     */
    fun choose(
        pool: List<QuestTemplate>,
        ctx: QuestContext?,
        usage: Map<String, Int>,
        usedThisTrip: Map<String, Int>,
        rng: Random,
        maxStaticPerTrip: Int = 1,
    ): QuestTemplate? {
        val cands = pool.filter { fits(it, ctx) && (it.dynamic || (usedThisTrip[it.id] ?: 0) < maxStaticPerTrip) }
        if (cands.isEmpty()) return null
        val weights = cands.map { t ->
            var w = if (t.dynamic) 2.0 else 1.0
            if (t.countries.isNotEmpty() || t.minLat != null) w *= 3.0
            if (t.time != TimeNeed.ANY) w *= 2.0
            if (!t.dynamic) w /= (1 + (usage[t.id] ?: 0)).toDouble()
            w
        }
        var r = rng.nextDouble() * weights.sum()
        for ((i, w) in weights.withIndex()) { if (r < w) return cands[i]; r -= w }
        return cands.last()
    }
}

/** Grobe, bewusst vorsichtige Laender-Schaetzung ohne Netz (nur Kerngebiete, an Grenzen null). */
object CountryGuess {
    private data class Box(val code: String, val minLat: Double, val maxLat: Double, val minLon: Double, val maxLon: Double)
    private val boxes = listOf(
        Box("DK", 54.95, 57.60, 8.20, 12.50),
        Box("SE", 55.60, 69.00, 12.80, 24.00),
        Box("DE", 50.00, 54.40, 8.20, 13.30),
        Box("DE", 47.80, 50.00, 8.20, 13.00),
        Box("NO", 58.50, 71.00, 5.00, 11.50),
        Box("FI", 60.50, 70.00, 22.50, 31.00),
    )

    fun guess(lat: Double, lon: Double): String? =
        boxes.firstOrNull { lat in it.minLat..it.maxLat && lon in it.minLon..it.maxLon }?.code
}
