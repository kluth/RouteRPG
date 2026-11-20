package app.kiloquest.game

import kotlin.random.Random

/** Sprache der Wortlisten (die Spiellogik selbst ist sprachneutral). */
enum class Lang { DE, EN;
    companion object { fun of(code: String?): Lang = if (code?.lowercase()?.startsWith("de") == true) DE else EN }
}

enum class AudioGameType { WORD_CHAIN, LETTER_CATEGORY, RHYME, STORY_RELAY }

/** Kleine kindgerechte Wortlisten (Hauptwoerter), nur fuer Kilos eigene Zuege und Reimsammlungen. */
object WordLists {
    val de = ("Apfel Ananas Ameise Auto Ball Banane Baum Berg Biene Blume Brot Bus Dach Dackel Delfin Dino Ei Elefant Ente Esel Eule Eis " +
        "Fahrrad Fisch Flugzeug Frosch Fuchs Gabel Giraffe Gans Hase Haus Hund Hut Igel Insel Jacke Kaenguru Katze Kuh Kerze Krokodil Lampe Loewe " +
        "Mond Maus Mama Nase Nudel Nest Ofen Orange Otter Papa Pilz Pinguin Quelle Rakete Regen Robbe Rose Sonne Schaf Schiff Schnee Stern Tiger " +
        "Tisch Torte Tuer Uhr Ufer Vogel Wal Wolke Wurm Wiese Zebra Zug Zwerg Ziege Ente Esche Eisen Nacht Tanne Elch Rentier Eisbaer Lappland " +
        "Ratte Eimer Raupe Elster Tulpe Eiche Ei").split(' ').distinct()
    val en = ("apple ant airplane ball banana bear bee bird boat bus cat chair cow crab dog dolphin donkey dragon duck eagle egg elephant " +
        "fish flower fox frog giraffe goat hat horse house igloo island jelly kangaroo kite lamp lion monkey moon mouse nest nut orange owl " +
        "panda pear penguin queen rabbit rain robot rocket sheep snow star sun tiger train tree turtle umbrella van whale wolf worm yak zebra " +
        "tent tractor rabbit reindeer elk river rose ear ice").split(' ').distinct()
    fun of(lang: Lang) = if (lang == Lang.DE) de else en

    /** Reimgruppen (Woerter, die sich reimen), je Sprache. */
    val rhymesDe = listOf(
        listOf("Haus", "Maus", "Laus", "raus"), listOf("Hund", "Mund", "rund", "bunt"), listOf("Hase", "Nase", "Vase", "Glase"),
        listOf("Katze", "Tatze", "Glatze", "Fratze"), listOf("Baum", "Traum", "Schaum", "Raum"), listOf("Hose", "Rose", "Dose", "Nase"),
        listOf("Tisch", "Fisch", "frisch", "Gemisch"), listOf("Schuh", "Kuh", "Ruh", "zu"), listOf("Eis", "heiss", "weiss", "Preis"),
        listOf("Bein", "Stein", "klein", "Wein"), listOf("Sonne", "Tonne", "Wonne", "Gonne"), listOf("Hand", "Sand", "Land", "Wand"),
    )
    val rhymesEn = listOf(
        listOf("cat", "hat", "bat", "mat"), listOf("dog", "log", "frog", "fog"), listOf("sun", "run", "fun", "bun"),
        listOf("star", "car", "far", "jar"), listOf("tree", "bee", "key", "sea"), listOf("moon", "spoon", "balloon", "tune"),
        listOf("house", "mouse", "blouse", "grouse"), listOf("fish", "dish", "wish", "swish"), listOf("bear", "chair", "hair", "pair"),
        listOf("boat", "coat", "goat", "float"), listOf("snow", "go", "show", "glow"), listOf("ring", "king", "sing", "wing"),
    )
    fun rhymes(lang: Lang) = if (lang == Lang.DE) rhymesDe else rhymesEn
}

/** Ergebnis eines Spielzugs. Kilo schimpft nie: bei Fehlern gibt es einen Hinweis und eine neue Chance. */
sealed interface TurnResult {
    data class Accepted(val word: String, val kiloWord: String?, val nextLetter: Char?) : TurnResult
    /** Kilo findet kein Wort mehr: das Kind hat "gewonnen". */
    data class KiloConcedes(val word: String) : TurnResult
    data class Rejected(val reason: Reason) : TurnResult
    enum class Reason { NOT_HEARD, WRONG_LETTER, ALREADY_USED }
}

private fun norm(s: String) = AnswerMatcher.normalize(s)
private fun tokensOf(heard: List<String>) = heard.flatMap { norm(it).split(' ') }.filter { it.isNotEmpty() }

/** Wortkette: das naechste Wort beginnt mit dem letzten Buchstaben des vorigen. */
class WordChain(private val lang: Lang, seed: Long) {
    private val rng = Random(seed)
    private val words = WordLists.of(lang)
    private val used = HashSet<String>()
    var last: String? = null; private set
    var accepted = 0; private set

    /** Buchstabe, mit dem das naechste Wort beginnen muss (null vor dem Start). */
    val requiredLetter: Char? get() = last?.let { norm(it).lastOrNull() }

