package app.kiloquest.nav

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.sin
import kotlin.math.sqrt

enum class Planet(val key: String) { VENUS("planet_venus"), MARS("planet_mars"), JUPITER("planet_jupiter"), SATURN("planet_saturn") }

/** Hoehe (ueber dem Horizont, negativ = darunter) und Azimut (0 = N, 90 = O) in Grad. */
data class SkyPosition(val altitudeDeg: Double, val azimuthDeg: Double)

data class MoonInfo(
    /** 0.0 = Neumond, 0.25 = erstes Viertel, 0.5 = Vollmond, 0.75 = letztes Viertel. */
    val cycle: Double,
    /** Beleuchteter Anteil der Scheibe 0..1. */
    val illumination: Double,
    /** Name-Key: moon_new, moon_waxing_crescent, moon_first_quarter, moon_waxing_gibbous, moon_full, moon_waning_gibbous, moon_last_quarter, moon_waning_crescent. */
    val phaseKey: String,
    val position: SkyPosition,
)

/** Ein Himmelsobjekt in der Abendliste: [key] ist ein String-Key (moon_*, planet_*), [bestEpochSec] der Zeitpunkt mit der groessten Hoehe. */
data class TonightEntry(val key: String, val bestEpochSec: Long, val position: SkyPosition)

/**
 * Lokale Astronomie ohne Netz (kein Bezug zu `game-engine`; dort gibt es eine eigene grobe `Sun`/`Moon`).
 *
 * Naeherung: Bahnelemente und Stoerungsterme nach Paul Schlyter ("How to compute planetary positions"),
 * Epoche d = Tage seit 1999-12-31 0:00 UT. Genauigkeit: Mond ca. 0,3 Grad, Planeten und Sonne ca. 0,1-1 Grad
 * (Venus/Mars ohne Stoerungsterme; Jupiter/Saturn mit den Haupt-Stoerungen). Kein Refraktions- oder Nutationsmodell,
 * Mond mit Parallaxe (topozentrisch). Fuer "ist das da oben die Venus?" voellig ausreichend.
 */
object Sky {
    private const val MIN_ALTITUDE = 5.0
    private const val DARK_SUN_ALTITUDE = -6.0
    private val PHASE_KEYS = listOf(
        "moon_new", "moon_waxing_crescent", "moon_first_quarter", "moon_waxing_gibbous",
        "moon_full", "moon_waning_gibbous", "moon_last_quarter", "moon_waning_crescent",
    )

    private fun rad(d: Double) = d * PI / 180.0
    private fun deg(r: Double) = r * 180.0 / PI
    private fun rev(x: Double) = x.mod(360.0)
    private fun sind(x: Double) = sin(rad(x))
    private fun cosd(x: Double) = cos(rad(x))

    private fun dayNumber(epochSec: Long) = epochSec / 86400.0 + 2440587.5 - 2451543.5

    private class Elements(val n: Double, val i: Double, val w: Double, val a: Double, val e: Double, val m: Double)

    private fun elements(p: Planet?, d: Double): Elements = when (p) {
        null -> Elements(0.0, 0.0, 282.9404 + 4.70935E-5 * d, 1.0, 0.016709 - 1.151E-9 * d, 356.0470 + 0.9856002585 * d)
        Planet.VENUS -> Elements(76.6799 + 2.46590E-5 * d, 3.3946 + 2.75E-8 * d, 54.8910 + 1.38374E-5 * d, 0.723330, 0.006773 - 1.302E-9 * d, 48.0052 + 1.6021302244 * d)
        Planet.MARS -> Elements(49.5574 + 2.11081E-5 * d, 1.8497 - 1.78E-8 * d, 286.5016 + 2.92961E-5 * d, 1.523688, 0.093405 + 2.516E-9 * d, 18.6021 + 0.5240207766 * d)
        Planet.JUPITER -> Elements(100.4542 + 2.76854E-5 * d, 1.3030 - 1.557E-7 * d, 273.8777 + 1.64505E-5 * d, 5.20256, 0.048498 + 4.469E-9 * d, 19.8950 + 0.0830853001 * d)
        Planet.SATURN -> Elements(113.6634 + 2.38980E-5 * d, 2.4886 - 1.081E-7 * d, 339.3939 + 2.97661E-5 * d, 9.55475, 0.055546 - 9.499E-9 * d, 316.9670 + 0.0334442282 * d)
    }

