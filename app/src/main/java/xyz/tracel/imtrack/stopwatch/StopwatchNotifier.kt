package xyz.tracel.imtrack.stopwatch

import android.Manifest
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
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import xyz.tracel.imtrack.MainActivity
import xyz.tracel.imtrack.R
import xyz.tracel.imtrack.data.ProjectRepository
import xyz.tracel.imtrack.data.RunningStopwatch
import xyz.tracel.imtrack.data.StopwatchRepository
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The Stopwatch's ongoing notification (ADR-0005): no service, just a notification whose clock the
 * system draws from the start time. [reconcile] makes the shade match the stored state.
 */
@Singleton
class StopwatchNotifier @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val stopwatchRepository: StopwatchRepository,
    private val projectRepository: ProjectRepository,
) {
    private val manager = NotificationManagerCompat.from(context)

    // Each reconcile reads the state and acts on it as one step, so a stale read can't re-post after a stop.
    private val reconciling = Mutex()

    /**
     * Idempotent: posts the notification if a Stopwatch is running and notifications are allowed,
     * otherwise cancels it. Cancelling from code doesn't fire the delete intent, so it never stops anything.
     */
    suspend fun reconcile() = reconciling.withLock {
        val running = stopwatchRepository.getRunning()
        val name = running?.let { projectRepository.get(it.projectId)?.name }
        if (running != null && name != null && notificationsAllowed(context)) {
            post(running, name)
        } else {
            manager.cancel(NotificationId)
        }
    }

    /**
     * Reconciles now and whenever the Stopwatch starts or stops (from anywhere, Import included) or
     * its Project is renamed, for as long as [scope] lives.
     */
    fun watch(scope: CoroutineScope) {
        scope.launch {
            combine(stopwatchRepository.observeRunning(), projectRepository.observeAll()) { running, projects ->
                running?.let { stopwatch -> stopwatch to projects.find { it.id == stopwatch.projectId }?.name }
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
        val stop = PendingIntent.getBroadcast(
            context,
            0,
            Intent(context, StopwatchReceiver::class.java).setAction(StopwatchReceiver.ActionStop),
            PendingIntent.FLAG_IMMUTABLE,
        )
        val open = PendingIntent.getActivity(
            context,
            0,
            // Brings an open app forward (onNewIntent) and shows Home, keeping what's open behind it.
            Intent(context, MainActivity::class.java)
                .setAction(MainActivity.ActionOpenHome)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(context, ChannelId)
            .setSmallIcon(R.drawable.ic_stat_stopwatch)
            .setContentTitle(projectName)
            .setWhen(running.startedAt)
            .setShowWhen(true)
            .setUsesChronometer(true)
            .setOngoing(true)
            .setSilent(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(open)
            // Android 14+ lets users swipe ongoing notifications away; that's a deliberate stop.
            .setDeleteIntent(stop)
            .addAction(R.drawable.ic_stop, context.getString(R.string.stop), stop)
            .build()
        // notificationsAllowed() checked the permission just before.
        @Suppress("MissingPermission")
        manager.notify(NotificationId, notification)
    }

    companion object {
        const val ChannelId = "stopwatch"
        const val NotificationId = 1
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
