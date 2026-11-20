package app.kiloquest

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.os.Build
import android.provider.MediaStore
import java.io.File

/**
 * Speicher der Roadtrip-Fotos: nur im privaten App-Speicher (`filesDir/photos`), auf Wunsch zusaetzlich eine Kopie in der Galerie.
 * Dateinamen werden nie aus Fremddaten gebildet; gespeichert werden nur Namen (keine Pfade).
 */
object PhotoStore {
    private const val MAX_EDGE = 1600
    private const val QUALITY = 88

    fun dir(ctx: Context): File = File(ctx.filesDir, "photos").apply { mkdirs() }

    private fun safe(name: String) = name.takeIf { Regex("[A-Za-z0-9._-]{1,80}").matches(it) && ".." !in it }

    fun file(ctx: Context, name: String): File? = safe(name)?.let { File(dir(ctx), it) }.takeIf { it?.exists() == true }

    /** Verkleinert ein rohes Kamerabild (laengste Kante 1600 px), dreht es nach EXIF und legt es unter [name] ab; das Original wird geloescht. */
    fun processAndStore(ctx: Context, raw: File, name: String): File? {
        val target = safe(name)?.let { File(dir(ctx), it) } ?: return null
        return runCatching {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(raw.path, bounds)
            var sample = 1
            while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= MAX_EDGE) sample *= 2
            val opts = BitmapFactory.Options().apply { inSampleSize = sample }
            val decoded = BitmapFactory.decodeFile(raw.path, opts) ?: return@runCatching null
            val degrees = when (ExifInterface(raw.path).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
                ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                else -> 0f
            }
            val scale = MAX_EDGE.toFloat() / maxOf(decoded.width, decoded.height)
            val m = Matrix().apply {
                if (degrees != 0f) postRotate(degrees)
                if (scale < 1f) postScale(scale, scale)
            }
            val out = if (m.isIdentity) decoded else Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, m, true)
            target.outputStream().use { out.compress(Bitmap.CompressFormat.JPEG, QUALITY, it) }
            if (out !== decoded) decoded.recycle()
            out.recycle()
            target
        }.getOrNull().also { raw.delete() }
    }

    /** Kleine Vorschau fuer das Tagebuch (laengste Kante hoechstens [maxPx]). */
    fun thumbnail(ctx: Context, name: String, maxPx: Int = 360): Bitmap? {
        val f = file(ctx, name) ?: return null
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(f.path, bounds)
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= maxPx) sample *= 2
        return BitmapFactory.decodeFile(f.path, BitmapFactory.Options().apply { inSampleSize = sample })
    }

    /** Kopie in die Galerie (Pictures/KiloQuest), nur ab Android 10 ohne zusaetzliche Berechtigung. Gibt true bei Erfolg zurueck. */
    fun copyToGallery(ctx: Context, file: File, displayName: String): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return false
        return runCatching {
            val values = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, displayName)
                put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/KiloQuest")
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }
            val resolver = ctx.contentResolver
            val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) ?: return@runCatching false
            resolver.openOutputStream(uri)?.use { out -> file.inputStream().use { it.copyTo(out) } }
            resolver.update(uri, ContentValues().apply { put(MediaStore.Images.Media.IS_PENDING, 0) }, null, null)
            true
        }.getOrDefault(false)
    }

    /** Alle Fotos loeschen (Tagebuch loeschen, Datenschutz). */
    fun deleteAll(ctx: Context) {
        dir(ctx).listFiles()?.forEach { it.delete() }
    }
}
