package app.kiloquest.nav

enum class PowerSaverMode { AUTO, ON, OFF }

/** Was der Sparmodus aendert: Faktor fuer Abfrage-Abstaende, Dauerzuhoeren und Foto-Bitten. */
data class PowerEffects(val saving: Boolean, val pollFactor: Int, val allowWakeListening: Boolean, val allowPhotoPrompts: Boolean)

/**
 * Akku-Sparmodus: im Auto-Modus spart die App ab [lowPercent] (ohne Laden) oder wenn Android selbst den Energiesparmodus an hat.
 * Gespart wird bei Netzabfragen (laengere Abstaende), beim Mikrofon-Dauerzuhoeren und bei Foto-Bitten; Navigation und Ansagen bleiben unveraendert.
 */
object PowerPolicy {
    const val LOW_PERCENT = 20

    fun saving(mode: PowerSaverMode, batteryPercent: Int?, charging: Boolean, systemPowerSave: Boolean): Boolean = when (mode) {
        PowerSaverMode.ON -> true
        PowerSaverMode.OFF -> false
        PowerSaverMode.AUTO -> systemPowerSave || (!charging && batteryPercent != null && batteryPercent in 0 until LOW_PERCENT)
    }

    fun effects(saving: Boolean): PowerEffects =
        if (saving) PowerEffects(true, pollFactor = 3, allowWakeListening = false, allowPhotoPrompts = false) else PowerEffects(false, 1, true, true)
}

/** Speicherbelegung der App in Bytes je Bereich; [warn] sagt, ob ein Hinweis angezeigt werden soll. */
data class StorageUsage(val photosBytes: Long, val offlineBytes: Long, val cacheBytes: Long) {
    val totalBytes: Long get() = photosBytes + offlineBytes + cacheBytes
    val warn: Boolean get() = photosBytes > WARN_PHOTOS_BYTES || offlineBytes > WARN_OFFLINE_BYTES

    companion object {
        const val WARN_PHOTOS_BYTES = 300L * 1024 * 1024
        const val WARN_OFFLINE_BYTES = 1500L * 1024 * 1024

        /** Groesse fuer die Anzeige: unter 1 MB in KB, sonst MB mit einer Nachkommastelle (unter 10 MB), sonst ganze MB, ab 1 GB in GB. */
        fun format(bytes: Long): String = when {
            bytes < 1024 * 1024 -> "${bytes / 1024} KB"
            bytes < 10L * 1024 * 1024 -> String.format(java.util.Locale.ROOT, "%.1f MB", bytes / 1048576.0)
            bytes < 1024L * 1024 * 1024 -> "${bytes / 1048576} MB"
            else -> String.format(java.util.Locale.ROOT, "%.1f GB", bytes / 1073741824.0)
        }
    }
}
