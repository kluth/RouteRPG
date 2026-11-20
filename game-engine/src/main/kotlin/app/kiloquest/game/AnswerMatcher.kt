package app.kiloquest.game

import java.text.Normalizer

/**
 * Ordnet gesprochene Antworten (Erkennungsergebnisse der Spracherkennung) den Optionen einer Quest zu.
 * Sprachneutral: Ordinal- und "Fertig"-Woerter kommen aus den String-Ressourcen der App.
 */
object AnswerMatcher {
    fun normalize(s: String): String =
        Normalizer.normalize(s.lowercase().replace("ß", "ss"), Normalizer.Form.NFD)
            .replace(Regex("\\p{M}+"), "")
            .replace(Regex("[^a-z0-9 ]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()

    private fun tokens(s: String) = normalize(s).split(' ').filter { it.isNotEmpty() }

    /** Aehnlichkeit 0..1 ueber die Levenshtein-Distanz. */
    fun similarity(a: String, b: String): Double {
        if (a == b) return 1.0
        if (a.isEmpty() || b.isEmpty()) return 0.0
        val d = Array(a.length + 1) { IntArray(b.length + 1) }
        for (i in 0..a.length) d[i][0] = i
        for (j in 0..b.length) d[0][j] = j
        for (i in 1..a.length) for (j in 1..b.length) {
            d[i][j] = minOf(d[i - 1][j] + 1, d[i][j - 1] + 1, d[i - 1][j - 1] + if (a[i - 1] == b[j - 1]) 0 else 1)
        }
        return 1.0 - d[a.length][b.length].toDouble() / maxOf(a.length, b.length)
    }

    /**
     * Index der gemeinten Option oder null (nichts erkannt oder nicht eindeutig).
     * [ordinals]: je Option eine Liste von Woertern fuer "erste/zweite/dritte" bzw. 1/2/3.
     */
    fun matchOption(spoken: List<String>, options: List<String>, ordinals: List<List<String>> = emptyList()): Int? {
        val opts = options.map { normalize(it) }
        val ords = ordinals.map { l -> l.map { normalize(it) }.toSet() }
        val scores = DoubleArray(opts.size)
        for (hyp in spoken) {
            val tok = tokens(hyp)
            if (tok.isEmpty()) continue
            val joined = tok.joinToString(" ")
            for ((i, o) in opts.withIndex()) {
                if (o.isEmpty()) continue
                val oTok = o.split(' ')
                var s = 0.0
                // gesamte Option als Wortfolge enthalten
                if (" $joined ".contains(" $o ")) s = 1.0
                else {
                    // jedes Wort der Option fast getroffen (Erkennungsfehler)
                    val perWord = oTok.map { w -> tok.maxOf { similarity(it, w) } }
                    val avg = perWord.average()
                    if (avg >= 0.8 && (oTok.size > 1 || o.length >= 3)) s = avg * 0.95
                }
                if (ords.getOrNull(i)?.any { it in tok } == true) s = maxOf(s, 0.9)
                if (s > scores[i]) scores[i] = s
            }
        }
        val best = scores.max()
        if (best < 0.8) return null
        val winners = scores.indices.filter { scores[it] >= best - 1e-9 }
        // Mehrdeutig (zwei Optionen gleich gut getroffen): keine Antwort
        return winners.singleOrNull()
    }

    /** Hat jemand "fertig/gefunden/ja" gesagt? */
    fun isDone(spoken: List<String>, doneWords: List<String>): Boolean {
        val words = doneWords.map { normalize(it) }.filter { it.isNotEmpty() }
        return spoken.any { hyp ->
            val joined = " ${normalize(hyp)} "
            words.any { joined.contains(" $it ") }
        }
    }
}
