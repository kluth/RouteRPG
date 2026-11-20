package app.kiloquest.nav

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable data class PlanPlace(val name: String, val lat: Double, val lon: Double) {
    val position: LatLon get() = LatLon(lat, lon)
}

/** Feste Buchung: [checkInDay] als Epochentag (java.time.LocalDate.toEpochDay), [nights] Naechte. */
@Serializable data class PlanStay(val name: String, val lat: Double, val lon: Double, val checkInDay: Int, val nights: Int = 1) {
    val position: LatLon get() = LatLon(lat, lon)
    val checkOutDay: Int get() = checkInDay + nights.coerceAtLeast(1)
}

@Serializable data class PlannedTrip(
    val id: String,
    val name: String,
    val startDay: Int,
    val origin: PlanPlace? = null,
    val destination: PlanPlace? = null,
    val stays: List<PlanStay> = emptyList(),
)

/** JSON-Speicher fuer geplante Reisen. Unlesbare Daten ergeben eine leere Liste, nie einen Absturz. */
object PlanStore {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    fun encode(trips: List<PlannedTrip>): String = json.encodeToString(trips)
    fun decode(text: String?): List<PlannedTrip> =
        if (text.isNullOrBlank()) emptyList() else runCatching { json.decodeFromString<List<PlannedTrip>>(text) }.getOrDefault(emptyList())
}
