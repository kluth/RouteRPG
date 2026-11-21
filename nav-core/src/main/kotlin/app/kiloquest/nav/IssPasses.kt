package app.kiloquest.nav

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.asin
import kotlin.math.atan
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tan

/** Zwei-Zeilen-Bahnelemente (TLE). Prueft Format und Pruefsummen; ungueltig = null. */
class Tle private constructor(
    val epochSec: Double,
    val inclinationDeg: Double,
    val raanDeg: Double,
    val eccentricity: Double,
    val argPerigeeDeg: Double,
    val meanAnomalyDeg: Double,
    val revPerDay: Double,
) {
    /** Alter in Tagen zum Zeitpunkt [nowEpochSec]. */
    fun ageDays(nowEpochSec: Long): Double = (nowEpochSec - epochSec) / 86400.0

    companion object {
        private fun checksumOk(line: String): Boolean {
            if (line.length < 69) return false
            var sum = 0
            for (i in 0 until 68) {
                val c = line[i]
                sum += when { c.isDigit() -> c - '0'; c == '-' -> 1; else -> 0 }
            }
            return sum % 10 == line[68] - '0'
        }

        /** Nimmt Text mit zwei (oder drei, mit Namenszeile) Zeilen; null bei Fehlern. */
        fun parse(text: String): Tle? = runCatching {
            val lines = text.lines().map { it.trimEnd() }.filter { it.isNotBlank() }
            val l1 = lines.firstOrNull { it.startsWith("1 ") } ?: return null
            val l2 = lines.firstOrNull { it.startsWith("2 ") } ?: return null
            if (!checksumOk(l1) || !checksumOk(l2)) return null
            val yy = l1.substring(18, 20).trim().toInt()
            val year = if (yy < 57) 2000 + yy else 1900 + yy
            val day = l1.substring(20, 32).trim().toDouble()
            val jan1 = java.time.LocalDate.of(year, 1, 1).toEpochDay() * 86400.0
            val ecc = ("0." + l2.substring(26, 33).trim()).toDouble()
            val n = l2.substring(52, 63).trim().toDouble()
            if (n <= 0.0 || ecc >= 1.0) return null
            Tle(
                jan1 + (day - 1.0) * 86400.0, l2.substring(8, 16).trim().toDouble(), l2.substring(17, 25).trim().toDouble(), ecc,
                l2.substring(34, 42).trim().toDouble(), l2.substring(43, 51).trim().toDouble(), n,
            )
        }.getOrNull()
    }
}

/** Ein sichtbarer Ueberflug: Zeiten in Epochensekunden (UTC), Hoehe und Azimut in Grad (0 = N, 90 = O). */
data class IssPass(
    val startSec: Long,
    val maxSec: Long,
    val endSec: Long,
    val maxAltitudeDeg: Double,
    val startAzimuthDeg: Double,
    val endAzimuthDeg: Double,
) {
    val durationSec: Long get() = endSec - startSec
}

/**
 * ISS-Ueberfluege aus TLE-Daten, ohne Bibliothek.
 *
 * ACHTUNG Naeherung: kein vollstaendiges SGP4. Es wird aus den Mittelwerten Kepler-Bewegung mit den saekularen
 * J2-Anteilen (Knoten, Perigaeum, mittlere Anomalie) gerechnet, ohne Luftwiderstand und ohne kurzperiodische Terme.
 * Der Fehler waechst mit dem Alter der Bahndaten (am Tag 0 wenige km, nach einigen Tagen merklich mehr). Deshalb nur
 * mit frischen Daten (siehe [MAX_AGE_DAYS]) verwenden und Zeiten als "etwa" anzeigen. Zeitaufloesung 10 s.
 *
 * Sichtbar heisst: Beobachter in Daemmerung/Dunkelheit (Sonne unter -6 Grad), ISS ueber 10 Grad und von der Sonne beleuchtet
 * (Zylinder-Erdschatten).
 */
object IssPasses {
    const val MAX_AGE_DAYS = 5.0
    private const val MU = 398600.4418
    private const val RE = 6378.137
    private const val J2 = 1.08263e-3
    private const val F = 1.0 / 298.257223563
    private const val MIN_ALT = 10.0
    private const val SUN_DARK = -6.0
    private const val STEP = 10L

    private fun rad(d: Double) = d * PI / 180.0
    private fun deg(r: Double) = r * 180.0 / PI

    /** ISS-Position im inertialen System (km) zur Zeit [epochSec]. */
    fun positionEci(tle: Tle, epochSec: Double): DoubleArray {
        val dt = epochSec - tle.epochSec
        val n = tle.revPerDay * 2 * PI / 86400.0
        val e = tle.eccentricity
        val a = (MU / (n * n)).pow(1.0 / 3.0)
        val i = rad(tle.inclinationDeg)
        val p = a * (1 - e * e)
        val k = J2 * (RE / p) * (RE / p)
        val raanDot = -1.5 * k * n * cos(i)
        val argpDot = 0.75 * k * n * (5 * cos(i) * cos(i) - 1)
        val mDot = n * (1 + 0.75 * k * sqrt(1 - e * e) * (3 * cos(i) * cos(i) - 1))
        val raan = rad(tle.raanDeg) + raanDot * dt
        val argp = rad(tle.argPerigeeDeg) + argpDot * dt
        val m = (rad(tle.meanAnomalyDeg) + mDot * dt).mod(2 * PI)
        var ea = m
        repeat(8) { ea -= (ea - e * sin(ea) - m) / (1 - e * cos(ea)) }
        val nu = 2 * atan(sqrt((1 + e) / (1 - e)) * tan(ea / 2))
        val r = a * (1 - e * cos(ea))
        val xp = r * cos(nu); val yp = r * sin(nu)
        val cw = cos(argp); val sw = sin(argp); val co = cos(raan); val so = sin(raan); val ci = cos(i); val si = sin(i)
        val x = (co * cw - so * sw * ci) * xp + (-co * sw - so * cw * ci) * yp
        val y = (so * cw + co * sw * ci) * xp + (-so * sw + co * cw * ci) * yp
        val z = (sw * si) * xp + (cw * si) * yp
        return doubleArrayOf(x, y, z)
    }

