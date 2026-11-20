package app.kiloquest.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import app.kiloquest.game.Avatars

/**
 * Drehbilder der 3D-Fahrzeuge (Kenney, CC0; siehe docs/licenses/BILDQUELLEN.md). 36 Bilder je Fahrzeug, eines je 10 Grad
 * Fahrtrichtung. Werden einmal aus den Assets gelesen und im Speicher gehalten (ein Fahrzeug etwa 5 MB).
 */
object Figures3D {
    private val cache = object : LruCache<String, List<Bitmap>>(3) {}

    /** Alle Drehbilder des Fahrzeugs zu [emoji] oder null, wenn es kein 3D-Modell dafuer gibt. */
    fun frames(ctx: Context, emoji: String): List<Bitmap>? {
        val id = Avatars.modelOf(emoji) ?: return null
        cache.get(id)?.let { return it }
        val list = runCatching {
            (0 until Avatars.FRAMES_3D).map { i ->
                ctx.assets.open("figures3d/${id}_${i.toString().padStart(2, '0')}.webp").use { BitmapFactory.decodeStream(it) }
            }
        }.getOrNull() ?: return null
        cache.put(id, list)
        return list
    }

    /** Ein einzelnes Bild (z. B. fuer Auswahl-Kacheln, Fahrt nach Osten). */
    fun preview(ctx: Context, emoji: String): Bitmap? = frames(ctx, emoji)?.getOrNull(Avatars.frameFor(null))
}
