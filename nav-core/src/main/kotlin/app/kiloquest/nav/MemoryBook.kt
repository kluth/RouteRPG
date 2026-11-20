package app.kiloquest.nav

import kotlin.math.cos

/** Ein Tag im Erinnerungsbuch: Textzeilen (ohne Fotos) und Foto-Dateinamen in Reihenfolge. */
data class BookDay(val day: Int, val entries: List<DiaryEntry>, val photoNames: List<String>)

/**
 * Reine Logik fuer das Erinnerungsbuch (PDF am Reiseende): Tage gruppieren, Fotos seitenweise, Route aus den Tagebuch-Positionen skizzieren.
 * Gezeichnet wird in der App; hier nichts mit Android.
 */
object MemoryBook {
    fun days(diary: List<DiaryEntry>): List<BookDay> = TravelDiary.byDay(diary).map { (day, list) ->
        BookDay(day, list.filter { it.kindOrNull != DiaryKind.PHOTO }, list.filter { it.kindOrNull == DiaryKind.PHOTO }.flatMap { it.photoFiles() })
    }.filter { it.entries.isNotEmpty() || it.photoNames.isNotEmpty() }

    /** Fotos in Seiten zu je [perPage] (Reihenfolge bleibt). */
    fun photoPages(names: List<String>, perPage: Int = 4): List<List<String>> = names.chunked(perPage.coerceAtLeast(1))

    /** Positionen der Eintraege in zeitlicher Reihenfolge, aufeinanderfolgende Doppelte entfernt, auf hoechstens [max] Punkte ausgeduennt. */
    fun routePoints(diary: List<DiaryEntry>, max: Int = 200): List<LatLon> {
        val pts = diary.sortedBy { it.atMs }.mapNotNull { e -> if (e.lat != null && e.lon != null) LatLon(e.lat!!, e.lon!!) else null }
        val dedup = ArrayList<LatLon>()
        for (p in pts) if (dedup.lastOrNull() != p) dedup += p
        if (dedup.size <= max) return dedup
        val step = dedup.size.toDouble() / max
        return (0 until max).map { dedup[(it * step).toInt()] } + dedup.last()
    }

    /** Punkte in ein Rechteck [width] x [height] (mit [pad] Rand) abbilden, Seitenverhaeltnis erhalten, Norden oben. Leer bei weniger als 2 Punkten. */
    fun project(points: List<LatLon>, width: Float, height: Float, pad: Float = 16f): List<Pair<Float, Float>> {
        if (points.size < 2) return emptyList()
        val midLat = (points.minOf { it.lat } + points.maxOf { it.lat }) / 2
        val k = cos(Math.toRadians(midLat)).coerceAtLeast(0.2)
        val xs = points.map { it.lon * k }
        val minX = xs.min(); val maxX = xs.max()
        val minY = points.minOf { it.lat }; val maxY = points.maxOf { it.lat }
        val w = (maxX - minX).coerceAtLeast(1e-6); val h = (maxY - minY).coerceAtLeast(1e-6)
        val scale = minOf((width - 2 * pad) / w, (height - 2 * pad) / h)
        val offX = (width - w * scale) / 2; val offY = (height - h * scale) / 2
        return points.indices.map { i -> (offX + (xs[i] - minX) * scale).toFloat() to (offY + (maxY - points[i].lat) * scale).toFloat() }
    }
}