    /** Position auf der Bahn: (x, y, z, r, Laenge, Breite) in der Bahnebene relativ zum Zentralkoerper. */
    private class Xyz(val x: Double, val y: Double, val z: Double, val r: Double)

    private fun kepler(mDeg: Double, e: Double): Double {
        var ea = mDeg + deg(e) * sind(mDeg) * (1 + e * cosd(mDeg))
        repeat(6) { ea -= (ea - deg(e) * sind(ea) - mDeg) / (1 - e * cosd(ea)) }
        return ea
    }

    private fun orbit(el: Elements): Xyz {
        val m = rev(el.m)
        val ea = kepler(m, el.e)
        val xv = el.a * (cosd(ea) - el.e)
        val yv = el.a * sqrt(1 - el.e * el.e) * sind(ea)
        val v = deg(atan2(yv, xv))
        val r = sqrt(xv * xv + yv * yv)
        val vw = v + el.w
        return Xyz(
            r * (cosd(el.n) * cosd(vw) - sind(el.n) * sind(vw) * cosd(el.i)),
            r * (sind(el.n) * cosd(vw) + cosd(el.n) * sind(vw) * cosd(el.i)),
            r * sind(vw) * sind(el.i),
            r,
        )
    }

    /** Geozentrische Sonne in ekliptikalen Koordinaten: (x, y, Laenge). */
    private fun sunEcliptic(d: Double): Triple<Double, Double, Double> {
        val el = elements(null, d)
        val ea = kepler(rev(el.m), el.e)
        val xv = cosd(ea) - el.e
        val yv = sqrt(1 - el.e * el.e) * sind(ea)
        val lon = rev(deg(atan2(yv, xv)) + el.w)
        val r = sqrt(xv * xv + yv * yv)
        return Triple(r * cosd(lon), r * sind(lon), lon)
    }

    /** Ekliptikale kartesische (geozentrische) Koordinaten -> Hoehe/Azimut. */
    private fun horizontal(xe: Double, ye: Double, ze: Double, d: Double, latDeg: Double, lonDeg: Double): SkyPosition {
        val ecl = 23.4393 - 3.563E-7 * d
        val xq = xe
        val yq = ye * cosd(ecl) - ze * sind(ecl)
        val zq = ye * sind(ecl) + ze * cosd(ecl)
        val ra = deg(atan2(yq, xq))
        val dec = deg(atan2(zq, sqrt(xq * xq + yq * yq)))
        // Sternzeit Greenwich (d ist ab 1999-12-31.0 gezaehlt; J2000-Tage = d - 0.5... hier ueber Julianisches Datum)
        val dJ2000 = d - 1.5 // 2451543.5 -> 2451545.0
        val gmst = rev(280.46061837 + 360.98564736629 * dJ2000)
        val ha = rev(gmst + lonDeg - ra)
        val sinAlt = sind(latDeg) * sind(dec) + cosd(latDeg) * cosd(dec) * cosd(ha)
        val alt = deg(asin(sinAlt.coerceIn(-1.0, 1.0)))
        val az = rev(deg(atan2(-cosd(dec) * sind(ha), sind(dec) * cosd(latDeg) - cosd(dec) * cosd(ha) * sind(latDeg))))
        return SkyPosition(alt, az)
    }

    fun sun(epochSec: Long, latDeg: Double, lonDeg: Double): SkyPosition {
        val d = dayNumber(epochSec)
        val (x, y, _) = sunEcliptic(d)
        return horizontal(x, y, 0.0, d, latDeg, lonDeg)
    }

