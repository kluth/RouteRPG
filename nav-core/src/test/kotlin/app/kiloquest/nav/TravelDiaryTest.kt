package app.kiloquest.nav

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TravelDiaryTest {
    private fun e(id: String, at: Long, kind: DiaryKind, title: String = id, day: Int = 100, country: String? = null, points: Int = 0, meters: Double = 0.0, text: String = "") =
        DiaryEntry(id, at, day, kind.name, title, text, country, points, meters)

    @Test fun addKeepsChronologicalOrderAndIgnoresSameId() {
        var l = emptyList<DiaryEntry>()
        l = TravelDiary.add(l, e("b", 200, DiaryKind.QUEST))
        l = TravelDiary.add(l, e("a", 100, DiaryKind.QUEST))
        l = TravelDiary.add(l, e("a", 100, DiaryKind.QUEST))
        assertEquals(listOf("a", "b"), l.map { it.id })
    }

    @Test fun sameStoryOnlyOnce() {
        var l = TravelDiary.add(emptyList(), e("1", 1, DiaryKind.STORY, "Dom"))
        l = TravelDiary.add(l, e("2", 2, DiaryKind.STORY, "Dom", day = 101))
        l = TravelDiary.add(l, e("3", 3, DiaryKind.STORY, "Burg"))
        assertEquals(listOf("Dom", "Burg"), l.map { it.title })
    }

    @Test fun sameCountryNotRepeatedOnSameDayButAllowedLater() {
        var l = TravelDiary.add(emptyList(), e("1", 1, DiaryKind.COUNTRY, country = "DK"))
        l = TravelDiary.add(l, e("2", 2, DiaryKind.COUNTRY, country = "DK"))
        assertEquals(1, l.size)
        l = TravelDiary.add(l, e("3", 3, DiaryKind.COUNTRY, country = "SE"))
        l = TravelDiary.add(l, e("4", 4, DiaryKind.COUNTRY, country = "DK")) // zurueck nach DK: neuer Eintrag
        assertEquals(listOf("DK", "SE", "DK"), l.map { it.country })
        l = TravelDiary.add(l, e("5", 5, DiaryKind.COUNTRY, country = "DK", day = 101)) // anderer Tag, gleiches Land
        assertEquals(4, l.size)
    }

    @Test fun capDropsOldestEntries() {
        var l = emptyList<DiaryEntry>()
        for (i in 1..(TravelDiary.MAX_ENTRIES + 5)) l = TravelDiary.add(l, e("q$i", i.toLong(), DiaryKind.QUEST))
        assertEquals(TravelDiary.MAX_ENTRIES, l.size)
        assertEquals("q6", l.first().id)
    }

    @Test fun byDayGroupsAndSorts() {
        val l = listOf(e("c", 300, DiaryKind.QUEST, day = 102), e("a", 100, DiaryKind.QUEST, day = 100), e("b", 200, DiaryKind.QUEST, day = 100))
        val g = TravelDiary.byDay(l)
        assertEquals(listOf(100, 102), g.map { it.first })
        assertEquals(listOf("a", "b"), g[0].second.map { it.id })
    }

    @Test fun statsCountEverything() {
        val l = listOf(
            e("t", 1, DiaryKind.TRIP_START), e("c1", 2, DiaryKind.COUNTRY, country = "DE"), e("c2", 3, DiaryKind.COUNTRY, country = "DK", day = 101),
            e("s", 4, DiaryKind.STORY, "Dom"), e("q1", 5, DiaryKind.QUEST, points = 10), e("q2", 6, DiaryKind.QUEST, points = 15),
            e("st", 7, DiaryKind.STICKER), e("a", 8, DiaryKind.ARRIVAL, meters = 123_000.0, day = 101),
        )
        val s = TravelDiary.stats(l)
        assertEquals(2, s.days)
        assertEquals(1, s.trips)
        assertEquals(listOf("DE", "DK"), s.countries)
        assertEquals(1, s.stories)
        assertEquals(2, s.quests)
        assertEquals(25, s.points)
        assertEquals(1, s.stickers)
        assertEquals(123_000.0, s.meters)
    }

    @Test fun roundTripAndBadInput() {
        val l = listOf(e("a", 1, DiaryKind.STORY, "Dom", text = "Ein Dom."), e("b", 2, DiaryKind.ARRIVAL, meters = 5.0))
        assertEquals(l, TravelDiary.decode(TravelDiary.encode(l)))
        assertEquals(emptyList(), TravelDiary.decode("kaputt{"))
        assertEquals(emptyList(), TravelDiary.decode(null))
    }

    @Test fun unknownKindIsKeptButNotCounted() {
        val odd = DiaryEntry("x", 1, 1, "UFO", "?")
        assertEquals(null, odd.kindOrNull)
        assertEquals(0, TravelDiary.stats(listOf(odd)).quests)
        assertEquals(1, TravelDiary.add(emptyList(), odd).size)
    }

    @Test fun flagEmoji() {
        assertEquals("🇩🇪", TravelDiary.flag("de"))
        assertEquals("", TravelDiary.flag("XYZ"))
        assertEquals("", TravelDiary.flag(null))
        assertTrue(TravelDiary.flag("SE").isNotEmpty())
    }

    @Test fun removeDropsOnlyThatEntry() {
        val l = listOf(e("a", 100, DiaryKind.QUEST), e("b", 200, DiaryKind.QUEST))
        assertEquals(listOf("b"), TravelDiary.remove(l, "a").map { it.id })
        assertEquals(l, TravelDiary.remove(l, "x"))
    }
}
