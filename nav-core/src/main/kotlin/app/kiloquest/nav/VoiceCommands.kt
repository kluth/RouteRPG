package app.kiloquest.nav

import java.util.Locale

enum class VoiceCommand {
    TOILET, BREAK, STORY, MUTE, UNMUTE, ETA, NEXT_TURN, SPEED_LIMIT, DESTINATION, STOP_NAVIGATION, HELP,
    /** Audio-Spiele (ohne Bildschirm), Hoerserie. */
    GAME_MENU, GAME_WORD_CHAIN, GAME_LETTERS, GAME_RHYME, GAME_STORY, GAME_STOP, SERIES,
    /** Maut/Vignetten der Route, Notfall-Karte. */
    TOLLS, EMERGENCY,
}

/**
 * Sprachbefehle fuer die Freisprech-Bedienung (Deutsch und Englisch). Die Spracherkennung laeuft nur auf dem Geraet; hier wird nur der erkannte
 * Text ausgewertet. Im Dauerbetrieb zaehlt ein Befehl nur mit Anrede "Kilo" (so reagiert die App nicht auf Gespraeche im Auto).
 */
object VoiceCommands {
    private val wake = Regex("""^(?:(?:hey|hallo|hi|ok|okay)\s+)?(?:kilo|killo|kielo|chilo)\b[\s,.:!?-]*""")

    // Reihenfolge zaehlt: spezifischere Befehle zuerst (z. B. "ansagen an" vor "ansagen aus")
    private val phrases: List<Pair<VoiceCommand, List<String>>> = listOf(
        VoiceCommand.EMERGENCY to listOf("notfall", "notruf", "unfall", "emergency", "accident", "need an ambulance", "call for help"),
        VoiceCommand.TOLLS to listOf("maut", "mautstrassen", "mautstraßen", "vignette", "vignetten", "toll roads", "tolls", "do we need a vignette"),
        VoiceCommand.GAME_STOP to listOf("spiel beenden", "spiel stoppen", "spiel aus", "hör auf zu spielen", "hoer auf zu spielen", "stop game", "end game", "stop playing", "quit the game"),
        VoiceCommand.GAME_WORD_CHAIN to listOf("wortkette", "word chain", "word game"),
        VoiceCommand.GAME_LETTERS to listOf("buchstabenspiel", "tier abc", "tier-abc", "abc spiel", "abc-spiel", "letter game", "alphabet game"),
        VoiceCommand.GAME_RHYME to listOf("reimspiel", "reime", "reim", "rhyme", "rhymes", "rhyming game"),
        VoiceCommand.GAME_STORY to listOf("geschichte weiterspinnen", "gemeinsame geschichte", "erfinde eine geschichte", "story game", "make up a story", "invent a story"),
        VoiceCommand.SERIES to listOf("hörserie", "hoerserie", "serie", "fortsetzung", "nächste folge", "naechste folge", "series", "next episode", "continue the series"),
        VoiceCommand.GAME_MENU to listOf("lass uns spielen", "wir wollen spielen", "hörspiel", "hoerspiel", "spielen", "spiel", "play a game", "let's play", "lets play", "game", "games"),
        VoiceCommand.UNMUTE to listOf("ansagen an", "ansagen ein", "ansagen wieder", "sprachansagen an", "stimme an", "unmute", "voice on", "announcements on", "turn the voice on"),
        VoiceCommand.MUTE to listOf("ansagen aus", "sprachansagen aus", "stumm", "sei still", "ruhe bitte", "mute", "be quiet", "voice off", "announcements off", "stop talking"),
        VoiceCommand.STOP_NAVIGATION to listOf("navigation beenden", "navigation stoppen", "navigation abbrechen", "fahrt beenden", "stop navigation", "end navigation", "cancel navigation"),
        VoiceCommand.TOILET to listOf("klo alarm", "klo-alarm", "kloalarm", "toilette", "ich muss mal", "wir müssen mal", "wir muessen mal", "klo", "toilet", "bathroom", "restroom", "need to pee", "potty"),
        VoiceCommand.BREAK to listOf("pause", "rastplatz", "rast machen", "raststätte", "raststaette", "rest stop", "take a break", "break", "pit stop"),
        VoiceCommand.STORY to listOf("geschichte", "erzähl", "erzaehl", "story", "tell me a story"),
        VoiceCommand.NEXT_TURN to listOf("nächste abbiegung", "naechste abbiegung", "wo muss ich abbiegen", "nächste anweisung", "naechste anweisung", "abbiegen", "next turn", "where do i turn", "next instruction"),
        VoiceCommand.SPEED_LIMIT to listOf("tempolimit", "höchstgeschwindigkeit", "hoechstgeschwindigkeit", "wie schnell darf", "speed limit", "how fast can i"),
        VoiceCommand.DESTINATION to listOf("wohin fahren wir", "wo fahren wir hin", "wohin geht es", "wo ist das ziel", "where are we going", "where are we heading", "what is the destination"),
        VoiceCommand.ETA to listOf("wie lange noch", "wann sind wir da", "wann kommen wir an", "wie weit noch", "ankunft", "how long", "when do we arrive", "how far", "arrival", "eta"),
        VoiceCommand.HELP to listOf("was kannst du", "was kann ich sagen", "hilfe", "help", "what can you do", "what can i say"),
    )

    fun normalize(text: String): String =
        text.lowercase(Locale.ROOT).replace(Regex("""[^\p{L}\p{N}\s-]"""), " ").replace(Regex("""\s+"""), " ").trim()

    /** Text hinter der Anrede "Kilo ..."; null, wenn die Anrede fehlt. */
    fun stripWake(text: String): String? {
        val n = normalize(text)
        val m = wake.find(n) ?: return null
        return n.substring(m.range.last + 1).trim()
    }

    /** Erkennt einen Befehl im Text. Mit [requireWake] nur nach der Anrede "Kilo". */
    fun parse(text: String, requireWake: Boolean): VoiceCommand? {
        val body = if (requireWake) stripWake(text) ?: return null else (stripWake(text) ?: normalize(text))
        if (body.isEmpty()) return null
        for ((cmd, list) in phrases) {
            if (list.any { phrase -> containsPhrase(body, normalize(phrase)) }) return cmd
        }
        return null
    }

    /** Erster erkannter Befehl aus mehreren Erkennungs-Alternativen (beste zuerst). */
    fun parseAny(alternatives: List<String>, requireWake: Boolean): VoiceCommand? =
        alternatives.firstNotNullOfOrNull { parse(it, requireWake) }

    // ganze Woerter bzw. Wortfolgen, damit "klo" nicht in "kloster" greift
    private fun containsPhrase(body: String, phrase: String): Boolean = Regex("""(?:^|\s)${Regex.escape(phrase)}(?:$|\s)""").containsMatchIn(body)
}
