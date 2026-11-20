package app.kiloquest.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.util.LruCache
import app.kiloquest.game.Avatars
import app.kiloquest.game.Model3D
import app.kiloquest.game.ModelKind

/**
 * Vorgerechnete Bilder einer 3D-Figur (Kenney, CC0; siehe docs/licenses/BILDQUELLEN.md): [grid] ist nach Richtung und
 * Gehzyklus-Bild geordnet (`grid[richtung][bild]`). Fahrzeuge haben 36 Richtungen mit einem Bild, Tiere und Personen 24
 * Richtungen mit 6 Bildern Gehzyklus.
 */
class FigureSheet(val model: Model3D, private val grid: List<List<Bitmap>>) {
    val headings: Int get() = model.headings
    val frames: Int get() = model.walkFrames
    fun heading(headingDegrees: Float?): Int = Avatars.headingIndex(headingDegrees, headings)
    fun bitmap(headingIndex: Int, frame: Int): Bitmap = grid[headingIndex.mod(headings)][frame.mod(frames)]
    fun bitmap(headingDegrees: Float?, frame: Int = 0): Bitmap = bitmap(heading(headingDegrees), frame)
    fun all(): List<List<Bitmap>> = grid
}

object Figures3D {
    private val cache = object : LruCache<String, FigureSheet>(2) {}

    private fun read(ctx: Context, name: String): Bitmap =
        ctx.assets.open("figures3d/$name").use { BitmapFactory.decodeStream(it) }

    private fun mirror(b: Bitmap): Bitmap = Bitmap.createBitmap(b, 0, 0, b.width, b.height, Matrix().apply { preScale(-1f, 1f) }, false)

    /** Alle Bilder der Figur zu [emoji] oder null, wenn es kein 3D-Modell dafuer gibt (dann bleibt es ein Emoji). */
    fun sheet(ctx: Context, emoji: String): FigureSheet? {
        val model = Avatars.model3d(emoji) ?: return null
        cache.get(model.id)?.let { return it }
        val grid = runCatching {
            if (model.kind == ModelKind.VEHICLE) {
                (0 until model.headings).map { h -> listOf(read(ctx, "${model.id}_${h.toString().padStart(2, '0')}.webp")) }
            } else {
                // gespeichert: Norden bis Sueden ueber Osten (13 Richtungen); die Westhaelfte ist die gespiegelte Osthaelfte
                val half = (0..model.headings / 2).map { h -> (0 until model.walkFrames).map { w -> read(ctx, "${model.id}_h${h.toString().padStart(2, '0')}_w$w.webp") } }
                (0 until model.headings).map { h -> if (h <= model.headings / 2) half[h] else half[model.headings - h].map(::mirror) }
            }
        }.getOrNull() ?: return null
        return FigureSheet(model, grid).also { cache.put(model.id, it) }
    }

    /** Ein einzelnes Bild fuer Auswahl-Kacheln: Fahrzeuge von der Seite (Fahrt nach Osten), Tiere und Personen von vorn im Stand. */
    fun preview(ctx: Context, emoji: String): Bitmap? = sheet(ctx, emoji)?.let {
        if (it.model.kind == ModelKind.WALKER) it.bitmap(180f, 0) else it.bitmap(null, 0)
    }
}
