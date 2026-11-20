package app.kiloquest.nav

import java.time.DayOfWeek
import java.time.LocalDateTime
import java.time.LocalTime

enum class OpenState { OPEN, CLOSED, UNKNOWN }

/** [closesAt] ist nur gesetzt, wenn der Ort offen ist und heute noch schliesst. */
data class OpenStatus(val state: OpenState, val closesAt: LocalTime? = null)

/**
 * Auswertung des OSM-Tags `opening_hours` fuer einen Zeitpunkt. Unterstuetzt wird der uebliche Kern:
 * `24/7`, `off`, Wochentage und Bereiche (`Mo-Fr`, `Sa,Su`), eine oder mehrere Zeitspannen (`08:00-12:00,14:00-18:00`),
 * Spannen ueber Mitternacht (`22:00-02:00`), mehrere Regeln mit `;` (spaetere ueberschreiben fruehere) und `off`/`closed`.
 * Alles andere (Monate, Datumsangaben, Sonnenauf-/untergang, offene Enden, Feiertage mit Sonderzeiten) ergibt [OpenState.UNKNOWN],
 * nie ein falsches "geschlossen". Feiertage (`PH`) und Schulferien (`SH`) werden ignoriert (Regel gilt dann nicht).
 */
object OpeningHours {
    private val days = listOf("mo", "tu", "we", "th", "fr", "sa", "su")
    private val timeRe = Regex("""^(\d{1,2}):(\d{2})-(\d{1,2}):(\d{2})$""")

    private class Rule(val days: Set<Int>?, val spans: List<IntRange>, val off: Boolean)

    private fun parseRules(text: String): List<Rule>? {
        val t = text.trim().lowercase().replace('–', '-').replace('—', '-')
        if (t.isEmpty()) return null
        if (t == "24/7") return listOf(Rule(null, listOf(0..1440), false))
        val rules = ArrayList<Rule>()
        for (raw in t.split(';')) {
            val r = raw.trim()
            if (r.isEmpty()) continue
            if (r == "off" || r == "closed") { rules += Rule(null, emptyList(), true); continue }
            if (r == "24/7") { rules += Rule(null, listOf(0..1440), false); continue }
            // Tokens trennen: erst Wochentage (optional), dann Zeiten oder off/closed
            val parts = r.split(Regex("\\s+"))
            var idx = 0
            var dayset: Set<Int>? = null
            var ignorable = false
            if (parts[0].isNotEmpty() && (parts[0][0].isLetter())) {
                val sel = parseDays(parts[0]) ?: return null
                dayset = sel.first
                ignorable = sel.second
                idx = 1
            }
            val rest = parts.drop(idx).joinToString(" ")
            if (rest == "off" || rest == "closed") {
                if (!ignorable) rules += Rule(dayset, emptyList(), true)
                continue
            }
            if (rest == "24/7" || rest == "00:00-24:00") { if (!ignorable) rules += Rule(dayset, listOf(0..1440), false); continue }
            if (rest.isEmpty()) return null
            val spans = ArrayList<IntRange>()
            for (s in rest.split(',')) {
                val m = timeRe.matchEntire(s.trim().replace(" ", "")) ?: return null
                val a = m.groupValues[1].toInt() * 60 + m.groupValues[2].toInt()
                var b = m.groupValues[3].toInt() * 60 + m.groupValues[4].toInt()
                if (a > 1440 || b > 1440 || m.groupValues[2].toInt() > 59 || m.groupValues[4].toInt() > 59) return null
                if (b <= a) b += 1440 // ueber Mitternacht
                spans += a..b
            }
            if (!ignorable) rules += Rule(dayset, spans, false)
        }
        return rules.ifEmpty { null }
    }

    /** Wochentage `mo`, `mo-fr`, `sa,su`; `ph`/`sh` werden als "ignorieren" markiert; sonst null (nicht unterstuetzt). */
    private fun parseDays(sel: String): Pair<Set<Int>, Boolean>? {
        val out = HashSet<Int>()
        var ignorable = false
        for (token in sel.split(',')) {
            when {
                token == "ph" || token == "sh" -> ignorable = true
                token.contains('-') -> {
                    val (a, b) = token.split('-', limit = 2).let { it[0] to it[1] }
                    val ia = days.indexOf(a); val ib = days.indexOf(b)
                    if (ia < 0 || ib < 0) return null
                    var i = ia
                    while (true) { out += i; if (i == ib) break; i = (i + 1) % 7 }
                }
                else -> { val i = days.indexOf(token); if (i < 0) return null; out += i }
            }
        }
        if (out.isEmpty() && !ignorable) return null
        return out to (ignorable && out.isEmpty())
    }

    private fun spansFor(rules: List<Rule>, dow: Int): List<IntRange> {
        var spans: List<IntRange> = emptyList()
        for (r in rules) {
            if (r.days != null && dow !in r.days) continue
            spans = if (r.off) emptyList() else r.spans
        }
        return spans
    }

    fun statusAt(openingHours: String?, at: LocalDateTime): OpenStatus {
        val rules = openingHours?.let { parseRules(it) } ?: return OpenStatus(OpenState.UNKNOWN)
        val dow = at.dayOfWeek.value - 1 // Mo = 0
        val prev = (dow + 6) % 7
        val minute = at.hour * 60 + at.minute
        // Heute offen?
        for (s in spansFor(rules, dow)) if (minute in s.first until minOf(s.last, 1440 + 1) && minute < s.last) {
            val closes = s.last
            return OpenStatus(OpenState.OPEN, if (closes < 1440) LocalTime.of(closes / 60, closes % 60) else if (closes == 1440) null else LocalTime.of((closes - 1440) / 60, (closes - 1440) % 60))
        }
        // Spanne von gestern, die ueber Mitternacht reicht?
        for (s in spansFor(rules, prev)) if (s.last > 1440 && minute < s.last - 1440) {
            val c = s.last - 1440
            return OpenStatus(OpenState.OPEN, LocalTime.of(c / 60, c % 60))
        }
        return OpenStatus(OpenState.CLOSED)
    }

    @Suppress("unused")
    private fun DayOfWeek.index() = value - 1
}
