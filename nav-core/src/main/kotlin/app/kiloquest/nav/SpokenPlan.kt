package app.kiloquest.nav

/** Sprachen, in denen Abbiegeansagen aus Bausteinen gebaut werden koennen. Andere Sprachen: Text von Valhalla. */
enum class SpokenLang(val tag: String) {
    DE("de"), EN("en");

    companion object {
        fun of(languageTag: String?): SpokenLang? = entries.firstOrNull { it.tag == languageTag?.lowercase()?.substringBefore('-') }
    }
}

/** Ein Teil einer Ansage: aufgenommener Baustein (Id aus [NavVocab]) oder ein Name (Strasse), der als eigener Clip gesucht wird. */
sealed interface Slot {
    data class Clip(val id: String) : Slot
    data class Name(val text: String) : Slot
}

/** Eine Ansage: [text] fuer die Geraete-Sprachausgabe (Rueckfall) und [slots] fuer aufgenommene Teilclips. Leere Slots = nur Text. */
data class Spoken(val text: String, val slots: List<Slot> = emptyList())

/**
 * Wortschatz der Abbiegeansagen. Quelle der Wahrheit fuer die Aufnahme ist `tools/voice/nav_vocab.json`; der Test
 * `SpokenPlanTest` prueft, dass beide Listen gleich sind. Texte stehen hier, weil nav-core keine Android-Strings kennt.
 */
object NavVocab {
    val DISTANCES = (100..500 step 50).toList()
    const val MAX_ROUNDABOUT_EXIT = 8

    private val ORDINAL_DE = listOf("erste", "zweite", "dritte", "vierte", "fünfte", "sechste", "siebte", "achte")
    private val ORDINAL_EN = listOf("first", "second", "third", "fourth", "fifth", "sixth", "seventh", "eighth")

    private val de = mapOf(
        "nav_left" to "links abbiegen", "nav_right" to "rechts abbiegen",
        "nav_sharp_left" to "scharf links abbiegen", "nav_sharp_right" to "scharf rechts abbiegen",
        "nav_slight_left" to "leicht links abbiegen", "nav_slight_right" to "leicht rechts abbiegen",
        "nav_straight" to "geradeaus weiterfahren", "nav_uturn" to "wenden",
        "nav_keep_left" to "links halten", "nav_keep_right" to "rechts halten",
        "nav_exit_left" to "die Ausfahrt links nehmen", "nav_exit_right" to "die Ausfahrt rechts nehmen",
        "nav_ramp_left" to "die Auffahrt links nehmen", "nav_ramp_right" to "die Auffahrt rechts nehmen",
        "nav_merge" to "einfädeln", "nav_ferry" to "die Fähre nehmen",
        "nav_onto_die" to "auf die", "nav_onto_den" to "auf den", "nav_onto_das" to "auf das",
    ) + (1..MAX_ROUNDABOUT_EXIT).associate { "nav_round_$it" to "in den Kreisverkehr fahren und die ${ORDINAL_DE[it - 1]} Ausfahrt nehmen" } +
        DISTANCES.associate { "nav_in_${it}m" to "In $it Metern" }

    private val en = mapOf(
        "nav_left" to "turn left", "nav_right" to "turn right",
        "nav_sharp_left" to "turn sharp left", "nav_sharp_right" to "turn sharp right",
        "nav_slight_left" to "bear left", "nav_slight_right" to "bear right",
        "nav_straight" to "continue straight", "nav_uturn" to "make a U-turn",
        "nav_keep_left" to "keep left", "nav_keep_right" to "keep right",
        "nav_exit_left" to "take the exit on the left", "nav_exit_right" to "take the exit on the right",
        "nav_ramp_left" to "take the ramp on the left", "nav_ramp_right" to "take the ramp on the right",
        "nav_merge" to "merge", "nav_ferry" to "take the ferry",
        "nav_onto_die" to "onto", "nav_onto_den" to "onto", "nav_onto_das" to "onto",
    ) + (1..MAX_ROUNDABOUT_EXIT).associate { "nav_round_$it" to "enter the roundabout and take the ${ORDINAL_EN[it - 1]} exit" } +
        DISTANCES.associate { "nav_in_${it}m" to "In $it meters" }

    /** Alle Ids mit Text je Sprache (fuer die Aufnahme und den Abgleich mit der Datei). */
    val ALL: Map<String, Map<SpokenLang, String>> = de.keys.associateWith { mapOf(SpokenLang.DE to de.getValue(it), SpokenLang.EN to en.getValue(it)) }

    fun text(id: String, lang: SpokenLang): String = ALL.getValue(id).getValue(lang)
}

/**
 * Baut Abbiegeansagen aus Bausteinen (Spec 016). Nur einfache Faelle (Abbiegen, Halten, Ausfahrt, Kreisverkehr ...);
 * alles andere (Start, Ziel, unbekannte Typen) liefert null, dann gilt der Text von Valhalla.
 * Strassennamen sind eigene Slots; ohne passenden Artikel (deutsche Namen wie "Am Markt") wird der Name weggelassen.
 */
object SpokenPlan {
    private val REF = Regex("^[ABEL] ?\\d{1,3}[a-z]?$")

    /** "In 300 Metern links abbiegen auf die Hauptstraße." */
    fun pre(m: Maneuver, metersToManeuver: Double, lang: SpokenLang): Spoken? {
        val core = core(m, lang) ?: return null
        val d = roundDistance(metersToManeuver)
        return join(listOf(Slot.Clip("nav_in_${d}m")) + core, lang)
    }

