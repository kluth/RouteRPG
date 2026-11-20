package app.kiloquest.game

/**
 * Gemeinsames Familienziel statt Rangliste: alle Punkte (auch aus Audio-Spielen) zaehlen fuer ein Ziel.
 * Gegen Streit: Punkte je Aufgabe sind gedeckelt, juengere Kinder zaehlen etwas mehr (gleiche Chance fuer alle).
 */
data class TeamGoal(val target: Int, val collected: Int = 0, val milestones: Int = 0) {
    val progress: Double get() = if (target <= 0) 0.0 else (collected.toDouble() / target).coerceIn(0.0, 1.0)
    val reached: Boolean get() = target > 0 && collected >= target
}

object TeamGoals {
    /** Meilensteine in Prozent. */
    val MILESTONES = listOf(25, 50, 75, 100)
    private const val CAP = 15

    /** Ziel passend zur Routenlaenge: ca. 0,5 Punkte je km, gerundet auf 50, mindestens 100, hoechstens 1500. */
    fun targetFor(routeKm: Double): Int = ((routeKm * 0.5 / 50).toInt() * 50).coerceIn(100, 1500)

    fun weight(age: AgeBand): Double = when (age) { AgeBand.MINI -> 1.5; AgeBand.EXPLORER -> 1.2; AgeBand.PRO -> 1.0 }

    fun contribution(points: Int, age: AgeBand): Int = (points.coerceIn(0, CAP) * weight(age)).let { Math.round(it).toInt() }

    data class Update(val goal: TeamGoal, /** Neu erreichter Meilenstein in Prozent (25/50/75/100) oder null. */ val milestone: Int?)

    fun add(goal: TeamGoal, points: Int, age: AgeBand): Update {
        val g = goal.copy(collected = goal.collected + contribution(points, age))
        val hit = MILESTONES.count { g.collected * 100 >= it * g.target }.coerceAtMost(MILESTONES.size)
        return if (hit > goal.milestones) Update(g.copy(milestones = hit), MILESTONES[hit - 1]) else Update(g, null)
    }

    fun encode(g: TeamGoal) = "${g.target}:${g.collected}:${g.milestones}"
    fun decode(s: String?): TeamGoal? = s?.split(':')?.takeIf { it.size == 3 }?.let { p ->
        val t = p[0].toIntOrNull() ?: return null; val c = p[1].toIntOrNull() ?: return null; val m = p[2].toIntOrNull() ?: return null
        if (t <= 0 || c < 0 || m !in 0..MILESTONES.size) null else TeamGoal(t, c, m)
    }
}
