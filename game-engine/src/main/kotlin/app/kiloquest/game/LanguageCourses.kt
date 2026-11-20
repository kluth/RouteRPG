package app.kiloquest.game

import kotlin.random.Random

/** Die Bedeutungen; die uebersetzten Bezeichnungen stehen in den String-Ressourcen unter `phrase_<id>`. */
enum class Phrase(val id: String) {
    HELLO("hello"), THANKS("thanks"), PLEASE("please"), GOODBYE("goodbye"),
    YES("yes"), NO("no"), WATER("water"), ICE_CREAM("icecream");

    val textKey: String get() = "phrase_$id"
}

/**
 * Mini-Sprachkurs eines Landes: wenige Alltagswoerter in der Landessprache (Daten, nicht uebersetzt).
 * [country] = ISO-3166-Laendercode (z. B. "SE"), [language] = ISO-639-Sprachcode fuer die Sprachausgabe.
 */
data class LanguageCourse(val country: String, val language: String, val words: Map<Phrase, String>)

/** Eine Quizfrage: Bedeutung erfragen, Antwort aus drei Woertern. */
data class PhraseQuiz(val phrase: Phrase, val options: List<String>, val correctIndex: Int)

object LanguageCourses {
    const val PENDING_KEY = "lang_pending"
    const val QUESTION_KEY = "lang_question"
    const val WELCOME_KEY = "lang_welcome"
    val textKeys: List<String> = listOf(PENDING_KEY, QUESTION_KEY, WELCOME_KEY) + Phrase.entries.map { it.textKey }

    private fun c(country: String, lang: String, vararg w: String) =
        LanguageCourse(country, lang, Phrase.entries.zip(w.toList()).toMap())

    // Reihenfolge: hello, thanks, please, goodbye, yes, no, water, icecream
    val all: List<LanguageCourse> = listOf(
        c("SE", "sv", "Hej", "Tack", "Snälla", "Hej då", "Ja", "Nej", "Vatten", "Glass"),
        c("DK", "da", "Hej", "Tak", "Vær så venlig", "Farvel", "Ja", "Nej", "Vand", "Is"),
        c("FI", "fi", "Hei", "Kiitos", "Ole hyvä", "Näkemiin", "Kyllä", "Ei", "Vesi", "Jäätelö"),
        c("NO", "no", "Hei", "Takk", "Vær så snill", "Ha det", "Ja", "Nei", "Vann", "Is"),
        c("NL", "nl", "Hallo", "Dank je", "Alstublieft", "Tot ziens", "Ja", "Nee", "Water", "IJs"),
        c("PL", "pl", "Cześć", "Dziękuję", "Proszę", "Do widzenia", "Tak", "Nie", "Woda", "Lody"),
    )

    fun forCountry(code: String?): LanguageCourse? = all.firstOrNull { it.country.equals(code, ignoreCase = true) }

    /**
     * Quizfrage zu einer noch nicht gelernten Phrase ([taught] ausgeschlossen); wenn alle gelernt sind, beliebig.
     * Falsche Antworten sind andere Woerter derselben Sprache (verschieden von der richtigen).
     */
    fun quiz(course: LanguageCourse, taught: Set<Phrase>, random: Random): PhraseQuiz {
        val pool = Phrase.entries.filter { it !in taught }.ifEmpty { Phrase.entries }
        val phrase = pool[random.nextInt(pool.size)]
        val correct = course.words.getValue(phrase)
        val wrong = course.words.filterKeys { it != phrase }.values.filter { it != correct }.distinct().shuffled(random).take(2)
        val options = (wrong + correct).shuffled(random)
        return PhraseQuiz(phrase, options, options.indexOf(correct))
    }

    /** Die beiden Willkommensphrasen beim Grenzuebertritt. */
    val welcomePhrases: List<Phrase> = listOf(Phrase.HELLO, Phrase.THANKS)
}
