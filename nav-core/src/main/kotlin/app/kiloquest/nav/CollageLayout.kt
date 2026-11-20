package app.kiloquest.nav

data class CollageCell(val x: Int, val y: Int, val w: Int, val h: Int)

/** Eine Foto-Zeile: Ruecklichtkamera links, Frontkamera rechts. */
data class CollageRow(val back: CollageCell, val front: CollageCell)

data class CollageLayoutResult(val width: Int, val height: Int, val headerHeight: Int, val rows: List<CollageRow>)

/**
 * Raster der Tages-Collage: pro Foto-Moment eine Zeile, links Aussicht (Rueckkamera), rechts Selfie (Frontkamera), beide im selben Format.
 * Reine Geometrie; das Zeichnen passiert in der App.
 */
object CollageLayout {
    const val MAX_ROWS_PER_PAGE = 5

    /** Teilt [count] Foto-Momente in Seiten zu hoechstens [perPage] Zeilen (Collagen bleiben gut teilbar). */
    fun pages(count: Int, perPage: Int = MAX_ROWS_PER_PAGE): List<IntRange> =
        if (count <= 0) emptyList() else (0 until count step perPage).map { it until minOf(count, it + perPage) }

    /** [aspect] = Breite/Hoehe der Zellen (Standard 4:3). Zwischen Zellen und Zeilen liegt [gap] Pixel, aussen ebenfalls. */
    fun layout(rows: Int, width: Int, headerHeight: Int, gap: Int = 12, aspect: Double = 4.0 / 3.0): CollageLayoutResult {
        require(rows >= 0 && width > 0 && gap >= 0 && aspect > 0)
        val cellW = (width - 3 * gap) / 2
        val cellH = (cellW / aspect).toInt()
        val list = (0 until rows).map { r ->
            val y = headerHeight + gap + r * (cellH + gap)
            CollageRow(CollageCell(gap, y, cellW, cellH), CollageCell(2 * gap + cellW, y, cellW, cellH))
        }
        val height = headerHeight + gap + rows * (cellH + gap)
        return CollageLayoutResult(width, height, headerHeight, list)
    }
}
