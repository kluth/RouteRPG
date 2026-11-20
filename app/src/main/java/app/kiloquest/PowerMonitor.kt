package app.kiloquest

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.PowerManager
import app.kiloquest.nav.PowerPolicy
import app.kiloquest.nav.PowerSaverMode
import app.kiloquest.nav.StorageUsage
import java.io.File

/** Liest Akkustand und Energiesparmodus (hoechstens alle 30 s) und fragt die [PowerPolicy]. Keine Daten verlassen das Geraet. */
class PowerMonitor(private val ctx: Context) {
    private var checkedAt = 0L
    private var percent: Int? = null
    private var charging = false
    private var systemSaver = false

    fun saving(mode: PowerSaverMode, nowMs: Long = System.currentTimeMillis()): Boolean {
        if (nowMs - checkedAt > 30_000L) {
            checkedAt = nowMs
            val i = ctx.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            val level = i?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
            val scale = i?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
            percent = if (level >= 0 && scale > 0) level * 100 / scale else null
            val status = i?.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
            charging = status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL
            systemSaver = (ctx.getSystemService(Context.POWER_SERVICE) as? PowerManager)?.isPowerSaveMode == true
        }
        return PowerPolicy.saving(mode, percent, charging, systemSaver)
    }
}

/** Speicherbelegung der App: Fotos, Offline-Pakete, temporaere Dateien (Teilen/Export). */
object StorageScanner {
    private fun size(f: File): Long = if (f.isFile) f.length() else f.listFiles()?.sumOf { size(it) } ?: 0L

    fun measure(ctx: Context) = StorageUsage(
        photosBytes = size(File(ctx.filesDir, "photos")),
        offlineBytes = size(File(ctx.filesDir, "offline-packs")),
        cacheBytes = size(File(ctx.cacheDir, "shared")),
    )

    /** Loescht nur Temporaeres (erzeugte Collagen, Exporte, Reisebuecher); Fotos und Pakete bleiben. */
    fun clearCache(ctx: Context) { File(ctx.cacheDir, "shared").listFiles()?.forEach { it.delete() } }
}
