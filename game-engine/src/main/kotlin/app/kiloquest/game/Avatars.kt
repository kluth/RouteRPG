package app.kiloquest.game

enum class AvatarCategory { VEHICLES, AIR_SEA_SPACE, ANIMALS, HEROES, FUN }

data class Avatar(val emoji: String, val category: AvatarCategory)

/**
 * Figuren, die die eigene Position auf der Karte anzeigen (Fahrzeuge, Flugzeuge, Tiere, Helden ...).
 * Es sind Emoji: sie kommen aus der Emoji-Schrift des Geraets, brauchen also keine Bilddateien und keine
 * Lizenz. Eigene Emoji sind erlaubt ([sanitizeCustom]). Sprachneutral, kein Android.
 */
object Avatars {
    const val DEFAULT = "🚗" // Auto

    private fun a(cat: AvatarCategory, vararg e: String) = e.map { Avatar(it, cat) }

    val all: List<Avatar> =
        a(
            AvatarCategory.VEHICLES,
            "🚗", "🚕", "🚙", "🏎️", "🚓", "🚑", "🚒", "🚌", "🚎", "🚐", "🛻", "🚚", "🚛", "🚜", "🏍️", "🛵", "🚲", "🛴", "🚂", "🚆", "🚋", "🛺", "🏗️", "🚧",
        ) + a(
            AvatarCategory.AIR_SEA_SPACE,
            "✈️", "🛩️", "🚁", "🚀", "🛸", "🪂", "🎈", "🛰️", "⛵", "🚤", "🛥️", "🚢", "🛶", "⛴️",
        ) + a(
            AvatarCategory.ANIMALS,
            "🦊", "🦉", "🫎", "🦌", "🐻", "🦔", "🦢", "🦭", "🐋", "🐶", "🐱", "🐴", "🦄", "🐉", "🦖", "🐢", "🐧", "🦁", "🐯", "🐼", "🐸", "🦋", "🐝", "🐙",
        ) + a(
            AvatarCategory.HEROES,
            "🦸", "🦹", "🥷", "🧙", "🧚", "🧜", "🧛", "🧞", "🤖", "👽", "👻", "🤴", "👸", "🧑‍🚀", "🧑‍🚒", "🧑‍✈️", "🏴‍☠️", "🥷🏽", "🎅", "⛄",
        ) + a(
            AvatarCategory.FUN,
            "⭐", "🌈", "🔥", "⚡", "🍕", "🍦", "🍩", "⚽", "🏆", "🎸", "🎯", "👑", "💎", "🌟",
        )

    fun byCategory(c: AvatarCategory): List<Avatar> = all.filter { it.category == c }

    private fun isEmojiCodePoint(cp: Int): Boolean =
        cp in 0x1F300..0x1FAFF || cp in 0x2600..0x27BF || cp in 0x2B00..0x2BFF || cp in 0x2190..0x21FF ||
            cp in 0x1F000..0x1F2FF || cp == 0x00A9 || cp == 0x00AE || cp in 0x2300..0x23FF || cp in 0x25A0..0x25FF || cp in 0x2900..0x297F ||
            cp in 0x1F1E6..0x1F1FF // Flaggen (Regionsbuchstaben)

    private fun isJoiner(cp: Int) = cp == 0x200D || cp == 0xFE0F || cp in 0x1F3FB..0x1F3FF || cp in 0xE0020..0xE007F

    /**
     * Macht aus beliebiger Eingabe ein einzelnes Emoji (mit Hautton, Kombination, Flagge), sonst null.
     * Text, Ziffern und Leerzeichen werden abgelehnt; nur das erste Emoji zaehlt.
     */
    fun sanitizeCustom(input: String): String? {
        val s = input.trim()
        if (s.isEmpty() || s.length > 32) return null
        val cps = s.codePoints().toArray()
        if (!isEmojiCodePoint(cps[0])) return null
        val out = StringBuilder()
        var i = 0
        var parts = 0
        while (i < cps.size) {
            val cp = cps[i]
            val ok = isEmojiCodePoint(cp) || isJoiner(cp)
            if (!ok) break
            val isJoin = cp == 0x200D
            // Ein neues Emoji ohne Verbindung zum vorigen beendet das Zeichen (nur ein Emoji erlaubt)
            if (!isJoin && !isJoiner(cp) && parts > 0) {
                val prev = cps[i - 1]
                val regionPair = cp in 0x1F1E6..0x1F1FF && prev in 0x1F1E6..0x1F1FF && parts == 1
                if (prev != 0x200D && !regionPair) break
            }
            out.appendCodePoint(cp)
            if (!isJoiner(cp)) parts++
            i++
        }
        return out.toString().takeIf { it.isNotEmpty() }
    }
}
