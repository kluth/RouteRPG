package app.kiloquest.nav

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PhotoStoryTest {
    private val min = 60_000L

    private fun prompter() = PhotoPrompter(seed = 1, warmupMs = 5 * min, minGapMs = 30 * min, scenicGapMs = 15 * min, randomMinMs = 45 * min, randomMaxMs = 90 * min, maxPerDay = 3, snoozeMs = 20 * min)

    @Test fun nothingBeforeStartOrDuringWarmup() {
        val p = prompter()
        assertNull(p.onTick(0, 100, 5, "Aussicht", false))
        p.start(0)
        assertNull(p.onTick(4 * min, 100, 5, "Aussicht", false)) // Aufwaermzeit
        assertNotNull(p.onTick(6 * min, 100, 5, "Aussicht", false))
    }

    @Test fun scenicBeatsRandomAndReportsPlace() {
        val p = prompter(); p.start(0)
        val a = p.onTick(10 * min, 100, 4, "Kullen", false)
        assertEquals(PhotoReason.SCENIC, a?.reason)
        assertEquals("Kullen", a?.place)
    }

    @Test fun weakScenicScoreIsIgnoredAndRandomComesLater() {
        val p = prompter(); p.start(0)
        assertNull(p.onTick(10 * min, 100, 2, "x", false))
        assertNull(p.onTick(44 * min, 100, 0, null, false))
        // spaetestens nach Aufwaermzeit + 90 min kommt die Zufalls-Bitte
        var at = -1L
        for (t in 45..120) if (p.onTick(t * min, 100, 0, null, false) != null) { at = t * min; break }
        assertTrue(at in (50 * min)..(95 * min), "Zufallsbitte bei $at")
    }

    @Test fun minimumGapsAreRespected() {
        val p = prompter(); p.start(0)
        assertNotNull(p.onTick(10 * min, 100, 5, null, false))
        assertNull(p.onTick(20 * min, 100, 5, null, false))   // erst 10 min nach der letzten
        assertNotNull(p.onTick(26 * min, 100, 5, null, false)) // 16 min >= Scenic-Abstand
    }

    @Test fun busyBlocksWithoutConsuming() {
        val p = prompter(); p.start(0)
        assertNull(p.onTick(10 * min, 100, 5, null, busy = true))
        assertNotNull(p.onTick(11 * min, 100, 5, null, busy = false))
    }

    @Test fun dailyLimitAndNewDayReset() {
        val p = prompter(); p.start(0)
        var t = 10 * min
        repeat(3) { assertNotNull(p.onTick(t, 100, 5, null, false)); t += 20 * min }
        assertNull(p.onTick(t, 100, 5, null, false))         // Tageslimit 3
        assertNotNull(p.onTick(t, 101, 5, null, false))      // neuer Tag
    }

    @Test fun laterSnoozesTakenDoesNot() {
        val p = prompter(); p.start(0)
        assertNotNull(p.onTick(10 * min, 100, 5, null, false))
        p.onAnswered(10 * min, taken = false)
        assertNull(p.onTick(29 * min, 100, 5, null, false))    // 19 min < 20 min Pause
        assertNotNull(p.onTick(31 * min, 100, 5, null, false))
        p.onAnswered(31 * min, taken = true)
        assertNotNull(p.onTick(47 * min, 100, 5, null, false)) // keine Pause nach Foto
    }

    @Test fun manualAlwaysWorksAndCounts() {
        val p = prompter(); p.start(0)
        assertEquals(PhotoReason.MANUAL, p.manual(1 * min, 100).reason)
        p.manual(2 * min, 100); p.manual(3 * min, 100)
        assertNull(p.onTick(40 * min, 100, 5, null, false)) // Limit erreicht
    }

    @Test fun deterministicWithSeed() {
        fun firstRandom(seed: Long): Long {
            val p = PhotoPrompter(seed = seed); p.start(0)
            for (t in 0..300) if (p.onTick(t * min, 1, 0, null, false) != null) return t * min
            return -1
        }
        assertEquals(firstRandom(7), firstRandom(7))
    }

    // ---- Schoene Gegend
    private fun body(vararg tags: String) = """{"elements":[${tags.joinToString(",") { """{"type":"node","lat":1.0,"lon":1.0,"tags":{$it}}""" }}]}"""

    @Test fun viewpointIsScenic() {
        val s = ScenicFinder.assess(body("\"tourism\":\"viewpoint\",\"name\":\"Kullaberg\""))
        assertEquals(3, s.score)
        assertEquals("Kullaberg", s.name)
    }

    @Test fun anonymousPondIsNotScenicButNamedLakeIsMore() {
        assertEquals(1, ScenicFinder.assess(body("\"natural\":\"water\"")).score)
        assertEquals(2, ScenicFinder.assess(body("\"natural\":\"water\",\"name\":\"Mjøsa\"")).score)
    }

    @Test fun manySpotsAddABonusCappedAtFive() {
        val s = ScenicFinder.assess(body("\"natural\":\"peak\"", "\"natural\":\"peak\"", "\"waterway\":\"waterfall\"", "\"natural\":\"glacier\"", "\"tourism\":\"viewpoint\""))
        assertEquals(4, s.score)
        assertEquals(Scenic.NONE, ScenicFinder.assess(body("\"highway\":\"residential\"")))
    }

    @Test fun badResponsesAreNone() {
        assertEquals(Scenic.NONE, ScenicFinder.assess("kaputt"))
        assertEquals(Scenic.NONE, ScenicFinder.assess("""{"elements":[]}"""))
    }

    @Test fun queryContainsRadiusAndPosition() {
        val q = ScenicFinder.buildQuery(60.5, 24.9, 1200)
        assertTrue("around:1200,60.50000,24.90000" in q && "viewpoint" in q)
    }

    // ---- Collage
    @Test fun collagePagesSplitRows() {
        assertEquals(emptyList(), CollageLayout.pages(0))
        assertEquals(listOf(0 until 5, 5 until 7), CollageLayout.pages(7))
    }

    @Test fun collageLayoutFitsWithoutOverlap() {
        val l = CollageLayout.layout(3, 1080, headerHeight = 160, gap = 12)
        assertEquals(3, l.rows.size)
        for (r in l.rows) {
            assertTrue(r.back.x + r.back.w + 12 == r.front.x)
            assertTrue(r.front.x + r.front.w + 12 <= 1080 + 12)
            assertEquals(r.back.w, r.front.w)
            assertEquals(r.back.h, r.front.h)
        }
        assertTrue(l.rows[1].back.y >= l.rows[0].back.y + l.rows[0].back.h + 12)
        assertEquals(l.rows.last().back.y + l.rows.last().back.h + 12, l.height)
    }

    @Test fun photoEntryFilesAreSafeNames() {
        val e = DiaryEntry("1", 1, 1, DiaryKind.PHOTO.name, "Foto", "a-back.jpg|a-front.jpg|../x.jpg|dir/y.jpg")
        assertEquals(listOf("a-back.jpg", "a-front.jpg"), e.photoFiles())
        assertEquals(emptyList(), DiaryEntry("2", 1, 1, DiaryKind.QUEST.name, "q", "a.jpg").photoFiles())
        assertEquals(1, TravelDiary.stats(listOf(e)).photos)
    }
}
