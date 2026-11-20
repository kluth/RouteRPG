package app.kiloquest.nav

import java.time.LocalDateTime
import java.time.LocalTime
import kotlin.test.Test
import kotlin.test.assertEquals

class OpeningHoursTest {
    // 2026-11-16 ist ein Montag
    private fun at(day: Int, h: Int, m: Int = 0) = LocalDateTime.of(2026, 11, 16 + day, h, m)
    private fun state(oh: String?, day: Int, h: Int, m: Int = 0) = OpeningHours.statusAt(oh, at(day, h, m)).state

    @Test fun twentyFourSevenAndEmpty() {
        assertEquals(OpenState.OPEN, state("24/7", 3, 3))
        assertEquals(OpenState.UNKNOWN, state(null, 0, 12))
        assertEquals(OpenState.UNKNOWN, state("", 0, 12))
    }

    @Test fun weekdaysAndTimes() {
        val oh = "Mo-Fr 08:00-18:00; Sa 09:00-14:00"
        assertEquals(OpenState.OPEN, state(oh, 0, 8))
        assertEquals(OpenState.CLOSED, state(oh, 0, 7, 59))
        assertEquals(OpenState.OPEN, state(oh, 4, 17, 59))
        assertEquals(OpenState.CLOSED, state(oh, 4, 18))
        assertEquals(OpenState.OPEN, state(oh, 5, 13))
        assertEquals(OpenState.CLOSED, state(oh, 5, 15))
        assertEquals(OpenState.CLOSED, state(oh, 6, 12)) // Sonntag nicht genannt
    }

    @Test fun closingTimeIsReported() {
        assertEquals(LocalTime.of(18, 0), OpeningHours.statusAt("Mo-Fr 08:00-18:00", at(1, 10)).closesAt)
    }

    @Test fun laterRulesOverrideEarlierOnes() {
        val oh = "Mo-Su 08:00-20:00; We off"
        assertEquals(OpenState.OPEN, state(oh, 0, 12))
        assertEquals(OpenState.CLOSED, state(oh, 2, 12))
        assertEquals(OpenState.CLOSED, state("off", 0, 12))
    }

    @Test fun splitDay() {
        val oh = "Mo-Fr 08:00-12:00,14:00-18:00"
        assertEquals(OpenState.OPEN, state(oh, 1, 11))
        assertEquals(OpenState.CLOSED, state(oh, 1, 13))
        assertEquals(OpenState.OPEN, state(oh, 1, 15))
    }

    @Test fun overMidnight() {
        val oh = "Fr,Sa 20:00-02:00"
        assertEquals(OpenState.OPEN, state(oh, 4, 23))      // Freitag 23 Uhr
        assertEquals(OpenState.OPEN, state(oh, 5, 1))       // Samstag 1 Uhr (aus der Freitagsspanne)
        assertEquals(OpenState.CLOSED, state(oh, 5, 3))
        assertEquals(LocalTime.of(2, 0), OpeningHours.statusAt(oh, at(5, 1)).closesAt)
        assertEquals(OpenState.OPEN, state(oh, 6, 1))       // Sonntag 1 Uhr (Samstagsspanne)
        assertEquals(OpenState.CLOSED, state(oh, 0, 1))     // Montag: Sonntag ist nicht genannt
    }

    @Test fun holidaysAreIgnoredButNeverMakeAWrongClosed() {
        val oh = "Mo-Sa 08:00-20:00; PH off"
        assertEquals(OpenState.OPEN, state(oh, 1, 12))
        assertEquals(OpenState.CLOSED, state(oh, 6, 12))
    }

    @Test fun unsupportedSyntaxIsUnknown() {
        for (oh in listOf("sunrise-sunset", "Apr-Oct: 08:00-20:00", "Mo-Fr 10:00+", "by appointment", "Mo 25:00-26:00", "Jan 01 off", "Mo-Xx 08:00-09:00")) {
            assertEquals(OpenState.UNKNOWN, state(oh, 0, 12), oh)
        }
    }

    @Test fun caseAndDashVariants() {
        assertEquals(OpenState.OPEN, state("mo-fr 08:00–18:00", 0, 9))
        assertEquals(OpenState.OPEN, state("Mo-Su 00:00-24:00", 3, 23))
    }
}
