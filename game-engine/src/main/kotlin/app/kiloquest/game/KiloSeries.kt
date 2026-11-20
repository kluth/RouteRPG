package app.kiloquest.game

/**
 * Kilo-Hoerserie ueber die Reise: [EPISODES] Folgen mit Cliffhanger. Pro Kalendertag wird eine weitere Folge freigeschaltet
 * (ab dem Tag der ersten Folge), so gibt es auf Mehrtagesreisen jeden Tag eine Fortsetzung. Texte stehen in den Ressourcen (series_ep1 ...).
 */
object KiloSeries {
    const val EPISODES = 6

    fun key(ep: Int) = "series_ep$ep"

    /** Wie viele Folgen sind am Tag [today] (Epochentag) freigeschaltet? [firstDay] = Tag der ersten gehoerten Folge, null = noch keine. */
    fun available(firstDay: Int?, today: Int): Int = if (firstDay == null) 1 else (today - firstDay + 1).coerceIn(1, EPISODES)

    /** Naechste ungehoerte, freigeschaltete Folge oder null. */
    fun next(heard: Set<Int>, availableCount: Int): Int? = (1..availableCount.coerceAtMost(EPISODES)).firstOrNull { it !in heard }

    fun encode(heard: Set<Int>) = heard.sorted().joinToString(",")
    fun decode(s: String?): Set<Int> = s?.split(',')?.mapNotNull { it.trim().toIntOrNull() }?.filter { it in 1..EPISODES }?.toSet() ?: emptySet()
}