    private fun friendly(w: String): Boolean {
        val c = norm(w).lastOrNull() ?: return false
        return words.count { norm(it).startsWith(c) && norm(it) !in used && norm(it) != norm(w) } >= 2
    }

    /** Kilos erstes Wort. */
    fun start(): String {
        val w = words.filter { friendly(it) }.random(rng)
        used += norm(w); last = w
        return w
    }

    /** Ein Wort, das jetzt passen wuerde (Hinweis fuer das Kind). */
    fun hint(): String? = requiredLetter?.let { c -> words.filter { norm(it).startsWith(c) && norm(it) !in used }.randomOrNull(rng) }

    fun play(heard: List<String>): TurnResult {
        val need = requiredLetter ?: return TurnResult.Rejected(TurnResult.Reason.NOT_HEARD)
        val toks = tokensOf(heard)
        if (toks.isEmpty()) return TurnResult.Rejected(TurnResult.Reason.NOT_HEARD)
        val fit = toks.filter { it.length >= 2 && it[0] == need }
        if (fit.isEmpty()) return TurnResult.Rejected(TurnResult.Reason.WRONG_LETTER)
        val word = fit.firstOrNull { it !in used } ?: return TurnResult.Rejected(TurnResult.Reason.ALREADY_USED)
        used += word; accepted++
        val endLetter = word.last()
        val pool = words.filter { norm(it).startsWith(endLetter) && norm(it) !in used }
        // Endet das Wort auf einen seltenen Buchstaben, gibt Kilo auf (das Kind hat gewonnen)
        val reply = pool.filter { friendly(it) }.randomOrNull(rng)
        if (reply == null) { last = word; return TurnResult.KiloConcedes(word) }
        used += norm(reply); last = reply
        return TurnResult.Accepted(word, reply, reply.let { norm(it).last() })
    }
}

/** Kategorie plus Anfangsbuchstabe ("Ein Tier mit M"). Jedes Wort mit passendem Anfangsbuchstaben zaehlt, jedes Wort nur einmal. */
class LetterGame(private val lang: Lang, seed: Long) {
    private val rng = Random(seed)
    private val used = HashSet<String>()
    private val letters = if (lang == Lang.DE) "ABDEFGHKLMNPRSTWZ".toList() else "ABCDFGHLMPRST".toList()
    data class Round(val categoryKey: String, val letter: Char)
    var round: Round? = null; private set

    fun next(): Round = Round(CATEGORIES.random(rng), letters.random(rng)).also { round = it }

    fun answer(heard: List<String>): TurnResult {
        val r = round ?: return TurnResult.Rejected(TurnResult.Reason.NOT_HEARD)
        val toks = tokensOf(heard)
        if (toks.isEmpty()) return TurnResult.Rejected(TurnResult.Reason.NOT_HEARD)
        val fit = toks.filter { it.length >= 2 && it[0] == r.letter.lowercaseChar() }
        if (fit.isEmpty()) return TurnResult.Rejected(TurnResult.Reason.WRONG_LETTER)
        val w = fit.firstOrNull { it !in used } ?: return TurnResult.Rejected(TurnResult.Reason.ALREADY_USED)
        used += w
        return TurnResult.Accepted(w, null, null)
    }

    companion object { val CATEGORIES = listOf("animal", "food", "thing", "place") }
}

/** Reimspiel (ab 4 Jahren): Kilo nennt ein Wort, das Kind findet ein Wort, das sich reimt. */
class RhymeGame(private val lang: Lang, seed: Long) {
    private val rng = Random(seed)
    private val groups = WordLists.rhymes(lang)
    private var order = groups.shuffled(rng)
    private var idx = 0
    var current: List<String> = emptyList(); private set
    val base: String? get() = current.firstOrNull()

    fun next(): String { current = order[idx++ % order.size].shuffled(rng); return current.first() }

    /** Ein Reimwort als Hinweis (nie das Ausgangswort). */
    fun hint(): String? = current.drop(1).firstOrNull()

    fun check(heard: List<String>): Boolean {
        val b = base?.let { norm(it) } ?: return false
        val group = current.map { norm(it) }.toSet()
        return tokensOf(heard).any { t ->
            t != b && t.length >= 2 && (t in group || (t.length >= 3 && b.length >= 3 && t.takeLast(3) == b.takeLast(3)) ||
                (t.length >= 3 && b.length >= 3 && t.takeLast(2) == b.takeLast(2) && t.last() in "aeiou".toSet()))
        }
    }
}

/** Geschichte weiterspinnen: Kilo beginnt, alle fuegen reihum einen Satz an, Kilo beendet. Rein aus Textschluesseln, deterministisch per Seed. */
class StoryRelay(seed: Long, private val rounds: Int = 4) {
    private val rng = Random(seed)
    val steps: List<String> = buildList {
        add("audio_story_start_" + (1..STARTS).random(rng))
        val nexts = (1..NEXTS).shuffled(rng)
        for (i in 0 until rounds) add("audio_story_next_" + nexts[i % nexts.size])
        add("audio_story_end")
    }
    companion object { const val STARTS = 4; const val NEXTS = 4 }
}
