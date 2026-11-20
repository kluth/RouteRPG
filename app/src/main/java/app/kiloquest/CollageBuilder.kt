package app.kiloquest

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import app.kiloquest.nav.CollageLayout
import app.kiloquest.nav.DiaryEntry
import java.io.File
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/**
 * Baut aus den Foto-Momenten eines Tages Collagen (hoechstens 5 Zeilen je Bild): links die Aussicht, rechts das Selfie.
 * Die Bilder entstehen im Cache-Ordner und werden zum Teilen angeboten; nichts wird hochgeladen.
 */
object CollageBuilder {
    private const val WIDTH = 1080

    /** [entries] = Foto-Eintraege eines Tages (chronologisch). Gibt die erzeugten JPEG-Dateien zurueck (leer, wenn keine Fotos lesbar sind). */
    fun build(ctx: Context, day: Int, entries: List<DiaryEntry>): List<File> {
        val withPhotos = entries.filter { it.photoFiles().isNotEmpty() }
        val dateText = LocalDate.ofEpochDay(day.toLong()).format(DateTimeFormatter.ofLocalizedDate(FormatStyle.LONG))
        val dir = File(ctx.cacheDir, "shared").apply { mkdirs() }
        dir.listFiles { f -> f.name.startsWith("collage-") }?.forEach { it.delete() }
        val out = ArrayList<File>()
        for ((pageIndex, range) in CollageLayout.pages(withPhotos.size).withIndex()) {
            val page = withPhotos.subList(range.first, range.last + 1)
            val layout = CollageLayout.layout(page.size, WIDTH, headerHeight = 170)
            val bmp = Bitmap.createBitmap(layout.width, layout.height, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bmp)
            canvas.drawColor(Color.parseColor("#F4F1E8"))
            val title = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#1F2A36"); textSize = 64f; isFakeBoldText = true }
            val sub = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#5B6672"); textSize = 40f }
            canvas.drawText(ctx.getString(R.string.photo_collage_title), 24f, 84f, title)
            canvas.drawText(dateText, 24f, 140f, sub)
            val label = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE; textSize = 34f; isFakeBoldText = true }
            val band = Paint().apply { color = Color.argb(140, 0, 0, 0) }
            page.forEachIndexed { i, e ->
                val files = e.photoFiles()
                val row = layout.rows[i]
                listOf(files.getOrNull(0) to row.back, files.getOrNull(1) to row.front).forEach { (name, cell) ->
                    val dst = RectF(cell.x.toFloat(), cell.y.toFloat(), (cell.x + cell.w).toFloat(), (cell.y + cell.h).toFloat())
                    val photo = name?.let { PhotoStore.thumbnail(ctx, it, 900) }
                    if (photo != null) {
                        drawCenterCrop(canvas, photo, dst)
                        photo.recycle()
                    } else {
                        canvas.drawRect(dst, Paint().apply { color = Color.parseColor("#D9D4C5") })
                    }
                }
                if (e.title.isNotBlank()) {
                    val b = row.back
                    canvas.drawRect(RectF(b.x.toFloat(), (b.y + b.h - 56).toFloat(), (b.x + b.w).toFloat(), (b.y + b.h).toFloat()), band)
                    canvas.drawText(e.title.take(28), (b.x + 14).toFloat(), (b.y + b.h - 16).toFloat(), label)
                }
            }
            val name = "collage-%d-%d.jpg".format(day, pageIndex + 1)
            val f = File(dir, name)
            f.outputStream().use { bmp.compress(Bitmap.CompressFormat.JPEG, 90, it) }
            bmp.recycle()
            out += f
        }
        return out
    }

    private fun drawCenterCrop(canvas: Canvas, src: Bitmap, dst: RectF) {
        val srcRatio = src.width.toFloat() / src.height
        val dstRatio = dst.width() / dst.height()
        val crop = if (srcRatio > dstRatio) {
            val w = (src.height * dstRatio).toInt()
            Rect((src.width - w) / 2, 0, (src.width + w) / 2, src.height)
        } else {
            val h = (src.width / dstRatio).toInt()
            Rect(0, (src.height - h) / 2, src.width, (src.height + h) / 2)
        }
        canvas.drawBitmap(src, crop, dst, Paint(Paint.FILTER_BITMAP_FLAG))
    }
}
