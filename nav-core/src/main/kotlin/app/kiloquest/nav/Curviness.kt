package app.kiloquest.nav

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos

/**
 * Wie kurvig ist die Strecke voraus? Grundlage fuer den Reiseuebelkeit-Modus.
 * Gemessen wird die Summe der Richtungsaenderungen pro Kilometer. Einzelne scharfe Abbiegungen
 * (Kreuzungen, >= [maxVertexAngleDeg]) zaehlen nicht, nur laengere kurvige Abschnitte.
 */
class CurveProfile(
    route: Route,
    private val binMeters: Double = 250.0,
    private val windowMeters: Double = 1000.0,
    private val thresholdDegPerKm: Double = 150.0,
    private val maxVertexAngleDeg: Double = 60.0,
) {
    private val bins: DoubleArray

    init {
        val pts = route.line.points
        val cum = route.line.cumulative
        val n = (route.line.lengthMeters / binMeters).toInt() + 2
        bins = DoubleArray(n)
        for (i in 1 until pts.size - 1) {
            // Sehr kurze Segmente (GPS-/Digitalisierungsrauschen) ueberspringen
            if (cum[i] - cum[i - 1] < 3.0 || cum[i + 1] - cum[i] < 3.0) continue
            val angle = abs(turnDegrees(bearing(pts[i - 1], pts[i]), bearing(pts[i], pts[i + 1])))
            if (angle < 2.0 || angle >= maxVertexAngleDeg) continue
            bins[(cum[i] / binMeters).toInt().coerceIn(0, n - 1)] += angle
        }
    }

    /** Richtungsaenderung in Grad pro km im Fenster [alongMeters, alongMeters + window]. */
    fun degreesPerKm(alongMeters: Double): Double {
        val from = (alongMeters / binMeters).toInt().coerceIn(0, bins.size - 1)
        val count = (windowMeters / binMeters).toInt().coerceAtLeast(1)
        var sum = 0.0
        for (b in from until minOf(bins.size, from + count)) sum += bins[b]
        return sum / (windowMeters / 1000.0)
    }

    fun isCurvy(alongMeters: Double): Boolean = degreesPerKm(alongMeters) >= thresholdDegPerKm

    private fun bearing(a: LatLon, b: LatLon): Double {
        val dLon = (b.lon - a.lon) * cos(a.lat * PI / 180.0)
        val dLat = b.lat - a.lat
        return Math.toDegrees(atan2(dLon, dLat))
    }

    private fun turnDegrees(b1: Double, b2: Double): Double {
        var d = b2 - b1
        while (d > 180) d -= 360
        while (d < -180) d += 360
        return d
    }
}

/**
 * Entscheidet, wann ein gesprochener "Blick nach draussen"-Hinweis faellig ist:
 * erst wenn die Kurvigkeit mindestens [minCurvyMs] anhaelt, danach hoechstens alle [promptEveryMs].
 */
class SicknessCoach(private val minCurvyMs: Long = 20_000, private val promptEveryMs: Long = 180_000) {
    private var curvySince: Long? = null
    private var lastPrompt = Long.MIN_VALUE / 2

    fun onTick(curvy: Boolean, nowMs: Long): Boolean {
        if (!curvy) { curvySince = null; return false }
        val since = curvySince ?: nowMs.also { curvySince = it }
        if (nowMs - since < minCurvyMs || nowMs - lastPrompt < promptEveryMs) return false
        lastPrompt = nowMs
        return true
    }
}
