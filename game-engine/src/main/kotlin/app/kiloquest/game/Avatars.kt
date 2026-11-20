package app.kiloquest.game

enum class AvatarCategory { VEHICLES, AIR_SEA_SPACE, ANIMALS, HEROES, FUN }

/**
 * Wie eine Figur zur Fahrtrichtung passt: [POINTER] zeigt schraeg nach oben rechts (Flugzeug, Rakete) und dreht sich auf die
 * Richtung; [SIDE_LEFT] ist ein Fahrzeug in Seitenansicht, das nach links schaut und bei Fahrt nach Osten gespiegelt wird
 * (nie kopfueber); [UPRIGHT] bleibt aufrecht (Tiere, Personen, Symbole).
 */
enum class Orient { UPRIGHT, SIDE_LEFT, POINTER }

enum class ModelKind { VEHICLE, WALKER }

/**
 * 3D-Modell einer Figur als vorgerechnete Drehbilder. Fahrzeuge: [headings] = 36 Bilder (je 10 Grad). Laufende Figuren (Tiere,
 * Personen): 24 Richtungen (je 15 Grad) mit je [walkFrames] = 6 Bildern des Gehzyklus; gespeichert ist nur die Haelfte Norden bis
 * Sueden ueber Osten (13 Richtungen), die andere Haelfte entsteht durch Spiegeln.
 */
data class Model3D(val id: String, val kind: ModelKind) {
    val headings: Int get() = if (kind == ModelKind.VEHICLE) 36 else 24
    val walkFrames: Int get() = if (kind == ModelKind.VEHICLE) 1 else 6

    /** Dateinamen in `assets/figures3d/`. */
    fun assetNames(): List<String> =
        if (kind == ModelKind.VEHICLE) (0 until headings).map { "${id}_${it.toString().padStart(2, '0')}.webp" }
        else (0..headings / 2).flatMap { h -> (0 until walkFrames).map { w -> "${id}_h${h.toString().padStart(2, '0')}_w$w.webp" } }
}

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
            "🐰", "🐘", "🦒", "🐷", "🐮", "🐒", "🐠", "🦜", "🐥", "🐛", "🐗",
        ) + a(
            AvatarCategory.HEROES,
            "🦸", "🦹", "🥷", "🧙", "🧚", "🧜", "🧛", "🧞", "🤖", "👽", "👻", "🤴", "👸", "🧑‍🚀", "🧑‍🚒", "🧑‍✈️", "🏴‍☠️", "🥷🏽", "🎅", "⛄",
            "👨", "👴", "👮", "🤓", "👦", "🧔", "👧", "👩", "💁", "👱", "👷", "💂", "🧟",
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
    private val vehicles: Map<String, String> = mapOf(
        "🚗" to "sedan", "🚕" to "taxi", "🚙" to "suv", "🏎️" to "race", "🚓" to "police", "🚑" to "ambulance", "🚒" to "firetruck",
        "🚐" to "van", "🛻" to "pickup", "🚚" to "delivery", "🚛" to "garbage", "🚜" to "tractor",
        "🚆" to "train_bullet", "🚋" to "train_city", "🚂" to "train_city_b",
        "⛵" to "sailboat", "🚤" to "speedboat", "🚢" to "liner", "🛶" to "rowboat", "⛴️" to "cargoship", "🏴‍☠️" to "galleon",
        "🚀" to "rocket", "🛸" to "ufo",
    )
    /** Tiere (Kenney Cube Pets) und Personen (Kenney Mini Characters), beide CC0, mit Gehzyklus. */
    private val walkers: Map<String, String> = mapOf(
        "🐶" to "dog", "🐱" to "cat", "🦁" to "lion", "🐯" to "tiger", "🐰" to "bunny", "🐘" to "elephant", "🦒" to "giraffe",
        "🐷" to "pig", "🐮" to "cow", "🐒" to "monkey", "🐠" to "fish", "🦜" to "parrot", "🐥" to "chick", "🐛" to "caterpillar", "🐗" to "hog",
        "👨" to "male_a", "👴" to "male_b", "👮" to "male_c", "🦹" to "male_d", "🤓" to "male_e", "👦" to "male_f", "🧔" to "male_g",
        "👧" to "female_a", "👩" to "female_b", "💁" to "female_c", "👸" to "female_d", "🥷" to "female_e", "👱" to "female_f",
        "👷" to "worker", "🤖" to "robot", "💂" to "soldier", "🤴" to "knight", "👽" to "alien", "🧟" to "zombie", "🦸" to "raider",
    )

    /** Alle 3D-Modelle (fuer den Test, dass jede Datei vorhanden ist). */
    val models3d: List<Model3D> =
        vehicles.values.map { Model3D(it, ModelKind.VEHICLE) } + walkers.values.map { Model3D(it, ModelKind.WALKER) }

    /** IDs der Fahrzeug-Modelle (Kompatibilitaet). */
    val modelIds: Set<String> get() = models3d.map { it.id }.toSet()

    fun model3d(emoji: String): Model3D? =
        vehicles[emoji]?.let { Model3D(it, ModelKind.VEHICLE) } ?: walkers[emoji]?.let { Model3D(it, ModelKind.WALKER) }

    /** Modell-ID des Emoji oder null, wenn es nur als Emoji dargestellt wird (Flugzeuge, Fantasiewesen ...). */
    fun modelOf(emoji: String): String? = model3d(emoji)?.id

    /** Nummer des Drehbilds (0 bis [headings]-1) fuer die Fahrtrichtung; ohne Richtung blickt das Modell nach Osten. */
    fun headingIndex(headingDegrees: Float?, headings: Int): Int {
        val h = headingDegrees ?: return headings / 4
        return Math.round((((h % 360f) + 360f) % 360f) / (360f / headings)) % headings
    }

    fun frameFor(headingDegrees: Float?): Int = headingIndex(headingDegrees, FRAMES_3D)

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