    fun planet(p: Planet, epochSec: Long, latDeg: Double, lonDeg: Double): SkyPosition {
        val d = dayNumber(epochSec)
        val (sx, sy, _) = sunEcliptic(d)
        val h = orbit(elements(p, d))
        var lon = rev(deg(atan2(h.y, h.x)))
        var lat = deg(atan2(h.z, sqrt(h.x * h.x + h.y * h.y)))
        val mj = rev(elements(Planet.JUPITER, d).m)
        val ms = rev(elements(Planet.SATURN, d).m)
        when (p) {
            Planet.JUPITER -> lon += -0.332 * sind(2 * mj - 5 * ms - 67.6) - 0.056 * sind(2 * mj - 2 * ms + 21) +
                0.042 * sind(3 * mj - 5 * ms + 21) - 0.036 * sind(mj - 2 * ms) + 0.022 * cosd(mj - ms) +
                0.023 * sind(2 * mj - 3 * ms + 52) - 0.016 * sind(mj - 5 * ms - 69)
            Planet.SATURN -> {
                lon += 0.812 * sind(2 * mj - 5 * ms - 67.6) - 0.229 * cosd(2 * mj - 4 * ms - 2) +
                    0.119 * sind(mj - 2 * ms - 3) + 0.046 * sind(2 * mj - 6 * ms - 69) + 0.014 * sind(mj - 3 * ms + 32)
                lat += -0.020 * cosd(2 * mj - 4 * ms - 2) + 0.018 * sind(2 * mj - 6 * ms - 49)
            }
            else -> {}
        }
        val xh = h.r * cosd(lon) * cosd(lat)
        val yh = h.r * sind(lon) * cosd(lat)
        val zh = h.r * sind(lat)
        return horizontal(xh + sx, yh + sy, zh, d, latDeg, lonDeg)
    }

    /** Mondlaenge/-breite (geozentrisch, Grad) und Abstand in Erdradien. */
    private fun moonEcliptic(d: Double): Triple<Double, Double, Double> {
        val n = 125.1228 - 0.0529538083 * d
        val i = 5.1454
        val w = 318.0634 + 0.1643573223 * d
        val a = 60.2666
        val e = 0.054900
        val mm = rev(115.3654 + 13.0649929509 * d)
        val ea = kepler(mm, e)
        val xv = a * (cosd(ea) - e)
        val yv = a * sqrt(1 - e * e) * sind(ea)
        val v = deg(atan2(yv, xv))
        var r = sqrt(xv * xv + yv * yv)
        val vw = v + w
        val xh = r * (cosd(n) * cosd(vw) - sind(n) * sind(vw) * cosd(i))
        val yh = r * (sind(n) * cosd(vw) + cosd(n) * sind(vw) * cosd(i))
        val zh = r * sind(vw) * sind(i)
        var lon = rev(deg(atan2(yh, xh)))
        var lat = deg(atan2(zh, sqrt(xh * xh + yh * yh)))
        val ms = rev(356.0470 + 0.9856002585 * d)
        val ls = rev(282.9404 + 4.70935E-5 * d + ms)
        val lm = rev(mm + w + n)
        val dd = rev(lm - ls)
        val f = rev(lm - n)
        lon += -1.274 * sind(mm - 2 * dd) + 0.658 * sind(2 * dd) - 0.186 * sind(ms) - 0.059 * sind(2 * mm - 2 * dd) -
            0.057 * sind(mm - 2 * dd + ms) + 0.053 * sind(mm + 2 * dd) + 0.046 * sind(2 * dd - ms) + 0.041 * sind(mm - ms) -
            0.035 * sind(dd) - 0.031 * sind(mm + ms) - 0.015 * sind(2 * f - 2 * dd) + 0.011 * sind(mm - 4 * dd)
        lat += -0.173 * sind(f - 2 * dd) - 0.055 * sind(mm - f - 2 * dd) - 0.046 * sind(mm + f - 2 * dd) +
            0.033 * sind(f + 2 * dd) + 0.017 * sind(2 * mm + f)
        r += -0.58 * cosd(mm - 2 * dd) - 0.46 * cosd(2 * dd)
        return Triple(rev(lon), lat, r)
    }

