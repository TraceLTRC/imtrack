package xyz.tracel.imtrack.stopwatch

import android.Manifest
import android.app.AlarmManager
import android.app.Application
import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import xyz.tracel.imtrack.MutableClock
import xyz.tracel.imtrack.data.AppDatabase
import xyz.tracel.imtrack.data.LastCreatedSession
import xyz.tracel.imtrack.data.ProjectRepository
import xyz.tracel.imtrack.data.SessionRepository
import xyz.tracel.imtrack.data.StopwatchRepository
import xyz.tracel.imtrack.settings.ReminderSettings
import java.time.Instant
import java.time.LocalDate

@RunWith(RobolectricTestRunner::class)
class StopwatchNotifierTest {
    @get:Rule val folder = TemporaryFolder()
    private val dataStoreScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    /** Midday on Sun 4 Oct 2026, UTC. */
    private val clock = MutableClock(Instant.parse("2026-10-04T12:00:00Z").toEpochMilli())
    private val context: Application = ApplicationProvider.getApplicationContext()
    private val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    private lateinit var db: AppDatabase
    private lateinit var projects: ProjectRepository
    private lateinit var sessions: SessionRepository
    private lateinit var stopwatch: StopwatchRepository
    private lateinit var reminder: ReminderSettings
    private lateinit var notifier: StopwatchNotifier
    private val alarms get() = shadowOf(context.getSystemService(AlarmManager::class.java))
    private val hour = 3_600_000L
    private lateinit var guitarId: String

    @Before
    fun setUp() {
        shadowOf(context).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()
        projects = ProjectRepository(db.projectDao(), clock)
        sessions = SessionRepository(db.sessionDao(), clock, LastCreatedSession())
        stopwatch = StopwatchRepository(db, db.runningStopwatchDao(), sessions, clock)
        reminder = ReminderSettings(PreferenceDataStoreFactory.create(scope = dataStoreScope) { folder.newFile("s.preferences_pb").also { it.delete() } })
        notifier = StopwatchNotifier(context, stopwatch, projects, reminder, clock)
        runTest {
            projects.create("Guitar", 0)
            guitarId = projects.observeAll().first().single().id
        }
    }

    @After
    fun tearDown() {
        dataStoreScope.cancel()
        db.close()
    }

    @Test
    fun aRunningStopwatchShowsAnOngoingSilentNotificationWithTheSystemClock() = runTest {
        stopwatch.start(guitarId)

        notifier.reconcile()

        val notification = stopwatchNotification()!!
        assertEquals("Guitar", notification.extras.getString(Notification.EXTRA_TITLE))
        assertEquals(clock.millis, notification.`when`)
        assertTrue(notification.extras.getBoolean(Notification.EXTRA_SHOW_CHRONOMETER))
        assertTrue(notification.flags and Notification.FLAG_ONGOING_EVENT != 0)
        assertEquals(listOf("Stop"), notification.actions.map { it.title.toString() })
        assertEquals(NotificationManager.IMPORTANCE_LOW, manager.getNotificationChannel(StopwatchNotifier.ChannelId).importance)
    }

    @Test
    fun reconcilingIsIdempotent() = runTest {
        stopwatch.start(guitarId)

        notifier.reconcile()
        notifier.reconcile()

        assertEquals(1, shadowOf(manager).allNotifications.size)
    }

    @Test
    fun theReminderIsScheduledForStartPlusReminderAfter() = runTest {
        stopwatch.start(guitarId)

        notifier.reconcile()

        assertEquals(clock.millis + 4 * hour, alarms.peekNextScheduledAlarm()!!.triggerAtTime)
        assertNull(reminderNotification())
    }

    @Test
    fun aDueReminderShowsOnceWithStopAndKeepRunning() = runTest {
        stopwatch.start(guitarId)
        notifier.reconcile()
        clock.millis += 4 * hour

        // What the alarm does.
        notifier.reconcile()

        val shown = reminderNotification()!!
        assertEquals("Still on Guitar?", shown.extras.getString(Notification.EXTRA_TITLE))
        assertEquals("The Stopwatch has been running for 4 h.", shown.extras.getString(Notification.EXTRA_TEXT))
        assertEquals(listOf("Stop", "Keep running"), shown.actions.map { it.title.toString() })
        assertNull(alarms.peekNextScheduledAlarm())
        assertTrue(stopwatch.getRunning()!!.reminderShown)

        notifier.dismissReminder()
        clock.millis += hour
        notifier.reconcile()

        assertNull(reminderNotification())
        assertNotNull(stopwatchNotification())
        assertNull(alarms.peekNextScheduledAlarm())
    }

