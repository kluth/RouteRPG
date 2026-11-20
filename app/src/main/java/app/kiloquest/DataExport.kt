package app.kiloquest

import android.content.Context
import app.kiloquest.nav.DiaryEntry
import app.kiloquest.nav.TravelDiary
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** Datenexport (DSGVO Art. 20): Tagebuch als JSON und alle Fotos in einer ZIP-Datei im Cache, zum Teilen. Nichts verlaesst das Geraet ohne Zutun. */
object DataExport {
    fun build(ctx: Context, diary: List<DiaryEntry>): File {
        val dir = File(ctx.cacheDir, "shared").apply { mkdirs() }
        val out = File(dir, "kiloquest-export.zip")
        ZipOutputStream(out.outputStream().buffered()).use { zip ->
            zip.putNextEntry(ZipEntry("diary.json"))
            zip.write(TravelDiary.encode(diary).toByteArray(Charsets.UTF_8))
            zip.closeEntry()
            PhotoStore.dir(ctx).listFiles()?.filter { it.isFile }?.forEach { f ->
                zip.putNextEntry(ZipEntry("photos/" + f.name))
                f.inputStream().use { it.copyTo(zip) }
                zip.closeEntry()
            }
        }
        return out
    }
}