    fun moon(epochSec: Long, latDeg: Double, lonDeg: Double): MoonInfo {
        val d = dayNumber(epochSec)
        val (lon, lat, r) = moonEcliptic(d)
        val sunLon = sunEcliptic(d).third
        val elong = rev(lon - sunLon)
        val cycle = elong / 360.0
        val xe = r * cosd(lon) * cosd(lat)
        val ye = r * sind(lon) * cosd(lat)
        val ze = r * sind(lat)
        val geo = horizontal(xe, ye, ze, d, latDeg, lonDeg)
        // Parallaxe: Mond ist r Erdradien entfernt
        val alt = geo.altitudeDeg - deg(asin(1.0 / r)) * cosd(geo.altitudeDeg)
        return MoonInfo(cycle, (1 - cosd(elong)) / 2, phaseKey(cycle), SkyPosition(alt, geo.azimuthDeg))
    }

    /** Phase 0..1 (0/1 = Neu) -> Name-Key, 8 gleich breite Stufen um die Hauptphasen. */
    fun phaseKey(cycle: Double): String = PHASE_KEYS[floor(cycle.mod(1.0) * 8 + 0.5).toInt() % 8]

    /** Sichtbar jetzt: Himmel dunkel (Sonne unter -6 Grad) und Objekt hoeher als 5 Grad. Sortiert nach Hoehe. */
    fun visibleNow(epochSec: Long, latDeg: Double, lonDeg: Double): List<TonightEntry> {
        if (sun(epochSec, latDeg, lonDeg).altitudeDeg >= DARK_SUN_ALTITUDE) return emptyList()
        return bodiesAt(epochSec, latDeg, lonDeg).filter { it.position.altitudeDeg > MIN_ALTITUDE }
            .sortedByDescending { it.position.altitudeDeg }
    }

    private fun bodiesAt(epochSec: Long, latDeg: Double, lonDeg: Double): List<TonightEntry> {
        val m = moon(epochSec, latDeg, lonDeg)
        return listOf(TonightEntry(m.phaseKey, epochSec, m.position)) +
            Planet.entries.map { TonightEntry(it.key, epochSec, planet(it, epochSec, latDeg, lonDeg)) }
    }

    /**
     * "Was ist heute Abend sichtbar?": ab [fromEpochSec] bis zu 36 h voraus den ersten Dunkelheitsbeginn suchen und
     * dann in 30-Minuten-Schritten die naechsten 6 h (solange dunkel) pruefen. Je Objekt der Zeitpunkt mit der groessten
     * Hoehe (> 5 Grad), sortiert nach Hoehe. Leer bei Polartag.
     */
    fun tonight(fromEpochSec: Long, latDeg: Double, lonDeg: Double): List<TonightEntry> {
        var start: Long? = null
        var t = fromEpochSec
        while (t <= fromEpochSec + 36 * 3600) {
            if (sun(t, latDeg, lonDeg).altitudeDeg < DARK_SUN_ALTITUDE) { start = t; break }
            t += 600
        }
        val s = start ?: return emptyList()
        val best = LinkedHashMap<String, TonightEntry>()
        var u = s
        while (u <= s + 6 * 3600 && sun(u, latDeg, lonDeg).altitudeDeg < DARK_SUN_ALTITUDE) {
            for (e in bodiesAt(u, latDeg, lonDeg)) {
                val k = if (e.key.startsWith("moon_")) "moon" else e.key
                val cur = best[k]
                if (e.position.altitudeDeg > MIN_ALTITUDE && (cur == null || e.position.altitudeDeg > cur.position.altitudeDeg)) best[k] = e
            }
            u += 1800
        }
        return best.values.sortedByDescending { it.position.altitudeDeg }
    }

    /** Kleinster Winkelabstand zweier Azimute in Grad (Hilfsfunktion fuer Tests/Anzeige). */
    fun azimuthDiff(a: Double, b: Double): Double = abs((a - b + 540.0).mod(360.0) - 180.0)
}
