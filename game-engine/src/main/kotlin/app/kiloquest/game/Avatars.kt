package app.kiloquest.game

enum class AvatarCategory { VEHICLES, AIR_SEA_SPACE, ANIMALS, HEROES, FUN }

/**
 * Wie eine Figur zur Fahrtrichtung passt: [POINTER] zeigt schraeg nach oben rechts (Flugzeug, Rakete) und dreht sich auf die
 * Richtung; [SIDE_LEFT] ist ein Fahrzeug in Seitenansicht, das nach links schaut und bei Fahrt nach Osten gespiegelt wird
 * (nie kopfueber); [UPRIGHT] bleibt aufrecht (Tiere, Personen, Symbole).
 */
enum class Orient { UPRIGHT, SIDE_LEFT, POINTER }

/** Darstellung einer Figur auf der Karte: [rotationDegrees] im Uhrzeigersinn, [mirrored] = links/rechts gespiegelt. */
data class MarkerPose(val rotationDegrees: Float, val mirrored: Boolean)

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

    private val pointers = setOf("✈️", "🛩️", "🚀")
    private val sideLeft = setOf("🚗", "🚕", "🚙", "🏎️", "🚓", "🚑", "🚒", "🚌", "🚎", "🚐", "🛻", "🚚", "🚛", "🚜", "🏍️", "🛵", "🚲", "🛴", "🚂", "🚋", "🛺")

    fun orientOf(emoji: String): Orient = when (emoji) {
        in pointers -> Orient.POINTER
        in sideLeft -> Orient.SIDE_LEFT
        else -> Orient.UPRIGHT
    }

    /**
     * Pose fuer die Fahrtrichtung [headingDegrees] (0 = Norden, im Uhrzeigersinn). Ohne bekannte Richtung (null) bleibt die Figur
     * unveraendert. Zeigt die Fahrt nach Osten (0 bis 180 Grad), schaut ein Seitenansicht-Fahrzeug nach rechts.
     */
    fun pose(emoji: String, headingDegrees: Float?): MarkerPose {
        if (headingDegrees == null) return MarkerPose(0f, false)
        val h = ((headingDegrees % 360f) + 360f) % 360f
        return when (orientOf(emoji)) {
            Orient.POINTER -> MarkerPose(((h - 45f) + 360f) % 360f, false)
            Orient.SIDE_LEFT -> MarkerPose(0f, h in 1f..179f)
            Orient.UPRIGHT -> MarkerPose(0f, false)
        }
    }

    /**
     * Fahrzeuge mit echtem 3D-Modell (Kenney, CC0). Das Modell wurde in [FRAMES_3D] Drehbilder (je 10 Grad) gerechnet und liegt
     * als `figures3d/<id>_NN.webp` in den App-Assets; NN = Fahrtrichtung / 10 (00 = Norden, im Uhrzeigersinn).
     */
    const val FRAMES_3D = 36
    private val models: Map<String, String> = mapOf(
        "🚗" to "sedan", "🚕" to "taxi", "🚙" to "suv", "🏎️" to "race", "🚓" to "police", "🚑" to "ambulance", "🚒" to "firetruck",
        "🚐" to "van", "🛻" to "pickup", "🚚" to "delivery", "🚛" to "garbage", "🚜" to "tractor",
        "🚆" to "train_bullet", "🚋" to "train_city", "🚂" to "train_city_b",
        "⛵" to "sailboat", "🚤" to "speedboat", "🚢" to "liner", "🛶" to "rowboat", "⛴️" to "cargoship", "🏴‍☠️" to "galleon",
        "🚀" to "rocket", "🛸" to "ufo",
    )

    /** Alle Modell-IDs (fuer den Test, dass jede Datei vorhanden ist). */
    val modelIds: Set<String> get() = models.values.toSet()

    /** Modell-ID des Emoji oder null, wenn es nur als Emoji dargestellt wird (Tiere, Personen, Flugzeuge ...). */
    fun modelOf(emoji: String): String? = models[emoji]

    /** Nummer des Drehbilds (0 bis 35) fuer die Fahrtrichtung; ohne Richtung blickt das Modell nach Osten (Bild 9). */
    fun frameFor(headingDegrees: Float?): Int {
        val h = headingDegrees ?: return FRAMES_3D / 4
        return Math.round((((h % 360f) + 360f) % 360f) / (360f / FRAMES_3D)) % FRAMES_3D
    }

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
