package app.kiloquest

import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import app.kiloquest.nav.Reminder
import app.kiloquest.nav.ReminderKind
import app.kiloquest.nav.PlanStore
import app.kiloquest.nav.TripReminders
import java.time.LocalDate
import java.util.concurrent.TimeUnit

/** Plant die Pruefung auf faellige Erinnerungen (alle 6 Stunden, ueberlebt Neustart) und eine sofortige beim App-Start. */
object ReminderScheduler {
    private const val PERIODIC = "trip_reminders"
    private const val NOW = "trip_reminders_now"

    fun sync(context: Context, enabled: Boolean) {
        val wm = WorkManager.getInstance(context)
        if (!enabled) {
            wm.cancelUniqueWork(PERIODIC)
            wm.cancelUniqueWork(NOW)
            return
        }
        wm.enqueueUniquePeriodicWork(PERIODIC, ExistingPeriodicWorkPolicy.KEEP, PeriodicWorkRequestBuilder<TripReminderWorker>(6, TimeUnit.HOURS).build())
        wm.enqueueUniqueWork(NOW, ExistingWorkPolicy.REPLACE, OneTimeWorkRequestBuilder<TripReminderWorker>().build())
    }
}

internal object ReminderTexts {
    fun title(c: Context, r: Reminder): String = c.getString(R.string.app_name) + ": " + r.tripName

    fun body(c: Context, r: Reminder): String = when (r.kind) {
        ReminderKind.COUNTDOWN -> c.resources.getQuantityString(R.plurals.rem_countdown, r.daysLeft, r.daysLeft)
        ReminderKind.DEPARTURE_EVE -> c.getString(R.string.rem_eve)
        ReminderKind.DEPARTURE_DAY -> c.getString(R.string.rem_day)
        ReminderKind.CHECK_IN_TOMORROW -> c.getString(R.string.rem_checkin_tomorrow, r.stayName.orEmpty())
        ReminderKind.CHECK_IN_TODAY -> c.getString(R.string.rem_checkin_today, r.stayName.orEmpty())
    }
}

/** Prueft die gespeicherten Reisen und zeigt faellige Erinnerungen an; jede nur einmal. */
class TripReminderWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    @SuppressLint("MissingPermission") // areNotificationsEnabled() prueft die Berechtigung vorher
    override suspend fun doWork(): Result {
        val c = applicationContext
        val prefs = c.getSharedPreferences("kq", Context.MODE_PRIVATE)
        if (!SettingsStore.load(prefs).tripReminders) return Result.success()
        val trips = PlanStore.decode(prefs.getString("plans", null))
        val sent = prefs.getStringSet("reminders_sent", emptySet()).orEmpty().toSet()
        var keep = TripReminders.prune(sent, trips)
        val nm = NotificationManagerCompat.from(c)
        if (nm.areNotificationsEnabled()) {
            val due = TripReminders.due(trips, LocalDate.now().toEpochDay().toInt(), sent)
            if (due.isNotEmpty()) ensureChannel(c)
            val open = PendingIntent.getActivity(c, 0, Intent(c, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
            val dynamic = SettingsStore.load(prefs).dynamicNotifIcons
            for (r in due) {
                val ev = if (r.kind == app.kiloquest.nav.ReminderKind.DEPARTURE_DAY) app.kiloquest.nav.NotifEvent.DEPARTURE else app.kiloquest.nav.NotifEvent.REMINDER
                val spec = NotifIcons.spec(c, ev, null, dynamic)
                val n = NotificationCompat.Builder(c, CHANNEL)
                    .setSmallIcon(NotifIcons.smallRes(spec.glyph))
                    .setLargeIcon(NotifIcons.large(spec))
                    .setContentTitle(ReminderTexts.title(c, r))
                    .setContentText(ReminderTexts.body(c, r))
                    .setStyle(NotificationCompat.BigTextStyle().bigText(ReminderTexts.body(c, r)))
                    .setContentIntent(open)
                    .setAutoCancel(true)
                    .build()
                nm.notify(r.key.hashCode(), n)
                keep = keep + r.key
            }
        }
        if (keep != sent) prefs.edit().putStringSet("reminders_sent", keep).apply()
        return Result.success()
    }

    private fun ensureChannel(c: Context) {
        val nm = c.getSystemService(NotificationManager::class.java)
        if (nm.getNotificationChannel(CHANNEL) == null) {
            nm.createNotificationChannel(NotificationChannel(CHANNEL, c.getString(R.string.rem_channel), NotificationManager.IMPORTANCE_DEFAULT))
        }
    }

    private companion object { const val CHANNEL = "trip_reminders" }
}
