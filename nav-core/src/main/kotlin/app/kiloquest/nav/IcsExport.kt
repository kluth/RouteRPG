package app.kiloquest.nav

import java.time.LocalDate
import java.time.format.DateTimeFormatter

/** Kalenderdatei (iCalendar, RFC 5545) einer geplanten Reise: Abreise, Buchungen, Ankunft. Ganztaegige Termine. */
object IcsExport {
    private val day = DateTimeFormatter.BASIC_ISO_DATE

    /** Text escapen: Backslash, Semikolon, Komma, Zeilenumbruch. */
    fun escape(s: String): String = s.replace("\\", "\\\\").replace(";", "\\;").replace(",", "\\,").replace("\r\n", "\\n").replace("\n", "\\n")

    /** Zeilen laengen auf 75 Oktette begrenzen (Fortsetzung mit Leerzeichen), ohne UTF-8-Zeichen zu zerschneiden. */
    fun fold(line: String): String {
        val out = StringBuilder()
        var bytes = 0
        for (ch in line.codePoints().toArray()) {
            val s = String(Character.toChars(ch))
            val n = s.toByteArray(Charsets.UTF_8).size
            if (bytes + n > 75) { out.append("\r\n "); bytes = 1 }
            out.append(s); bytes += n
        }
        return out.toString()
    }

    private fun d(epochDay: Int) = LocalDate.ofEpochDay(epochDay.toLong()).format(day)

    /**
     * [arrivalDay] ist optional (nur nach einer Berechnung bekannt). [stamp] = Erstellzeit "yyyyMMdd'T'HHmmss'Z'",
     * wird uebergeben, damit das Ergebnis deterministisch bleibt.
     */
    fun build(trip: PlannedTrip, arrivalDay: Int?, stamp: String): String {
        val lines = ArrayList<String>()
        lines += "BEGIN:VCALENDAR"
        lines += "VERSION:2.0"
        lines += "PRODID:-//KiloQuest//Trip//EN"
        lines += "CALSCALE:GREGORIAN"
        fun event(uid: String, from: Int, toExclusive: Int, summary: String, description: String = "", location: String = "") {
            lines += "BEGIN:VEVENT"
            lines += "UID:$uid@kiloquest.local"
            lines += "DTSTAMP:$stamp"
            lines += "DTSTART;VALUE=DATE:${d(from)}"
            lines += "DTEND;VALUE=DATE:${d(toExclusive)}"
            lines += "SUMMARY:${escape(summary)}"
            if (description.isNotBlank()) lines += "DESCRIPTION:${escape(description)}"
            if (location.isNotBlank()) lines += "LOCATION:${escape(location)}"
            lines += "END:VEVENT"
        }
        val name = trip.name.ifBlank { trip.destination?.name ?: "KiloQuest" }
        val route = listOfNotNull(trip.origin?.name, trip.destination?.name).joinToString(" -> ")
        event("${trip.id}-start", trip.startDay, trip.startDay + 1, "$name: $route")
        trip.stays.sortedBy { it.checkInDay }.forEachIndexed { i, s ->
            event("${trip.id}-stay-$i", s.checkInDay, s.checkOutDay, s.name, s.note, s.name)
        }
        if (arrivalDay != null) event("${trip.id}-arrival", arrivalDay, arrivalDay + 1, "$name: ${trip.destination?.name.orEmpty()}")
        lines += "END:VCALENDAR"
        return lines.joinToString("\r\n") { fold(it) } + "\r\n"
    }
}
