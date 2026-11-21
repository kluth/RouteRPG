package app.kiloquest

import android.Manifest
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat

enum class CheckLevel { OK, WARN, FAIL }

/** Ein Punkt des Selbsttests: Titel, Hinweis (was tun) und Ergebnis. Hinweise stehen in den Strings, nie hier. */
data class SelfCheck(val titleRes: Int, val hintRes: Int, val level: CheckLevel)

/** Selbsttest: zeigt Eltern, was auf diesem Geraet noch fehlt (Berechtigungen, Bluetooth, Stimme, Karten, Server). Nichts verlaesst das Geraet. */
object SelfTest {
    private fun granted(ctx: Context, perm: String) = ContextCompat.checkSelfPermission(ctx, perm) == PackageManager.PERMISSION_GRANTED

    fun run(ctx: Context, state: UiState, tokenSet: Boolean = ServerConfig.token.isNotBlank()): List<SelfCheck> = buildList {
        add(check(R.string.st_server, R.string.st_server_hint, tokenSet, CheckLevel.FAIL))
        add(check(R.string.st_location, R.string.st_location_hint, granted(ctx, Manifest.permission.ACCESS_FINE_LOCATION), CheckLevel.FAIL))
        add(check(R.string.st_mic, R.string.st_mic_hint, granted(ctx, Manifest.permission.RECORD_AUDIO), CheckLevel.WARN))
        add(check(R.string.st_voice, R.string.st_voice_hint, state.voiceAvailable, CheckLevel.WARN))
        val btPerm = if (Build.VERSION.SDK_INT >= 31) granted(ctx, Manifest.permission.BLUETOOTH_CONNECT) else true
        val btOn = runCatching { ctx.getSystemService(BluetoothManager::class.java)?.adapter?.isEnabled == true }.getOrDefault(false)
        add(check(R.string.st_bluetooth, R.string.st_bluetooth_hint, btPerm && btOn, CheckLevel.WARN))
        if (Build.VERSION.SDK_INT >= 33) add(check(R.string.st_notif, R.string.st_notif_hint, granted(ctx, Manifest.permission.POST_NOTIFICATIONS), CheckLevel.WARN))
        add(check(R.string.st_offline, R.string.st_offline_hint, state.pkg.phase == PackagePhase.READY, CheckLevel.WARN))
        add(check(R.string.st_camera, R.string.st_camera_hint, granted(ctx, Manifest.permission.CAMERA), CheckLevel.WARN))
    }

    private fun check(title: Int, hint: Int, ok: Boolean, whenMissing: CheckLevel) = SelfCheck(title, hint, if (ok) CheckLevel.OK else whenMissing)

    /** Gesamtergebnis: schlechtester Einzelwert. */
    fun overall(checks: List<SelfCheck>): CheckLevel = when {
        checks.any { it.level == CheckLevel.FAIL } -> CheckLevel.FAIL
        checks.any { it.level == CheckLevel.WARN } -> CheckLevel.WARN
        else -> CheckLevel.OK
    }
}
