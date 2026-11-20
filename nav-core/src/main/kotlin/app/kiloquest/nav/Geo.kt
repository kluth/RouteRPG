package app.kiloquest.nav

import kotlin.math.PI
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

data class LatLon(val lat: Double, val lon: Double)

private const val EARTH_RADIUS_M = 6_371_008.8

private fun Double.rad() = this * PI / 180.0

/** Grosskreisdistanz in Metern. */
fun haversine(a: LatLon, b: LatLon): Double {
    val dLat = (b.lat - a.lat).rad()
    val dLon = (b.lon - a.lon).rad()
    val h = sin(dLat / 2).let { it * it } +
        cos(a.lat.rad()) * cos(b.lat.rad()) * sin(dLon / 2).let { it * it }
    return 2 * EARTH_RADIUS_M * asin(sqrt(h))
}

/** Ergebnis der Projektion eines Punktes auf einen Linienzug. */
data class Projection(
    val segmentIndex: Int,
    val alongMeters: Double,
    val offsetMeters: Double,
)

/** Linienzug mit vorberechneten kumulierten Distanzen. */
class Polyline(val points: List<LatLon>) {
    init { require(points.size >= 2) { "Polyline needs at least 2 points" } }

    /** cumulative[i] = Distanz vom Start bis points[i]. */
    val cumulative: DoubleArray = DoubleArray(points.size).also {
        for (i in 1 until points.size) it[i] = it[i - 1] + haversine(points[i - 1], points[i])
    }
    val lengthMeters: Double get() = cumulative.last()

    /**
     * Projiziert [p] auf den Linienzug. Es werden nur Segmente ab [fromSegment]
     * bis [fromSegment]+[window] geprueft (Fortschritt kann nicht springen); mit
     * window = Int.MAX_VALUE wird alles geprueft.
     */
    fun project(p: LatLon, fromSegment: Int = 0, window: Int = Int.MAX_VALUE): Projection {
        val start = fromSegment.coerceIn(0, points.size - 2)
        val end = if (window == Int.MAX_VALUE) points.size - 2
        else minOf(points.size - 2, start + window)
        var best = Projection(start, cumulative[start], Double.MAX_VALUE)
        for (i in start..end) {
            val a = points[i]
            val b = points[i + 1]
            // lokale ebene Naeherung (equirectangular), fuer Segmentlaengen im km-Bereich ausreichend
            val kx = cos(a.lat.rad()) * EARTH_RADIUS_M * PI / 180.0
            val ky = EARTH_RADIUS_M * PI / 180.0
            val bx = (b.lon - a.lon) * kx
            val by = (b.lat - a.lat) * ky
            val px = (p.lon - a.lon) * kx
            val py = (p.lat - a.lat) * ky
            val len2 = bx * bx + by * by
            val t = if (len2 == 0.0) 0.0 else ((px * bx + py * by) / len2).coerceIn(0.0, 1.0)
            val dx = px - t * bx
            val dy = py - t * by
            val off = sqrt(dx * dx + dy * dy)
            if (off < best.offsetMeters) {
                best = Projection(i, cumulative[i] + t * (cumulative[i + 1] - cumulative[i]), off)
            }
        }
        return best
    }
}
