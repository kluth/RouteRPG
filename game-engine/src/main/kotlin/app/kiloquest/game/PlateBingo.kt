package app.kiloquest.game

import kotlin.random.Random

/**
 * Laender-Unterscheidungszeichen fuer Kennzeichen-Bingo. Reine Schluessel + Flagge, keine Texte:
 * der Laendername kommt ueber den String-Key [nameKey] (`country_<code>`).
 */
data class PlateCountry(val code: String, val iso2: String) {
    val nameKey: String get() = "country_${code.lowercase()}"

    /** Flaggen-Emoji aus den zwei Regionalindikator-Zeichen des ISO-Codes. */
    val flag: String get() = iso2.uppercase().map { String(Character.toChars(0x1F1E6 + (it - 'A'))) }.joinToString("")
}

object PlateCountries {
    /** Haeufig auf europaeischen Strassen (Reihenfolge = Haeufigkeit); daraus werden Bingo-Karten gezogen. */
    val COMMON: List<PlateCountry> = listOf(
        "D" to "DE", "A" to "AT", "CH" to "CH", "F" to "FR", "I" to "IT", "NL" to "NL", "B" to "BE", "L" to "LU",
        "DK" to "DK", "PL" to "PL", "CZ" to "CZ", "E" to "ES", "S" to "SE", "N" to "NO", "FIN" to "FI", "H" to "HU",
        "SK" to "SK", "SLO" to "SI", "HR" to "HR", "P" to "PT", "GB" to "GB", "IRL" to "IE", "RO" to "RO", "BG" to "BG",
    ).map { PlateCountry(it.first, it.second) }

    /** Seltener: zaehlen nur fuer die Sammlung. */
    val RARE: List<PlateCountry> = listOf(
        "FL" to "LI", "MC" to "MC", "GR" to "GR", "EST" to "EE", "LV" to "LV", "LT" to "LT", "IS" to "IS",
        "SRB" to "RS", "BIH" to "BA", "MNE" to "ME", "MK" to "MK", "AL" to "AL", "UA" to "UA", "TR" to "TR", "M" to "MT", "CY" to "CY",
    ).map { PlateCountry(it.first, it.second) }

    val ALL: List<PlateCountry> = COMMON + RARE
    private val byCode = ALL.associateBy { it.code }

    fun byCode(code: String): PlateCountry? = byCode[code.trim().uppercase()]
}

/** 3x3-Karte, Zellen zeilenweise 0..8. */
data class PlateBingoCard(val seed: Long, val cells: List<PlateCountry>) {
    companion object {
        const val SIZE = 3

        /** Deterministisch: gleicher Seed = gleiche Karte. */
        fun generate(seed: Long): PlateBingoCard =
            PlateBingoCard(seed, PlateCountries.COMMON.shuffled(Random(seed)).take(SIZE * SIZE))

        /** Alle 8 Gewinnlinien: 3 Reihen, 3 Spalten, 2 Diagonalen. */
        val LINES: List<List<Int>> = listOf(
            listOf(0, 1, 2), listOf(3, 4, 5), listOf(6, 7, 8),
            listOf(0, 3, 6), listOf(1, 4, 7), listOf(2, 5, 8),
            listOf(0, 4, 8), listOf(2, 4, 6),
        )
    }
}

/** Ergebnis von [PlateBingoGame.spot]. */
data class PlateSpot(val game: PlateBingoGame, val newCountry: Boolean, val onCard: Boolean, val newLines: Int, val points: Int)

/**
 * Zustand: Karte, abgehakte Zellen, Sammlung (alle je gesehenen Laender, ueber Spiele hinweg) und Punkte.
 * Unveraenderlich; [spot] liefert den neuen Zustand.
 */
data class PlateBingoGame(
    val card: PlateBingoCard,
    val marked: Set<Int> = emptySet(),
    val collection: Set<String> = emptySet(),
    val points: Int = 0,
) {
    val completedLines: List<List<Int>> get() = PlateBingoCard.LINES.filter { marked.containsAll(it) }
    val hasBingo: Boolean get() = completedLines.isNotEmpty()
    val isFull: Boolean get() = marked.size == card.cells.size

    /** Fortschritt der Sammlung: gesehen / alle bekannten Laender. */
    val collectionCount: Int get() = collection.size
    val collectionTotal: Int get() = PlateCountries.ALL.size
    val collectionProgress: Double get() = collectionCount.toDouble() / collectionTotal

    /** Kennzeichen gesehen. Unbekannte Codes bringen nichts; Wiederholungen nichts Neues. */
    fun spot(code: String): PlateSpot {
        val country = PlateCountries.byCode(code) ?: return PlateSpot(this, false, false, 0, 0)
        val isNew = country.code !in collection
        val idx = card.cells.indexOf(country)
        val newMark = idx >= 0 && idx !in marked
        val nowMarked = if (newMark) marked + idx else marked
        val linesBefore = completedLines.size
        val next = copy(marked = nowMarked, collection = collection + country.code)
        val newLines = next.completedLines.size - linesBefore
        var pts = 0
        if (isNew) pts += POINTS_NEW_COUNTRY
        if (newMark) pts += POINTS_CARD_CELL
        pts += newLines * POINTS_LINE
        if (newMark && next.isFull) pts += POINTS_FULL
        return PlateSpot(next.copy(points = points + pts), isNew, idx >= 0, newLines, pts)
    }

    /** Neue Karte, Sammlung und Punkte bleiben. */
    fun nextCard(seed: Long) = copy(card = PlateBingoCard.generate(seed), marked = emptySet())

    companion object {
        const val POINTS_NEW_COUNTRY = 5
        const val POINTS_CARD_CELL = 3
        const val POINTS_LINE = 10
        const val POINTS_FULL = 30

        fun start(seed: Long, collection: Set<String> = emptySet()) = PlateBingoGame(PlateBingoCard.generate(seed), collection = collection)
    }
}
