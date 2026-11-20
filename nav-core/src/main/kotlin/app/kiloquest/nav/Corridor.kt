package app.kiloquest.nav

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.tan

data class GeoBox(val south: Double, val west: Double, val north: Double, val east: Double)

/**
 * Korridor um die Route fuer das Offline-"Reise-Paket": die Route wird in Abschnitte geteilt, je Abschnitt
 * eine Box (Kartenkacheln werden boxweise geladen, ein ganzer Streckenbogen waere viel zu gross).
 */
object Corridor {
    /** Boxen je [chunkMeters] Strecke, rundum um [padKm] erweitert. */
    fun boxes(route: Route, chunkMeters: Double = 60_000.0, padKm: Double = 12.0): List<GeoBox> {
        val pts = route.line.points
        val cum = route.line.cumulative
        val result = ArrayList<GeoBox>()
        var start = 0
        while (start < pts.size - 1) {
            var end = start + 1
            while (end < pts.size - 1 && cum[end] - cum[start] < chunkMeters) end++
            result += boxOf(pts.subList(start, end + 1), padKm)
            start = end
        }
        return result
    }

    private fun boxOf(points: List<LatLon>, padKm: Double): GeoBox {
        val south = points.minOf { it.lat }
        val north = points.maxOf { it.lat }
        val west = points.minOf { it.lon }
        val east = points.maxOf { it.lon }
        val padLat = padKm / 111.195
        val padLon = padKm / (111.195 * cos(((south + north) / 2) * PI / 180.0).coerceAtLeast(0.05))
        return GeoBox(
            (south - padLat).coerceAtLeast(-85.0), (west - padLon).coerceAtLeast(-180.0),
            (north + padLat).coerceAtMost(85.0), (east + padLon).coerceAtMost(180.0),
        )
    }

    private fun tileX(lon: Double, z: Int) = floor((lon + 180.0) / 360.0 * (1 shl z)).toInt().coerceIn(0, (1 shl z) - 1)
    private fun tileY(lat: Double, z: Int): Int {
        val r = lat * PI / 180.0
        return floor((1.0 - ln(tan(r) + 1.0 / cos(r)) / PI) / 2.0 * (1 shl z)).toInt().coerceIn(0, (1 shl z) - 1)
    }

    /** Anzahl Kacheln der Box auf Zoomstufe [z] (Web-Mercator). */
    fun tilesAt(box: GeoBox, z: Int): Long {
        val x = tileX(box.east, z) - tileX(box.west, z) + 1
        val y = tileY(box.south, z) - tileY(box.north, z) + 1
        return x.toLong() * y.toLong()
    }

    fun estimateTiles(boxes: List<GeoBox>, minZoom: Int, maxZoom: Int): Long =
        boxes.sumOf { b -> (minZoom..maxZoom).sumOf { z -> tilesAt(b, z) } }

    /** Grobe Groesse: Vektorkacheln im Mittel ca. [kbPerTile] KB. */
    fun estimateMegabytes(tiles: Long, kbPerTile: Double = 18.0): Double = tiles * kbPerTile / 1024.0

    /** Streckenmeter-Stuetzpunkte fuer Laendererkennung vorab (alle [stepMeters]). */
    fun samplePositions(route: Route, stepMeters: Double = 25_000.0): List<Pair<Double, LatLon>> {
        val total = route.line.lengthMeters
        val result = ArrayList<Pair<Double, LatLon>>()
        var m = 0.0
        while (m < total) {
            result += m to route.line.points[route.line.indexAtMeters(m)]
            m += stepMeters
        }
        result += total to route.line.points.last()
        return result
    }
}

/** Land je Streckenabschnitt, vorab ermittelt; fuer die Laendererkennung ohne Netz. */
class CountryTimeline(private val samples: List<Pair<Double, String>>) {
    /** Land an der Streckenposition (letzte Probe davor), null ohne Daten. */
    fun countryAt(alongMeters: Double): String? {
        var found: String? = null
        for ((m, c) in samples) { if (m <= alongMeters) found = c else break }
        return found ?: samples.firstOrNull()?.second
    }
    val isEmpty get() = samples.isEmpty()
}
