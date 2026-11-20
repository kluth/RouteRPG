package app.kiloquest.nav

data class GuidanceState(
    val alongMeters: Double,
    val offRouteMeters: Double,
    val isOffRoute: Boolean,
    val nextManeuverIndex: Int,
    val metersToNextManeuver: Double,
    val remainingMeters: Double,
    val arrived: Boolean,
)

/** Verfolgt den Fortschritt auf einer Route anhand von GPS-Positionen. */
class GuidanceEngine(
    private val route: Route,
    private val offRouteThresholdMeters: Double = 50.0,
    private val arrivalThresholdMeters: Double = 30.0,
) {
    private var lastSegment = 0
    private var progress = 0.0

    fun update(position: LatLon): GuidanceState {
        var proj = route.line.project(position, lastSegment, window = 150)
        // Wenn lokal weit weg: global suchen (z. B. nach Abkuerzung / GPS-Sprung)
        if (proj.offsetMeters > offRouteThresholdMeters) {
            val global = route.line.project(position)
            if (global.offsetMeters < proj.offsetMeters) proj = global
        }
        val off = proj.offsetMeters > offRouteThresholdMeters
        if (!off) {
            lastSegment = proj.segmentIndex
            // Fortschritt nie rueckwaerts (GPS-Rauschen)
            progress = maxOf(progress, proj.alongMeters)
        }
        val total = route.line.lengthMeters
        // Naechstes Manoever: erstes, dessen Position noch vor uns liegt (das letzte = Ankunft)
        val next = route.maneuvers.indexOfFirst { it.atMeters > progress + 1.0 }
            .let { if (it < 0) route.maneuvers.lastIndex else it }
        val toNext = (route.maneuvers.getOrNull(next)?.atMeters ?: total) - progress
        val remaining = total - progress
        val arrived = remaining <= arrivalThresholdMeters && !off
        return GuidanceState(
            alongMeters = progress,
            offRouteMeters = proj.offsetMeters,
            isOffRoute = off,
            nextManeuverIndex = next,
            metersToNextManeuver = toNext.coerceAtLeast(0.0),
            remainingMeters = remaining.coerceAtLeast(0.0),
            arrived = arrived,
        )
    }
}

/** Entscheidet, wann welche Sprachansage faellig ist (je Manoever hoechstens zwei). */
class AnnouncementPlanner(
    private val route: Route,
    private val preDistanceMeters: Double = 500.0,
    private val nowDistanceMeters: Double = 60.0,
) {
    private val spokenPre = HashSet<Int>()
    private val spokenNow = HashSet<Int>()

    fun next(state: GuidanceState): String? {
        if (state.isOffRoute || state.arrived) return null
        val i = state.nextManeuverIndex
        val m = route.maneuvers.getOrNull(i) ?: return null
        if (state.metersToNextManeuver <= nowDistanceMeters && spokenNow.add(i)) {
            spokenPre.add(i)
            return m.instruction.ifBlank { null }
        }
        if (state.metersToNextManeuver <= preDistanceMeters && spokenPre.add(i)) {
            return (m.verbalPre ?: m.instruction).ifBlank { null }
        }
        return null
    }
}
