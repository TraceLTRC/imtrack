package xyz.tracel.imtrack.stopwatch

import android.Manifest
import android.app.Application
import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import xyz.tracel.imtrack.MutableClock
import xyz.tracel.imtrack.data.AppDatabase
import xyz.tracel.imtrack.data.LastCreatedSession
import xyz.tracel.imtrack.data.ProjectRepository
import xyz.tracel.imtrack.data.SessionRepository
import xyz.tracel.imtrack.data.StopwatchRepository
import java.time.Instant
import java.time.LocalDate

@RunWith(RobolectricTestRunner::class)
class StopwatchNotifierTest {
    /** Midday on Sun 4 Oct 2026, UTC. */
    private val clock = MutableClock(Instant.parse("2026-10-04T12:00:00Z").toEpochMilli())
    private val context: Application = ApplicationProvider.getApplicationContext()
    private val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    private lateinit var db: AppDatabase
    private lateinit var projects: ProjectRepository
    private lateinit var sessions: SessionRepository
    private lateinit var stopwatch: StopwatchRepository
    private lateinit var notifier: StopwatchNotifier
    private lateinit var guitarId: String

    @Before
    fun setUp() {
        shadowOf(context).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()
        projects = ProjectRepository(db.projectDao(), clock)
        sessions = SessionRepository(db.sessionDao(), clock, LastCreatedSession())
        stopwatch = StopwatchRepository(db, db.runningStopwatchDao(), sessions, clock)
        notifier = StopwatchNotifier(context, stopwatch, projects)
        runTest {
            projects.create("Guitar", 0)
            guitarId = projects.observeAll().first().single().id
        }
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun aRunningStopwatchShowsAnOngoingSilentNotificationWithTheSystemClock() = runTest {
        stopwatch.start(guitarId)

        notifier.reconcile()

        val notification = shadowOf(manager).allNotifications.single()
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
