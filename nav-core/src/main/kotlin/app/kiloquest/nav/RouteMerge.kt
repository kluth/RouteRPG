package app.kiloquest.nav

/** Eine zusammengesetzte Route und die Streckenmeter der Zwischenziele (Etappenenden ohne das letzte). */
data class MergedRoute(val route: Route, val waypointsAlong: List<Double>)

/**
 * Setzt die Etappen einer Mehrpunkt-Anfrage (Valhalla "legs") zu **einer** Route zusammen, damit Fuehrung, Ansagen und Ankunftszeit durchgehend
 * funktionieren. Das Ende jeder Zwischen-Etappe wird zum Hinweis [waypointInstruction] (kein "Ziel erreicht"), der Start jeder Folge-Etappe entfaellt.
 */
object RouteMerge {
    private fun isArrive(type: Int) = type in 4..6
    private fun isDepart(type: Int) = type in 1..3

    fun merge(legs: List<Route>, waypointInstruction: String): MergedRoute {
        require(legs.isNotEmpty()) { "no legs" }
        if (legs.size == 1) return MergedRoute(legs[0], emptyList())
        val points = ArrayList<LatLon>(legs[0].line.points)
        val maneuvers = ArrayList<Maneuver>()
        val waypoints = ArrayList<Double>()
        var offset = 0.0
        legs.forEachIndexed { i, leg ->
            val last = i == legs.lastIndex
            if (i > 0) points += leg.line.points.drop(1)
            leg.maneuvers.forEachIndexed { j, m ->
                val shifted = m.copy(atMeters = m.atMeters + offset)
                when {
                    i > 0 && isDepart(m.type) -> Unit
                    !last && j == leg.maneuvers.lastIndex && isArrive(m.type) ->
                        maneuvers += shifted.copy(type = 8, instruction = waypointInstruction, verbalPre = waypointInstruction, verbalPost = null, lengthMeters = 0.0)
                    else -> maneuvers += shifted
                }
            }
            if (!last) waypoints += offset + leg.line.lengthMeters
            offset += leg.line.lengthMeters
        }
        val line = Polyline(points)
        return MergedRoute(Route(line, maneuvers, legs.sumOf { it.lengthMeters }, legs.sumOf { it.durationSeconds }), waypoints)
    }
}