    /** "Links abbiegen auf die Hauptstraße." (kurz vor dem Manoever) */
    fun now(m: Maneuver, lang: SpokenLang): Spoken? = core(m, lang)?.let { join(it, lang) }

    /** Auf 50 m gerundet, zwischen 100 und 500. */
    fun roundDistance(meters: Double): Int = (Math.round(meters / 50.0) * 50).toInt().coerceIn(100, 500)

    private fun core(m: Maneuver, lang: SpokenLang): List<Slot>? {
        val action = when (m.kind) {
            ManeuverKind.LEFT -> "nav_left"
            ManeuverKind.RIGHT -> "nav_right"
            ManeuverKind.SHARP_LEFT -> "nav_sharp_left"
            ManeuverKind.SHARP_RIGHT -> "nav_sharp_right"
            ManeuverKind.SLIGHT_LEFT -> "nav_slight_left"
            ManeuverKind.SLIGHT_RIGHT -> "nav_slight_right"
            ManeuverKind.STRAIGHT -> "nav_straight"
            ManeuverKind.UTURN_LEFT, ManeuverKind.UTURN_RIGHT -> "nav_uturn"
            ManeuverKind.KEEP_LEFT -> "nav_keep_left"
            ManeuverKind.KEEP_RIGHT -> "nav_keep_right"
            ManeuverKind.EXIT_LEFT -> "nav_exit_left"
            ManeuverKind.EXIT_RIGHT -> "nav_exit_right"
            ManeuverKind.RAMP_LEFT -> "nav_ramp_left"
            ManeuverKind.RAMP_RIGHT -> "nav_ramp_right"
            ManeuverKind.MERGE -> "nav_merge"
            ManeuverKind.FERRY -> "nav_ferry"
            ManeuverKind.ROUNDABOUT -> {
                val n = m.roundaboutExit ?: return null
                if (n !in 1..NavVocab.MAX_ROUNDABOUT_EXIT) return null
                "nav_round_$n"
            }
            else -> return null
        }
        val slots = mutableListOf<Slot>(Slot.Clip(action))
        // Strassenname nur bei Abbiegen/Halten/Ausfahrt (nicht beim Kreisverkehr: "auf die" waere falsch)
        if (m.kind != ManeuverKind.ROUNDABOUT && m.kind != ManeuverKind.FERRY && m.kind != ManeuverKind.UTURN_LEFT && m.kind != ManeuverKind.UTURN_RIGHT) {
            nameSlots(m.streetNames.firstOrNull(), lang)?.let { slots += it }
        }
        return slots
    }

    private fun nameSlots(raw: String?, lang: SpokenLang): List<Slot>? {
        val name = raw?.trim()?.takeIf { it.isNotEmpty() && it.length <= 40 && '/' !in it && ';' !in it } ?: return null
        if (lang == SpokenLang.EN) return listOf(Slot.Clip("nav_onto_die"), Slot.Name(name))
        val article = if (REF.matches(name)) "nav_onto_die" else when (germanArticle(name)) {
            GermanArticle.DIE -> "nav_onto_die"
            GermanArticle.DEN -> "nav_onto_den"
            GermanArticle.DAS -> "nav_onto_das"
            null -> return null
        }
        return listOf(Slot.Clip(article), Slot.Name(name))
    }

    private fun join(slots: List<Slot>, lang: SpokenLang): Spoken {
        val words = slots.joinToString(" ") { s ->
            when (s) {
                is Slot.Clip -> NavVocab.text(s.id, lang)
                is Slot.Name -> s.text
            }
        }
        val text = words.replaceFirstChar { it.uppercase() } + "."
        return Spoken(text, slots)
    }
}

/**
 * Laesst Namen ohne Aufnahme samt dem davor stehenden Artikel-Baustein ("auf die") weg, damit der Rest in einer Stimme gesprochen werden kann.
 * [hasName] sagt, ob zu einem Namen ein Clip vorliegt.
 */
fun dropMissingNames(slots: List<Slot>, hasName: (String) -> Boolean): List<Slot> {
    val out = ArrayList<Slot>(slots.size)
    for (s in slots) {
        if (s is Slot.Name && !hasName(s.text)) {
            if ((out.lastOrNull() as? Slot.Clip)?.id?.startsWith("nav_onto_") == true) out.removeAt(out.lastIndex)
        } else out += s
    }
    return out
}

enum class GermanArticle { DIE, DEN, DAS }

/** Grober Artikel-Tipp fuer deutsche Strassennamen ("auf die Hauptstraße", "auf den Mühlenweg", "auf das ...ufer"); null = unsicher. */
fun germanArticle(name: String): GermanArticle? {
    val low = name.lowercase().trim()
    val die = listOf("straße", "strasse", "allee", "gasse", "chaussee", "brücke", "bahn", "autobahn", "promenade", "steige")
    val den = listOf("weg", "ring", "platz", "damm", "pfad", "steig", "markt", "graben", "hof", "park", "kamp", "stieg", "wall", "tunnel")
    val das = listOf("ufer", "tor")
    val last = low.substringAfterLast(' ').substringAfterLast('-')
    if (low.startsWith("am ") || low.startsWith("an ") || low.startsWith("im ") || low.startsWith("in ") || low.startsWith("zum ") || low.startsWith("zur ") || low.startsWith("auf ")) return null
    return when {
        das.any { last.endsWith(it) } -> GermanArticle.DAS
        die.any { last.endsWith(it) } -> GermanArticle.DIE
        den.any { last.endsWith(it) } -> GermanArticle.DEN
        else -> null
    }
}
