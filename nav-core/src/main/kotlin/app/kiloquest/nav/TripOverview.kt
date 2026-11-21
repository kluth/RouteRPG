package app.kiloquest.nav

import kotlin.math.ceil

/**
 * Eine Tages-Etappe einer Mehrtagesreise (Spec 002/003). Keine Texte: Anzeige ueber Strings aus den Schluesseln.
 *
 * Schluessel: `origin`, `destination`, `stay:<Name>` (gebuchte Unterkunft), `night:<Tag>` (berechneter Zwischenstopp).
 */
data class TripDayOverview(
    /** 1-basierter Reisetag. */
    val day: Int,
    /** Kalendertag als Epochentag (Starttag + day - 1). */
    val epochDay: Int,
    val startKey: String,
    val endKey: String,
    val distanceMeters: Double,
    val driveSeconds: Double,
    /** Vorschlag Anzahl Quests fuer die Etappe (aus Distanz). */
    val questSuggestion: Int,
    /** Erinnerung am Vorabend, Epoch-Millis (UTC). */
    val reminderAtMillis: Long,
    /** Suchbereich fuer die Uebernachtung am Tagesende; null am letzten Tag (Ziel). */
    val lodgingSearch: LodgingSearchArea?,
    val endsAtBooking: Boolean,
    val overBudget: Boolean,
)

/** Kreis um das Tagesende, in dem eine Unterkunft gesucht wird (siehe [LodgingQuery]). */
data class LodgingSearchArea(val center: LatLon, val radiusMeters: Int)

/** Gliedert eine Mehrtagesreise in Tages-Etappen. Reine Logik, deterministisch, Uhrzeit wird uebergeben. */
object TripOverview {
    /** Eine Quest je ca. 20 km, mindestens 2, hoechstens 40 je Tag. */
    const val METERS_PER_QUEST = 20_000.0

    fun questSuggestion(distanceMeters: Double): Int =
        ceil(distanceMeters / METERS_PER_QUEST).toInt().coerceIn(2, 40)

    /**
     * [route] = Gesamtroute, [startDay] = Epochtag des ersten Fahrtags, [booked] = feste Unterkuenfte (siehe [BookedPlanner]).
     * Tage werden fortlaufend gezaehlt (Ruhetage nicht beruecksichtigt).
     * Erinnerung: [reminderHour] Uhr am Vorabend des Fahrtags in der Zeitzone mit [utcOffsetSeconds].
     */
    fun build(
        route: Route,
        startDay: Int,
        booked: List<BookedStay>,
        budgetSeconds: Double,
        utcOffsetSeconds: Int = 0,
        reminderHour: Int = 18,
        lodgingRadiusMeters: Int = 15_000,
    ): List<TripDayOverview> {
        val days = BookedPlanner.plan(route, booked, budgetSeconds)
        var prevAlong = 0.0
        var prevKey = "origin"
        return days.map { d ->
            val key = when {
                d.end == DayEnd.FINAL -> "destination"
                d.end == DayEnd.BOOKED -> "stay:" + d.name.orEmpty()
                else -> "night:${d.day}"
            }
            val epoch = startDay + d.day - 1
            val reminder = (epoch - 1L) * 86_400_000L + reminderHour * 3_600_000L - utcOffsetSeconds * 1000L
            val o = TripDayOverview(
                day = d.day, epochDay = epoch, startKey = prevKey, endKey = key,
                distanceMeters = d.alongMeters - prevAlong, driveSeconds = d.stageSeconds,
                questSuggestion = questSuggestion(d.alongMeters - prevAlong),
                reminderAtMillis = reminder,
                lodgingSearch = if (d.end == DayEnd.FINAL) null else LodgingSearchArea(d.point, lodgingRadiusMeters),
                endsAtBooking = d.end == DayEnd.BOOKED, overBudget = d.overBudget,
            )
            prevAlong = d.alongMeters
            prevKey = key
            o
        }
    }
}

/** Overpass-QL fuer Uebernachtungen am Tagesende; Antwort-Parser wie [LodgingFinder] (gleiches JSON, kein Netzwerkcode). */
object LodgingQuery {
    private val KINDS = "hotel|guest_house|hostel|camp_site"

    fun build(area: LodgingSearchArea, limit: Int = 120): String {
        val c = "${"%.5f".format(java.util.Locale.ROOT, area.center.lat)},${"%.5f".format(java.util.Locale.ROOT, area.center.lon)}"
        return "[out:json][timeout:25];nwr(around:${area.radiusMeters},$c)[tourism~\"^($KINDS)\$\"][name];out center tags $limit;"
    }

    /** Name/Typ/Position; Elemente ohne Name oder Position werden verworfen. */
    fun parse(body: String): List<LodgingOption> = LodgingFinder.parse(body)
}