    private fun gmstRad(epochSec: Double): Double {
        val jd = epochSec / 86400.0 + 2440587.5
        return rad((280.46061837 + 360.98564736629 * (jd - 2451545.0)).mod(360.0))
    }

    /** Einheitsvektor zur Sonne (inertial), niedrige Genauigkeit (~0,01 Grad). */
    fun sunDirection(epochSec: Double): DoubleArray {
        val d = epochSec / 86400.0 + 2440587.5 - 2451545.0
        val g = rad((357.528 + 0.9856003 * d).mod(360.0))
        val lam = rad((280.460 + 0.9856474 * d).mod(360.0) + 1.915 * sin(g) + 0.020 * sin(2 * g))
        val eps = rad(23.439 - 4.0e-7 * d)
        return doubleArrayOf(cos(lam), cos(eps) * sin(lam), sin(eps) * sin(lam))
    }

    fun inEarthShadow(r: DoubleArray, sun: DoubleArray): Boolean {
        val dot = r[0] * sun[0] + r[1] * sun[1] + r[2] * sun[2]
        if (dot >= 0) return false
        val px = r[0] - dot * sun[0]; val py = r[1] - dot * sun[1]; val pz = r[2] - dot * sun[2]
        return sqrt(px * px + py * py + pz * pz) < RE
    }

    /** Hoehe/Azimut der ISS fuer einen Beobachter (WGS84, Meereshoehe). */
    fun lookAngles(tle: Tle, epochSec: Double, latDeg: Double, lonDeg: Double): SkyPosition {
        val r = positionEci(tle, epochSec)
        val th = gmstRad(epochSec)
        val xe = r[0] * cos(th) + r[1] * sin(th)
        val ye = -r[0] * sin(th) + r[1] * cos(th)
        val ze = r[2]
        val lat = rad(latDeg); val lon = rad(lonDeg)
        val e2 = F * (2 - F)
        val nn = RE / sqrt(1 - e2 * sin(lat) * sin(lat))
        val ox = nn * cos(lat) * cos(lon); val oy = nn * cos(lat) * sin(lon); val oz = nn * (1 - e2) * sin(lat)
        val dx = xe - ox; val dy = ye - oy; val dz = ze - oz
        val east = -sin(lon) * dx + cos(lon) * dy
        val north = -sin(lat) * cos(lon) * dx - sin(lat) * sin(lon) * dy + cos(lat) * dz
        val up = cos(lat) * cos(lon) * dx + cos(lat) * sin(lon) * dy + sin(lat) * dz
        val range = sqrt(dx * dx + dy * dy + dz * dz)
        return SkyPosition(deg(asin(up / range)), deg(atan2(east, north)).mod(360.0))
    }

    /**
     * Sichtbare Ueberfluege ab [fromEpochSec] fuer [days] Tage. Mit [requireDark] = false auch helle (geometrische) Ueberfluege.
     * Leer, wenn die Bahndaten aelter als [MAX_AGE_DAYS] sind.
     */
    fun next(tle: Tle, fromEpochSec: Long, latDeg: Double, lonDeg: Double, days: Int = 3, requireDark: Boolean = true): List<IssPass> {
        if (abs(tle.ageDays(fromEpochSec)) > MAX_AGE_DAYS) return emptyList()
        val out = ArrayList<IssPass>()
        var start = -1L; var startAz = 0.0; var last = -1L; var lastAz = 0.0
        var maxAlt = -90.0; var maxT = 0L
        fun close() {
            if (start >= 0) out += IssPass(start, maxT, last, maxAlt, startAz, lastAz)
            start = -1; maxAlt = -90.0
        }
        var t = fromEpochSec
        val end = fromEpochSec + days * 86400L
        while (t <= end) {
            val look = lookAngles(tle, t.toDouble(), latDeg, lonDeg)
            val visible = look.altitudeDeg > MIN_ALT &&
                (!requireDark || (Sky.sun(t, latDeg, lonDeg).altitudeDeg < SUN_DARK && !inEarthShadow(positionEci(tle, t.toDouble()), sunDirection(t.toDouble()))))
            if (visible) {
                if (start < 0) { start = t; startAz = look.azimuthDeg }
                last = t; lastAz = look.azimuthDeg
                if (look.altitudeDeg > maxAlt) { maxAlt = look.altitudeDeg; maxT = t }
            } else close()
            t += STEP
        }
        close()
        return out
    }
}
