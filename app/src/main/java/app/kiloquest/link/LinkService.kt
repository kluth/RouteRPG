package app.kiloquest.link

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import app.kiloquest.KiloQuestApp
import app.kiloquest.MainActivity
import app.kiloquest.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

/**
 * Vordergrunddienst (Typ "connectedDevice"): haelt den Prozess am Leben, solange das Hauptgeraet auf Geraete wartet
 * oder Geraete verbunden sind bzw. dieses Geraet mit einem Hauptgeraet verbunden ist. Der Dienst haelt selbst keine
 * Verbindung; die liegt in [LinkController] und [BtClient]. Er beendet sich, wenn nichts mehr aktiv ist.
 */
class LinkService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var job: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val app = application as KiloQuestApp
        if (intent?.action == ACTION_STOP) {
            app.link.stopHosting(); app.linkClient.disconnect()
            stopSelf()
            return START_NOT_STICKY
        }
        ensureChannel()
        startForegroundCompat(buildNotification(getString(R.string.link_notif_waiting)))
        job?.cancel()
        job = scope.launch {
            combine(app.link.state, app.linkClient.state) { host, client -> host to client }.collect { (host, client) ->
                val connectedKids = host.devices.count { it.connected }
                val clientActive = client.status in setOf(ClientStatus.CONNECTING, ClientStatus.WAITING, ClientStatus.CONNECTED, ClientStatus.LOST)
                if (!host.hosting && !clientActive) { stopSelf(); return@collect }
                val text = when {
                    clientActive && client.status == ClientStatus.CONNECTED -> getString(R.string.link_notif_client, client.hostName ?: "")
                    clientActive -> getString(R.string.link_notif_client_connecting)
                    connectedKids > 0 -> getString(R.string.link_notif_host, connectedKids)
                    else -> getString(R.string.link_notif_waiting)
                }
                getSystemService(NotificationManager::class.java).notify(NOTIF_ID, buildNotification(text))
            }
        }
        return START_NOT_STICKY
    }

    private fun startForegroundCompat(n: Notification) {
        if (android.os.Build.VERSION.SDK_INT >= 29) startForeground(NOTIF_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE)
        else startForeground(NOTIF_ID, n)
    }

    private fun ensureChannel() {
        val nm = getSystemService(NotificationManager::class.java)
        if (nm.getNotificationChannel(CHANNEL) == null) {
            nm.createNotificationChannel(NotificationChannel(CHANNEL, getString(R.string.link_notif_channel), NotificationManager.IMPORTANCE_LOW))
        }
    }

    private fun buildNotification(text: String): Notification {
        val open = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        val stop = PendingIntent.getService(this, 1, Intent(this, LinkService::class.java).setAction(ACTION_STOP), PendingIntent.FLAG_IMMUTABLE)
        return NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_link)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(text)
            .setContentIntent(open)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
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
        private const val ACTION_STOP = "app.kiloquest.link.STOP"

        /** Aus der Oberflaeche (Vordergrund) starten; scheitert der Start (Systemgrenzen), laeuft die Kopplung eben ohne Dienst. */
        fun start(ctx: Context) {
            runCatching { ContextCompat.startForegroundService(ctx, Intent(ctx, LinkService::class.java)) }
        }
    }
}
