package app.kiloquest

import android.content.Context
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.pdf.PdfDocument
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import app.kiloquest.nav.DiaryEntry
import app.kiloquest.nav.MemoryBook
import app.kiloquest.nav.TravelDiary
import java.io.File
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/**
 * Erinnerungsbuch als PDF (A4): Titel, je Tag Erlebnisse und Fotos, Routenskizze aus den Tagebuch-Positionen, Zusammenfassung.
 * Entsteht komplett auf dem Geraet im Cache-Ordner und wird nur auf Wunsch geteilt. Es werden keine Kartenkacheln benutzt
 * (die Skizze ist unsere eigene Linie), deshalb ist kein Kartenhinweis noetig.
 */
object MemoryBookBuilder {
    private const val W = 595
    private const val H = 842
    private const val M = 40f

    private class Writer(private val ctx: Context) {
        val pdf = PdfDocument()
        private var page: PdfDocument.Page? = null
        private var number = 0
        var y = M
        val canvas get() = page!!.canvas
        val body = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#1F2A36"); textSize = 12f }
        val head = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#1F2A36"); textSize = 18f; isFakeBoldText = true }
        val big = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#1F2A36"); textSize = 34f; isFakeBoldText = true }
        val foot = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#8A94A0"); textSize = 9f }

        fun newPage() {
            finishPage()
            number++
            page = pdf.startPage(PdfDocument.PageInfo.Builder(W, H, number).create())
            canvas.drawColor(Color.parseColor("#F4F1E8"))
            y = M
        }

        fun finishPage() {
            page?.let {
                it.canvas.drawText(ctx.getString(R.string.memory_footer, number), M, H - 18f, foot)
                pdf.finishPage(it)
            }
            page = null
        }

        /** Absatz mit Zeilenumbruch; beginnt eine neue Seite, wenn der Platz nicht reicht. */
        fun paragraph(text: String, paint: TextPaint = body, gap: Float = 6f) {
            val layout = StaticLayout.Builder.obtain(text, 0, text.length, paint, (W - 2 * M).toInt()).setAlignment(Layout.Alignment.ALIGN_NORMAL).build()
            if (y + layout.height > H - 50f) newPage()
            canvas.save(); canvas.translate(M, y); layout.draw(canvas); canvas.restore()
            y += layout.height + gap
        }
    }

    private fun dayText(day: Int) = LocalDate.ofEpochDay(day.toLong()).format(DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL))

    /** Baut das Buch; gibt die PDF-Datei zurueck (null, wenn das Tagebuch leer ist). */
    fun build(ctx: Context, diary: List<DiaryEntry>): File? {
        if (diary.isEmpty()) return null
        val w = Writer(ctx)
        val st = TravelDiary.stats(diary)
        val days = MemoryBook.days(diary)

        // Titelseite
        w.newPage()
        w.y = 220f
        w.paragraph(ctx.getString(R.string.memory_title), w.big, 16f)
        if (days.isNotEmpty()) w.paragraph(dayText(days.first().day) + if (days.size > 1) " – " + dayText(days.last().day) else "", w.head, 12f)
        w.paragraph(ctx.getString(R.string.diary_stats_trip, st.days, ctx.getString(R.string.unit_km, st.meters / 1000.0), st.countries.size), w.body)
        if (st.countries.isNotEmpty()) w.paragraph(st.countries.joinToString(" ") { TravelDiary.flag(it) }, w.head)

        // Tage
        for (d in days) {
            w.newPage()
            w.paragraph(dayText(d.day), w.head, 10f)
            d.entries.forEach { w.paragraph("• " + diaryLine(ctx, it)) }
            for (names in MemoryBook.photoPages(d.photoNames, 4)) {
                w.newPage()
                w.paragraph(dayText(d.day), w.head, 10f)
                drawPhotos(ctx, w, names)
            }
        }

        // Routenskizze
        val pts = MemoryBook.project(MemoryBook.routePoints(diary), W - 2 * M, 420f, 24f)
        if (pts.size >= 2) {
            w.newPage()
            w.paragraph(ctx.getString(R.string.memory_route), w.head, 10f)
            val top = w.y
            val path = Path().apply { pts.forEachIndexed { i, (x, y) -> if (i == 0) moveTo(M + x, top + y) else lineTo(M + x, top + y) } }
            val line = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 3f; color = Color.parseColor("#2F7D5B"); strokeJoin = Paint.Join.ROUND }
            w.canvas.drawPath(path, line)
            val dot = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#D9534F") }
            w.canvas.drawCircle(M + pts.first().first, top + pts.first().second, 6f, Paint(dot).apply { color = Color.parseColor("#2F7D5B") })
            w.canvas.drawCircle(M + pts.last().first, top + pts.last().second, 6f, dot)
            w.y = top + 440f
            w.paragraph(ctx.getString(R.string.memory_route_note), w.foot)
        }

        // Zusammenfassung
        w.newPage()
        w.paragraph(ctx.getString(R.string.memory_summary), w.head, 12f)
        w.paragraph(ctx.getString(R.string.diary_stats_play, st.quests, st.points, st.stickers, st.stories))
        if (st.photos > 0) w.paragraph(ctx.getString(R.string.memory_photos, st.photos))
        w.paragraph(ctx.getString(R.string.memory_thanks), w.head)
        w.finishPage()

        val dir = File(ctx.cacheDir, "shared").apply { mkdirs() }
        val out = File(dir, "kiloquest-travel-book.pdf")
        out.outputStream().use { w.pdf.writeTo(it) }
        w.pdf.close()
        return out
    }

    /** Bis zu vier Fotos in einem 2x2-Raster (Mitte zugeschnitten). */
    private fun drawPhotos(ctx: Context, w: Writer, names: List<String>) {
        val cell = (W - 2 * M - 12f) / 2f
        val top = w.y
        names.forEachIndexed { i, name ->
            val bmp = PhotoStore.thumbnail(ctx, name, 700) ?: return@forEachIndexed
            val x = M + (i % 2) * (cell + 12f)
            val y = top + (i / 2) * (cell * 1.2f + 12f)
            val dst = android.graphics.RectF(x, y, x + cell, y + cell * 1.2f)
            val scale = maxOf(dst.width() / bmp.width, dst.height() / bmp.height)
            val sw = dst.width() / scale; val sh = dst.height() / scale
            val src = android.graphics.Rect(((bmp.width - sw) / 2).toInt(), ((bmp.height - sh) / 2).toInt(), ((bmp.width + sw) / 2).toInt(), ((bmp.height + sh) / 2).toInt())
            w.canvas.drawBitmap(bmp, src, dst, Paint(Paint.FILTER_BITMAP_FLAG))
        }
        w.y = top + 2 * (cell * 1.2f + 12f)
    }
}
