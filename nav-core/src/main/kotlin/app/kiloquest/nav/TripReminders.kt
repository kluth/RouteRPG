package app.kiloquest.nav

enum class ReminderKind { COUNTDOWN, DEPARTURE_EVE, DEPARTURE_DAY, CHECK_IN_TOMORROW, CHECK_IN_TODAY }

/** Faellige Erinnerung; [key] ist eindeutig und wird gespeichert, damit sie nur einmal erscheint. */
data class Reminder(val key: String, val tripId: String, val tripName: String, val kind: ReminderKind, val daysLeft: Int, val stayName: String? = null)

/** Erinnerungen vor Reisebeginn und vor jedem Check-in. Reine Logik, Tage als Epochentag. */
object TripReminders {
    /** Meilensteine in Tagen vor Abreise. 1 = Vorabend, 0 = Abreisetag. */
    val MILESTONES = listOf(30, 14, 7, 3, 1, 0)

    /**
     * Ist die App laenger nicht geoeffnet worden, wird nur der naechstliegende Meilenstein gemeldet (nicht alle verpassten).
     * Vergangene Reisen und bereits gesendete Schluessel ([sent]) erzeugen nichts.
     */
    fun due(trips: List<PlannedTrip>, today: Int, sent: Set<String>): List<Reminder> {
        val out = ArrayList<Reminder>()
        for (t in trips) {
            val label = t.name.ifBlank { t.destination?.name.orEmpty() }
            val left = t.startDay - today
            if (left in 0..MILESTONES.first()) {
                val m = MILESTONES.filter { it >= left }.min()
                val kind = when (m) { 0 -> ReminderKind.DEPARTURE_DAY; 1 -> ReminderKind.DEPARTURE_EVE; else -> ReminderKind.COUNTDOWN }
                val key = "${t.id}:m:$m"
                if (key !in sent) out += Reminder(key, t.id, label, kind, left)
            }
            t.stays.forEachIndexed { i, s ->
                val d = s.checkInDay - today
                val kind = when (d) { 1 -> ReminderKind.CHECK_IN_TOMORROW; 0 -> ReminderKind.CHECK_IN_TODAY; else -> null }
                if (kind != null) {
                    val key = "${t.id}:c:${s.checkInDay}:$i"
                    if (key !in sent) out += Reminder(key, t.id, label, kind, d, s.name)
                }
            }
        }
        return out
    }

    /** Schluessel zu nicht mehr vorhandenen Reisen entfernen, damit die Liste nicht waechst. */
    fun prune(sent: Set<String>, trips: List<PlannedTrip>): Set<String> {
        val ids = trips.map { it.id }.toSet()
        return sent.filter { it.substringBefore(':') in ids }.toSet()
    }

    /** Die naechste Reise, die noch nicht vorbei ist (heute oder spaeter), sonst null. */
    fun next(trips: List<PlannedTrip>, today: Int): PlannedTrip? =
        trips.filter { it.startDay >= today }.minByOrNull { it.startDay }
}
