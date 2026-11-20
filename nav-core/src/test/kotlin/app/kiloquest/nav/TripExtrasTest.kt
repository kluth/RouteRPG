package app.kiloquest.nav

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TripExtrasTest {
    private fun trip(id: String = "t", start: Int = 100, stays: List<PlanStay> = emptyList()) =
        PlannedTrip(id, "Lappland", start, PlanPlace("Duisburg", 51.4, 6.7), PlanPlace("Rovaniemi", 66.5, 25.7), stays)

    @Test fun milestonesFireOnce() {
        val t = trip()
        assertEquals(ReminderKind.COUNTDOWN, TripReminders.due(listOf(t), 70, emptySet()).single().kind) // 30 Tage
        assertTrue(TripReminders.due(listOf(t), 70, setOf("t:m:30")).isEmpty())
        assertEquals(ReminderKind.DEPARTURE_EVE, TripReminders.due(listOf(t), 99, emptySet()).single().kind)
        assertEquals(ReminderKind.DEPARTURE_DAY, TripReminders.due(listOf(t), 100, emptySet()).single().kind)
    }

    @Test fun missedMilestonesGiveOnlyTheNearestOne() {
        val r = TripReminders.due(listOf(trip()), 92, emptySet()) // 8 Tage -> naechster Meilenstein 7? nein: kleinstes m >= 8 ist 14
        assertEquals(1, r.size)
        assertEquals("t:m:14", r.single().key)
    }

    @Test fun nothingFarAheadOrInThePast() {
        assertTrue(TripReminders.due(listOf(trip()), 10, emptySet()).isEmpty())
        assertTrue(TripReminders.due(listOf(trip()), 101, emptySet()).isEmpty())
    }

    @Test fun checkInReminders() {
        val t = trip(start = 10, stays = listOf(PlanStay("Hotel", 1.0, 1.0, 105, 2)))
        val today = TripReminders.due(listOf(t), 105, emptySet())
        assertEquals(ReminderKind.CHECK_IN_TODAY, today.single().kind)
        assertEquals("Hotel", today.single().stayName)
        assertEquals(ReminderKind.CHECK_IN_TOMORROW, TripReminders.due(listOf(t), 104, emptySet()).single().kind)
    }

    @Test fun pruneRemovesDeletedTrips() {
        val kept = TripReminders.prune(setOf("a:m:7", "gone:m:7"), listOf(trip(id = "a")))
        assertEquals(setOf("a:m:7"), kept)
    }

    @Test fun nextTripIsEarliestNotPast() {
        val list = listOf(trip("a", 50), trip("b", 120), trip("c", 110))
        assertEquals("c", TripReminders.next(list, 100)?.id)
        assertEquals(null, TripReminders.next(list, 200))
    }

    @Test fun packingListForLaplandWinterWithSmallKids() {
        val items = PackingList.build(PackContext(66.5, 1, 6, 4, smallKids = true, camping = false))
        assertTrue(PackItem.WINTER_GEAR in items && PackItem.WARM_LAYERS in items && PackItem.DIAPERS in items)
        assertFalse(PackItem.SWIMWEAR in items)
        assertFalse(PackItem.TENT_GEAR in items)
        assertEquals(items.distinct(), items)
    }

    @Test fun packingListSummerCamping() {
        val items = PackingList.build(PackContext(43.0, 7, 10, 2, smallKids = false, camping = true))
        assertTrue(PackItem.SWIMWEAR in items && PackItem.SUN_PROTECTION in items && PackItem.TENT_GEAR in items)
        assertFalse(PackItem.WINTER_GEAR in items)
        assertFalse(PackItem.DIAPERS in items)
    }

    @Test fun packingProgress() {
        val items = listOf(PackItem.ID_PAPERS, PackItem.BOOKINGS)
        assertEquals(0.5, PackingList.progress(items, listOf("ID_PAPERS", "UNBEKANNT")))
        assertEquals(0.0, PackingList.progress(emptyList(), emptyList()))
    }

    @Test fun icsHasEventsEscapingAndFolding() {
        val t = trip(start = 20000, stays = listOf(PlanStay("Hotel, Nord; Süd", 1.0, 1.0, 20003, 2, "Buchung 123\nFrühstück")))
        val ics = IcsExport.build(t, 20010, "20260101T000000Z")
        assertTrue(ics.startsWith("BEGIN:VCALENDAR\r\n") && ics.endsWith("END:VCALENDAR\r\n"))
        assertEquals(3, Regex("BEGIN:VEVENT").findAll(ics).count())
        assertTrue("SUMMARY:Hotel\\, Nord\\; Süd" in ics)
        assertTrue("DESCRIPTION:Buchung 123\\nFrühstück" in ics)
        val start = java.time.LocalDate.ofEpochDay(20003).toString().replace("-", "")
        val end = java.time.LocalDate.ofEpochDay(20005).toString().replace("-", "")
        assertTrue("DTSTART;VALUE=DATE:$start" in ics && "DTEND;VALUE=DATE:$end" in ics)
        ics.split("\r\n").forEach { assertTrue(it.toByteArray().size <= 75, it) }
    }

    @Test fun icsFoldsLongLinesWithoutBreakingUtf8() {
        val long = IcsExport.fold("SUMMARY:" + "ä".repeat(60))
        long.split("\r\n").forEach { assertTrue(it.toByteArray().size <= 75) }
        assertEquals("SUMMARY:" + "ä".repeat(60), long.replace("\r\n ", ""))
    }

    @Test fun returnTripSwapsPlaces() {
        val r = TripOps.returnTrip(trip(stays = listOf(PlanStay("H", 1.0, 1.0, 101))), "n", 130, "Return:")
        assertEquals("Rovaniemi", r.origin?.name)
        assertEquals("Duisburg", r.destination?.name)
        assertTrue(r.stays.isEmpty())
        assertEquals(130, r.startDay)
        assertEquals("Return: Lappland", r.name)
    }

    @Test fun duplicateShiftsStaysAndResetsPacked() {
        val t = trip(stays = listOf(PlanStay("H", 1.0, 1.0, 103))).copy(packed = listOf("ID_PAPERS"))
        val c = TripOps.duplicate(t, "n", 200, "(copy)")
        assertEquals(203, c.stays.single().checkInDay)
        assertTrue(c.packed.isEmpty())
        assertEquals("n", c.id)
    }
}
