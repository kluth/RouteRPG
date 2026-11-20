package app.kiloquest.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint

/**
 * Emoji als Bild (fuer Karten-Ebenen); die Emoji-Schrift des Geraets zeichnet es. [mirrored] spiegelt links/rechts
 * (Fahrzeuge in Seitenansicht schauen dann nach rechts). [disc] legt einen hellen Kreis dahinter, damit die Figur auf jeder
 * Karte zu sehen ist.
 */
fun emojiBitmap(emoji: String, px: Int, mirrored: Boolean = false, disc: Boolean = false): Bitmap {
    val bmp = Bitmap.createBitmap(px, px, Bitmap.Config.ARGB_8888)
    val c = Canvas(bmp)
    if (disc) c.drawCircle(px / 2f, px / 2f, px / 2f - 2f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xE6FFFFFF.toInt() })
    val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = px * 0.62f; textAlign = Paint.Align.CENTER }
    val fm = p.fontMetrics
    if (mirrored) c.scale(-1f, 1f, px / 2f, px / 2f)
    c.drawText(emoji, px / 2f, px / 2f - (fm.ascent + fm.descent) / 2, p)
    return bmp
}
