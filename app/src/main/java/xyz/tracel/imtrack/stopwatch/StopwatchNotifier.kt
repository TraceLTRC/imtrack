package xyz.tracel.imtrack.stopwatch

import android.Manifest
import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import xyz.tracel.imtrack.MainActivity
import xyz.tracel.imtrack.R
import xyz.tracel.imtrack.data.ProjectRepository
import xyz.tracel.imtrack.data.RunningStopwatch
import xyz.tracel.imtrack.data.StopwatchRepository
import xyz.tracel.imtrack.settings.ReminderSettings
import java.time.Clock
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The Stopwatch's ongoing notification and its Reminder (ADR-0005): no service, just a notification whose
 * clock the system draws from the start time, and an inexact alarm for the Reminder. [reconcile] makes
 * the shade and the alarm match the stored state.
 */
@Singleton
class StopwatchNotifier @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val stopwatchRepository: StopwatchRepository,
    private val projectRepository: ProjectRepository,
    private val reminderSettings: ReminderSettings,
    private val clock: Clock,
) {
    private val manager = NotificationManagerCompat.from(context)
    private val alarms = context.getSystemService(AlarmManager::class.java)

    // Each reconcile reads the state and acts on it as one step, so a stale read can't re-post after a stop.
    private val reconciling = Mutex()

    /**
     * Idempotent: if a Stopwatch is running and notifications are allowed, posts its notification and
     * schedules the Reminder, or shows it if it's already due; otherwise clears both and the alarm.
     * Cancelling from code doesn't fire the delete intent, so it never stops anything.
     */
    suspend fun reconcile() = reconciling.withLock {
        val running = stopwatchRepository.getRunning()
        val name = running?.let { projectRepository.get(it.projectId)?.name }
        if (running == null || name == null || !notificationsAllowed(context)) {
            manager.cancel(NotificationId)
            manager.cancel(ReminderNotificationId)
            alarms.cancel(reminderDue())
            return@withLock
        }
        post(running, name)
        val hours = reminderSettings.reminderAfterHours.first()
        when (val plan = reminderPlan(running, hours, clock.millis())) {
            // Already shown in this run: leave a shown Reminder alone.
            ReminderPlan.None -> alarms.cancel(reminderDue())
            is ReminderPlan.ScheduleAt -> {
                // Not shown in this run, so a Reminder in the shade is a stopped run's (after a switch).
                manager.cancel(ReminderNotificationId)
                alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, plan.at, reminderDue())
            }
            ReminderPlan.ShowNow -> {
                alarms.cancel(reminderDue())
                showReminder(name, hours)
                stopwatchRepository.markReminderShown()
            }
        }
    }

    /** Keep running on the Reminder: dismisses it; the Stopwatch keeps going. */
    fun dismissReminder() {
        manager.cancel(ReminderNotificationId)
    }

    /**
     * Reconciles now and whenever the Stopwatch starts or stops (from anywhere, Import included), its
     * Project is renamed or **Reminder after** changes, for as long as [scope] lives.
     */
    fun watch(scope: CoroutineScope) {
        scope.launch {
            combine(
                stopwatchRepository.observeRunning(),
                projectRepository.observeAll(),
                reminderSettings.reminderAfterHours,
            ) { running, projects, hours ->
                running?.let { stopwatch -> Shown(stopwatch, projects.find { it.id == stopwatch.projectId }?.name, hours) }
            }.distinctUntilChanged().collect { reconcile() }
        }
    }

    /** Stop on the notification, or swiping it away: always keeps the Session, even under a minute. */
    suspend fun stopFromNotification() {
        stopwatchRepository.stop(keep = true)
        reconcile()
    }

    private fun post(running: RunningStopwatch, projectName: String) {
        manager.createNotificationChannel(
            NotificationChannelCompat.Builder(ChannelId, NotificationManagerCompat.IMPORTANCE_LOW)
                .setName(context.getString(R.string.stopwatch_channel))
                .setSound(null, null)
                .setVibrationEnabled(false)
                .build(),
        )
        val stop = broadcast(StopwatchReceiver.ActionStop)
        val notification = NotificationCompat.Builder(context, ChannelId)
            .setSmallIcon(R.drawable.ic_stat_stopwatch)
            .setContentTitle(projectName)
            .setWhen(running.startedAt)
            .setShowWhen(true)
            .setUsesChronometer(true)
            .setOngoing(true)
            .setSilent(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(openHome())
            // Android 14+ lets users swipe ongoing notifications away; that's a deliberate stop.
            .setDeleteIntent(stop)
            .addAction(R.drawable.ic_stop, context.getString(R.string.stop), stop)
            .build()
        // notificationsAllowed() checked the permission just before.
        @Suppress("MissingPermission")
        manager.notify(NotificationId, notification)
    }

    private fun showReminder(projectName: String, hours: Int) {
        manager.createNotificationChannel(
            NotificationChannelCompat.Builder(ReminderChannelId, NotificationManagerCompat.IMPORTANCE_DEFAULT)
                .setName(context.getString(R.string.reminder_channel))
                .build(),
        )
        val notification = NotificationCompat.Builder(context, ReminderChannelId)
            .setSmallIcon(R.drawable.ic_stat_stopwatch)
            .setContentTitle(context.getString(R.string.reminder_title, projectName))
            .setContentText(context.getString(R.string.reminder_text, hours))
            .setContentIntent(openHome())
            .setAutoCancel(true)
            .addAction(R.drawable.ic_stop, context.getString(R.string.stop), broadcast(StopwatchReceiver.ActionStop))
            .addAction(0, context.getString(R.string.keep_running), broadcast(StopwatchReceiver.ActionKeepRunning))
            .build()
        // notificationsAllowed() checked the permission just before.
        @Suppress("MissingPermission")
        manager.notify(ReminderNotificationId, notification)
    }

    /** The alarm that fires when the Reminder is due; it only triggers a reconcile. */
    private fun reminderDue() = broadcast(StopwatchReceiver.ActionReminderDue)

    private fun broadcast(action: String) = PendingIntent.getBroadcast(
        context,
        0,
        Intent(context, StopwatchReceiver::class.java).setAction(action),
        PendingIntent.FLAG_IMMUTABLE,
    )

    // Brings an open app forward (onNewIntent) and shows Home, keeping what's open behind it.
    private fun openHome() = PendingIntent.getActivity(
        context,
        0,
        Intent(context, MainActivity::class.java)
            .setAction(MainActivity.ActionOpenHome)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
        PendingIntent.FLAG_IMMUTABLE,
    )

    /** Everything the notifications depend on, so [watch] reconciles only when one of them changes. */
    private data class Shown(val running: RunningStopwatch, val projectName: String?, val reminderAfterHours: Int)

    companion object {
        const val ChannelId = "stopwatch"
        const val ReminderChannelId = "reminder"
        const val NotificationId = 1
        const val ReminderNotificationId = 2
    }
}

/**
 * Whether the app may post notifications: the Android 13+ permission and the app's switch in system settings.
 * Without it the Stopwatch works only inside the app.
 */
fun notificationsAllowed(context: Context): Boolean =
    NotificationManagerCompat.from(context).areNotificationsEnabled() && !needsNotificationPermission(context)

/** Whether the Android 13+ notification permission still has to be granted; earlier versions have none. */
fun needsNotificationPermission(context: Context): Boolean =
    Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
        PackageManager.PERMISSION_GRANTED