    @Test
    fun afterARebootPastTheDueTimeTheReminderAppearsButOnlyOnce() = runTest {
        stopwatch.start(guitarId)
        clock.millis += 9 * hour

        notifier.reconcile()
        assertNotNull(reminderNotification())
        manager.cancelAll()

        // A second reboot: the Reminder was already shown in this run.
        notifier.reconcile()
        assertNotNull(stopwatchNotification())
        assertNull(reminderNotification())
    }

    @Test
    fun changingReminderAfterReschedulesFromTheStart() = runTest {
        stopwatch.start(guitarId)
        val start = clock.millis
        clock.millis += 2 * hour
        notifier.reconcile()

        reminder.setReminderAfterHours(3)
        notifier.reconcile()
        assertEquals(start + 3 * hour, alarms.peekNextScheduledAlarm()!!.triggerAtTime)

        reminder.setReminderAfterHours(1)
        notifier.reconcile()
        assertNotNull(reminderNotification())
    }

    @Test
    fun stoppingClearsBothNotificationsAndTheAlarm() = runTest {
        stopwatch.start(guitarId)
        clock.millis += 5 * hour
        notifier.reconcile()
        assertNotNull(reminderNotification())

        notifier.stopFromNotification()

        assertEquals(emptyList<Notification>(), shadowOf(manager).allNotifications)
        assertNull(alarms.peekNextScheduledAlarm())
    }

    @Test
    fun switchingProjectsClearsTheStoppedRunsReminder() = runTest {
        projects.create("Reading", 0)
        val readingId = projects.observeAll().first { it.size == 2 }.single { it.name == "Reading" }.id
        stopwatch.start(guitarId)
        clock.millis += 5 * hour
        notifier.reconcile()
        assertNotNull(reminderNotification())

        // A switch: no reconcile sees the moment between the stop and the start.
        stopwatch.stop(keep = true)
        stopwatch.start(readingId)
        notifier.reconcile()

        assertNull(reminderNotification())
        assertEquals("Reading", stopwatchNotification()!!.extras.getString(Notification.EXTRA_TITLE))
        assertEquals(clock.millis + 4 * hour, alarms.peekNextScheduledAlarm()!!.triggerAtTime)
    }

    @Test
    fun withoutPermissionNoReminderIsScheduled() = runTest {
        shadowOf(context).denyPermissions(Manifest.permission.POST_NOTIFICATIONS)
        stopwatch.start(guitarId)

        notifier.reconcile()

        assertNull(alarms.peekNextScheduledAlarm())
    }

    private fun stopwatchNotification() = shadowOf(manager).getNotification(StopwatchNotifier.NotificationId)

    private fun reminderNotification() = shadowOf(manager).getNotification(StopwatchNotifier.ReminderNotificationId)

    @Test
    fun noStopwatchMeansNoNotification() = runTest {
        stopwatch.start(guitarId)
        notifier.reconcile()
        stopwatch.stop(keep = true)

        notifier.reconcile()

        assertEquals(emptyList<Notification>(), shadowOf(manager).allNotifications)
    }

    @Test
    fun withoutPermissionNothingIsPosted() = runTest {
        shadowOf(context).denyPermissions(Manifest.permission.POST_NOTIFICATIONS)
        stopwatch.start(guitarId)

        notifier.reconcile()

        assertEquals(emptyList<Notification>(), shadowOf(manager).allNotifications)
    }

    @Test
    fun stopFromTheNotificationKeepsEvenUnderAMinuteAndRemovesIt() = runTest {
        stopwatch.start(guitarId)
        notifier.reconcile()
        clock.millis += 20_000L

        notifier.stopFromNotification()

        assertEquals(mapOf(guitarId to 20L), sessions.observeTotalsOn(LocalDate.of(2026, 10, 4)).first())
        assertEquals(emptyList<Notification>(), shadowOf(manager).allNotifications)
    }

    @Test
    fun aSecondStopFromTheNotificationSavesNothingMore() = runTest {
        stopwatch.start(guitarId)
        clock.millis += 120_000L

        notifier.stopFromNotification()
        notifier.stopFromNotification()

        assertEquals(1, sessions.observeLive().first().size)
    }
}
