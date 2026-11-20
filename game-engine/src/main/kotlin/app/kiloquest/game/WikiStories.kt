package app.kiloquest.game

import java.net.URLEncoder
import kotlin.random.Random
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

data class Story(val title: String, val text: String)

/**
 * Kurze Geschichten zu Orten in der Naehe aus Wikipedia (Geosearch + Intro-Auszug).
 * Inhalte stehen unter CC BY-SA; die App zeigt die Quelle an. Kein Netzwerkcode hier.
 */
object WikiStories {
    const val PENDING_KEY = "story_pending"
    const val TEXT_KEY = "story_text"
    const val SOURCE_KEY = "story_source"

    val textKeys: List<String> = listOf(PENDING_KEY, TEXT_KEY, SOURCE_KEY)

    private val json = Json { ignoreUnknownKeys = true }

    private fun maxChars(age: AgeBand) = when (age) {
        AgeBand.MINI -> 170
        AgeBand.EXPLORER -> 280
        AgeBand.PRO -> 450
    }

    private fun sentences(age: AgeBand) = when (age) {
        AgeBand.MINI -> 1
        AgeBand.EXPLORER -> 2
        AgeBand.PRO -> 3
    }

    /** Sprache muss ein Wikipedia-Sprachcode sein (2-3 Buchstaben), sonst Englisch. */
    fun safeLang(lang: String): String = if (Regex("^[a-z]{2,3}$").matches(lang)) lang else "en"

    fun buildUrl(lang: String, lat: Double, lon: Double, age: AgeBand, radiusMeters: Int = 8000, limit: Int = 12): String {
        fun e(s: String) = URLEncoder.encode(s, "UTF-8")
        val params = listOf(
            "action" to "query", "generator" to "geosearch",
            "ggscoord" to "$lat|$lon", "ggsradius" to radiusMeters.toString(), "ggslimit" to limit.toString(),
            "prop" to "extracts", "exintro" to "1", "explaintext" to "1",
            "exsentences" to sentences(age).toString(), "exlimit" to limit.toString(),
            "format" to "json", "formatversion" to "2",
        )
        return "https://${safeLang(lang)}.wikipedia.org/w/api.php?" + params.joinToString("&") { (k, v) -> "$k=${e(v)}" }
    }

    fun parse(body: String, age: AgeBand): List<Story> {
        val pages = json.decodeFromString<Response>(body).query?.pages.orEmpty()
        return pages.mapNotNull { p ->
            val text = clean(p.extract.orEmpty(), maxChars(age))
            if (p.title.isBlank() || text.length < 40) null else Story(p.title, text)
        }
    }

    /** Entfernt Klammern (Aussprache, Lebensdaten), Zeilenumbrueche; kuerzt an Satzgrenzen. */
    fun clean(raw: String, maxChars: Int): String {
        var t = raw.replace(Regex("\\s*\\([^)]{0,120}\\)"), "").replace(Regex("\\s+"), " ").trim()
        if (t.length <= maxChars) return t
        val cut = t.substring(0, maxChars)
        val end = cut.lastIndexOfAny(charArrayOf('.', '!', '?'))
        t = if (end >= maxChars / 3) cut.substring(0, end + 1)
        else cut.substring(0, cut.lastIndexOf(' ').takeIf { it > 0 } ?: maxChars).trimEnd(',', ';', ':') + "…"
        return t
    }

    // Mehrsprachige Hinweise auf "kindertaugliche" Orte (Natur, Bauwerke, Tiere)
    private val interesting = Regex(
        "burg|schloss|berg|see\\b|fluss|bach|brücke|insel|wald|zoo|turm|leuchtturm|mühle|höhle|moor|strand|hafen|" +
            "castle|mountain|lake|river|bridge|island|forest|tower|lighthouse|mill|cave|beach|harbou?r|" +
            "slott|berg|sjö|älv|bro|ö\\b|skog|torn|fyr|kvarn|grotta|strand|hamn|" +
            "kirke|slot|sø|å\\b|bro|ø\\b|skov|tårn|fyrtårn|mølle|strand|havn",
        RegexOption.IGNORE_CASE,
    )

    /** Bevorzugt Orte, die Kinder vom Auto aus sehen koennen; sonst irgendeinen. */
    fun pick(stories: List<Story>, random: Random): Story? {
        if (stories.isEmpty()) return null
        val good = stories.filter { interesting.containsMatchIn(it.title) || interesting.containsMatchIn(it.text) }
        val pool = good.ifEmpty { stories }
        return pool[random.nextInt(pool.size)]
    }

    fun resolve(placeholder: Quest, story: Story?): Quest =
        if (story != null) {
            placeholder.copy(type = QuestType.STORY, textKey = TEXT_KEY, args = listOf(story.title, story.text))
        } else {
            placeholder.copy(type = QuestType.STORY, textKey = RouteFacts.FALLBACK_KEY, args = emptyList())
        }

    @Serializable private data class Response(val query: Query? = null)
    @Serializable private data class Query(val pages: List<Page> = emptyList())
    @Serializable private data class Page(val title: String = "", val extract: String? = null)
}
