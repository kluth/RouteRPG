package app.kiloquest.nav

/** Reisen kopieren und umdrehen. */
object TripOps {
    /** Rueckreise: Start und Ziel getauscht, Buchungen werden nicht uebernommen (sie gehoeren zur Hinreise). */
    fun returnTrip(t: PlannedTrip, newId: String, startDay: Int, namePrefix: String): PlannedTrip =
        PlannedTrip(newId, "$namePrefix ${t.name.ifBlank { t.destination?.name.orEmpty() }}".trim(), startDay, t.destination, t.origin)

    /** Kopie mit neuer Id und neuem Abreisetag; Buchungstage werden um denselben Abstand verschoben, Haken zurueckgesetzt. */
    fun duplicate(t: PlannedTrip, newId: String, startDay: Int, nameSuffix: String): PlannedTrip {
        val shift = startDay - t.startDay
        return t.copy(
            id = newId,
            name = "${t.name} $nameSuffix".trim(),
            startDay = startDay,
            stays = t.stays.map { it.copy(checkInDay = it.checkInDay + shift) },
            packed = emptyList(),
        )
    }
}
