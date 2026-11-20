package app.kiloquest

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import app.kiloquest.link.ClientStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

/**
 * Vordergrunddienst: haelt die App am Leben, solange navigiert wird (Typ "location": Position und Ansagen laufen auch
 * im Hintergrund oder bei gesperrtem Bildschirm) oder Geraete gekoppelt sind (Typ "connectedDevice").
 * Der Dienst haelt selbst keinen Zustand; er zeigt die Benachrichtigung und beendet sich, wenn nichts mehr aktiv ist.
 */
class KeepAliveService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var job: Job? = null
    private var lastText = ""
    private var lastProgress = -1
    private var lastNotifyAt = 0L

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val app = application as KiloQuestApp
        if (intent?.action == ACTION_STOP) {
            app.nav.stopNavigation(); app.link.stopHosting(); app.linkClient.disconnect()
            stopSelf()
            return START_NOT_STICKY
        }
        ensureChannel()
        // Erster Start: Typ aus dem aktuellen Zustand bestimmen (startForeground muss sofort kommen)
        val navNow = app.nav.state.value.navigating
        val linkNow = app.link.state.value.hosting || app.linkClient.state.value.status != ClientStatus.IDLE
        try {
            startForegroundCompat(buildNotification(getString(R.string.link_notif_waiting)), typeFor(navNow, linkNow))
        } catch (_: Exception) {
            stopSelf(); return START_NOT_STICKY
        }
        job?.cancel()
        job = scope.launch {
            combine(app.nav.state, app.link.state, app.linkClient.state) { nav, host, client -> Triple(nav, host, client) }.collect { (nav, host, client) ->
                val clientActive = client.status in setOf(ClientStatus.CONNECTING, ClientStatus.WAITING, ClientStatus.CONNECTED, ClientStatus.LOST)
                val navigating = nav.navigating
                if (!navigating && !host.hosting && !clientActive) { stopSelf(); return@collect }
                val connectedKids = host.devices.count { it.connected }
                // Fahrtkarte: Fortschrittsbalken (Strecke) und Teamziel in der Benachrichtigung (normaler Balken; Android-16-Live-Updates stehen noch aus)
                var progress: Int? = null
                val text = when {
                    navigating -> {
                        val total = nav.route?.lengthMeters ?: 0.0
                        val m = nav.guidance?.remainingMeters ?: total
                        if (total > 0) progress = ((1.0 - m / total).coerceIn(0.0, 1.0) * 1000).toInt()
                        val dist = if (m >= 1000) getString(R.string.unit_km, m / 1000.0) else getString(R.string.unit_m, m.toInt())
                        val goal = nav.teamGoal
                        if (goal != null && !goal.reached) getString(R.string.nav_notif_progress, dist, (goal.progress * 100).toInt()) else getString(R.string.nav_notif_running, dist)
                    }
                    clientActive && client.status == ClientStatus.CONNECTED -> getString(R.string.link_notif_client, client.hostName ?: "")
                    clientActive -> getString(R.string.link_notif_client_connecting)
                    connectedKids > 0 -> getString(R.string.link_notif_host, connectedKids)
                    else -> getString(R.string.link_notif_waiting)
                }
                val now = System.currentTimeMillis()
                // Hoechstens alle 10 s aktualisieren, sonst flackert die Benachrichtigung mit jedem Positions-Update
                if ((text != lastText || progress != lastProgress.takeIf { it >= 0 }) && now - lastNotifyAt > 10_000) {
                    lastText = text; lastNotifyAt = now; lastProgress = progress ?: -1
                    try { startForegroundCompat(buildNotification(text, progress), typeFor(navigating, host.hosting || clientActive)) } catch (_: Exception) { }
                }
            }
        }
        return START_NOT_STICKY
    }

    /** Nur Typen angeben, deren Rechte vorhanden sind (sonst wirft Android 14 eine Ausnahme). */
    private fun typeFor(navigating: Boolean, link: Boolean): Int {
        if (android.os.Build.VERSION.SDK_INT < 29) return 0
        var t = 0
        if (navigating && granted(Manifest.permission.ACCESS_FINE_LOCATION)) t = t or ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
        val btOk = android.os.Build.VERSION.SDK_INT < 31 || granted(Manifest.permission.BLUETOOTH_CONNECT)
        if (link && btOk) t = t or ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE
        return t
    }

    private fun granted(p: String) = ContextCompat.checkSelfPermission(this, p) == PackageManager.PERMISSION_GRANTED

    private fun startForegroundCompat(n: Notification, type: Int) {
        if (android.os.Build.VERSION.SDK_INT >= 29 && type != 0) startForeground(NOTIF_ID, n, type) else startForeground(NOTIF_ID, n)
    }

    private fun ensureChannel() {
        val nm = getSystemService(NotificationManager::class.java)
        if (nm.getNotificationChannel(CHANNEL) == null) {
            nm.createNotificationChannel(NotificationChannel(CHANNEL, getString(R.string.link_notif_channel), NotificationManager.IMPORTANCE_LOW))
        }
    }

    private fun buildNotification(text: String, progressPermille: Int? = null): Notification {
        val open = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        val stop = PendingIntent.getService(this, 1, Intent(this, KeepAliveService::class.java).setAction(ACTION_STOP), PendingIntent.FLAG_IMMUTABLE)
        return NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_link)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(text)
            .setContentIntent(open)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(if (progressPermille != null) NotificationCompat.CATEGORY_NAVIGATION else NotificationCompat.CATEGORY_SERVICE)
            .apply { if (progressPermille != null) setProgress(1000, progressPermille, false) }
            .addAction(0, getString(R.string.link_notif_stop), stop)
            .build()
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        private const val CHANNEL = "link"
        private const val NOTIF_ID = 4711
        private const val ACTION_STOP = "app.kiloquest.STOP"

        /** Aus der Oberflaeche (Vordergrund) starten; scheitert der Start (Systemgrenzen), laeuft alles wie vorher ohne Dienst. */
        fun start(ctx: Context) {
            runCatching { ContextCompat.startForegroundService(ctx, Intent(ctx, KeepAliveService::class.java)) }
        }
    }
}
