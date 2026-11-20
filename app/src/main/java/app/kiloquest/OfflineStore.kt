package app.kiloquest

import android.content.Context
import app.kiloquest.nav.OfflinePack
import app.kiloquest.nav.OfflinePackCodec
import app.kiloquest.nav.OfflinePackMeta
import java.io.File

/** Speichert Offline-Pakete als Dateien im App-Speicher (`offline-packs/<id>.json` plus kleine `.meta.json`). */
class OfflineStore(context: Context) {
    private val dir = File(context.filesDir, "offline-packs").apply { mkdirs() }

    private fun safe(id: String) = id.takeIf { Regex("[A-Za-z0-9-]{1,64}").matches(it) }

    fun save(p: OfflinePack) {
        val id = safe(p.id) ?: return
        // Erst in eine Temp-Datei, dann umbenennen: ein Abbruch hinterlaesst kein halbes Paket
        val data = File(dir, "$id.json.tmp").also { it.writeText(OfflinePackCodec.encode(p), Charsets.UTF_8) }
        data.renameTo(File(dir, "$id.json"))
        File(dir, "$id.meta.json").writeText(OfflinePackCodec.encodeMeta(OfflinePackCodec.meta(p)), Charsets.UTF_8)
    }

    fun list(): List<OfflinePackMeta> =
        dir.listFiles { f -> f.name.endsWith(".meta.json") }.orEmpty()
            .mapNotNull { OfflinePackCodec.decodeMeta(runCatching { it.readText(Charsets.UTF_8) }.getOrNull()) }
            .filter { File(dir, "${it.id}.json").exists() }
            .sortedByDescending { it.createdDay }

    fun load(id: String): OfflinePack? {
        val s = safe(id) ?: return null
        val f = File(dir, "$s.json")
        return if (f.exists()) OfflinePackCodec.decode(runCatching { f.readText(Charsets.UTF_8) }.getOrNull()) else null
    }

    fun delete(id: String) {
        val s = safe(id) ?: return
        File(dir, "$s.json").delete()
        File(dir, "$s.meta.json").delete()
    }
}
