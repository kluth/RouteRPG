package app.kiloquest.nav

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

enum class DiaryKind { TRIP_START, COUNTRY, STORY, QUEST, STICKER, ARRIVAL }

/**
 * Ein Eintrag im Reisetagebuch. Alles bleibt lokal auf dem Geraet. [day] = Epochentag, [kind] = [DiaryKind]-Name.
 * [text] ist bei Geschichten der Wikipedia-Auszug (zum erneuten Anhoeren), sonst optional.
 */
@Serializable data class DiaryEntry(
    val id: String,
    val atMs: Long,
    val day: Int,
    val kind: String,
    val title: String,
    val text: String = "",
    val country: String? = null,
    val points: Int = 0,
    val meters: Double = 0.0,
    val lat: Double? = null,
    val lon: Double? = null,
) {
    val kindOrNull: DiaryKind? get() = runCatching { DiaryKind.valueOf(kind) }.getOrNull()
}

data class DiaryStats(
    val days: Int,
    val trips: Int,
    /** Besuchte Laender (ISO-Codes) in Reihenfolge des ersten Besuchs. */
    val countries: List<String>,
    val stories: Int,
    val quests: Int,
    val points: Int,
    val stickers: Int,
    /** Gefahrene Strecke (Summe der Ankuenfte). */
    val meters: Double,
)

/** Reisetagebuch: Eintraege hinzufuegen (ohne Dubletten, mit Obergrenze), gruppieren, zaehlen, speichern. Reine Logik. */
object TravelDiary {
    const val MAX_ENTRIES = 1500
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    fun encode(entries: List<DiaryEntry>): String = json.encodeToString(entries)

    /** Unlesbare Daten ergeben eine leere Liste, nie einen Absturz. */
    fun decode(text: String?): List<DiaryEntry> =
        if (text.isNullOrBlank()) emptyList() else runCatching { json.decodeFromString<List<DiaryEntry>>(text) }.getOrDefault(emptyList())

    /**
     * Fuegt [e] hinzu. Dubletten: dieselbe Geschichte nur einmal; dasselbe Land nicht zweimal hintereinander am selben Tag;
     * dieselbe Id nie doppelt. Bei mehr als [MAX_ENTRIES] fallen die aeltesten weg.
     */
    fun add(list: List<DiaryEntry>, e: DiaryEntry): List<DiaryEntry> {
        if (list.any { it.id == e.id }) return list
        when (e.kindOrNull) {
            DiaryKind.STORY -> if (list.any { it.kind == e.kind && it.title == e.title }) return list
            DiaryKind.COUNTRY -> {
                val last = list.lastOrNull { it.kind == e.kind }
                if (last != null && last.country == e.country && last.day == e.day) return list
            }
            else -> Unit
        }
        val next = (list + e).sortedBy { it.atMs }
        return if (next.size > MAX_ENTRIES) next.takeLast(MAX_ENTRIES) else next
    }

    /** Eintraege je Tag, Tage aufsteigend, Eintraege chronologisch. */
    fun byDay(list: List<DiaryEntry>): List<Pair<Int, List<DiaryEntry>>> =
        list.sortedBy { it.atMs }.groupBy { it.day }.toList().sortedBy { it.first }

    fun stats(list: List<DiaryEntry>): DiaryStats {
        val sorted = list.sortedBy { it.atMs }
        return DiaryStats(
            days = sorted.map { it.day }.distinct().size,
            trips = sorted.count { it.kind == DiaryKind.TRIP_START.name },
            countries = sorted.filter { it.kind == DiaryKind.COUNTRY.name }.mapNotNull { it.country }.distinct(),
            stories = sorted.count { it.kind == DiaryKind.STORY.name },
            quests = sorted.count { it.kind == DiaryKind.QUEST.name },
            points = sorted.sumOf { it.points },
            stickers = sorted.count { it.kind == DiaryKind.STICKER.name },
            meters = sorted.filter { it.kind == DiaryKind.ARRIVAL.name }.sumOf { it.meters },
        )
    }

    /** Flaggen-Emoji zu einem ISO-3166-Code; bei ungueltigem Code leer. */
    fun flag(country: String?): String {
        val c = country?.trim()?.uppercase().orEmpty()
        if (c.length != 2 || c.any { it !in 'A'..'Z' }) return ""
        return c.map { String(Character.toChars(0x1F1E6 + (it - 'A'))) }.joinToString("")
    }
}
