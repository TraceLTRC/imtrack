package xyz.tracel.imtrack.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import xyz.tracel.imtrack.MutableClock
import java.time.Instant
import java.time.LocalDate

@RunWith(RobolectricTestRunner::class)
class StopwatchRepositoryTest {
    /** Midday on Sun 4 Oct 2026, UTC. */
    private val clock = MutableClock(Instant.parse("2026-10-04T12:00:00Z").toEpochMilli())
    private val oct4 = LocalDate.of(2026, 10, 4)
    private lateinit var db: AppDatabase
    private lateinit var lastCreated: LastCreatedSession
    private lateinit var sessions: SessionRepository
    private lateinit var stopwatch: StopwatchRepository

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java,
        ).build()
        lastCreated = LastCreatedSession()
        sessions = SessionRepository(db.sessionDao(), clock, lastCreated)
        stopwatch = StopwatchRepository(db, db.runningStopwatchDao(), sessions, clock)
        runTest {
            listOf("guitar", "reading").forEach {
                db.projectDao().insert(Project(it, it, 0, archived = false, createdAt = 0L, updatedAt = 0L, deletedAt = null))
            }
        }
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun startStoresTheStartInstantAndDay() = runTest {
        stopwatch.start("guitar")

        assertEquals(
            RunningStopwatch(projectId = "guitar", startedAt = clock.millis, startDate = oct4, reminderShown = false),
            stopwatch.observeRunning().first(),
        )
    }

    @Test
    fun onlyOneStopwatchRunsAtATime() = runTest {
        assertTrue(stopwatch.start("guitar"))

        assertFalse(stopwatch.start("reading"))

        assertEquals("guitar", stopwatch.observeRunning().first()?.projectId)
    }

    @Test
    fun keepingSavesTheRealSecondsAsTheUndoTarget() = runTest {
        stopwatch.start("guitar")
        clock.millis += 1_925_400L

        val stopped = stopwatch.stop(keep = true)!!

        assertEquals(StoppedStopwatch("guitar", oct4, 1_925L, saved = true), stopped)
        assertNull(stopwatch.observeRunning().first())
        val session = sessions.observeLive().first().single()
        assertEquals(1_925L, session.durationSeconds)
        assertEquals(oct4, session.date)
        assertEquals(session.id, lastCreated.id.value)
    }

    @Test
    fun discardingSavesNothing() = runTest {
        stopwatch.start("guitar")
        clock.millis += 40_000L

        val stopped = stopwatch.stop(keep = false)!!

        assertEquals(StoppedStopwatch("guitar", oct4, 40L, saved = false), stopped)
        assertNull(stopwatch.observeRunning().first())
        assertEquals(emptyList<Session>(), sessions.observeLive().first())
    }

    @Test
    fun aStopCountsUpToWhenStopWasTapped() = runTest {
        stopwatch.start("guitar")
        val tapped = clock.millis + 40_000L
        clock.millis += 75_000L

        stopwatch.stop(keep = true, stoppedAt = tapped)

        assertEquals(40L, sessions.observeLive().first().single().durationSeconds)
    }

    @Test
    fun aStopwatchStartedBeforeMidnightIsDatedTheStartDay() = runTest {
        clock.millis = Instant.parse("2026-10-04T23:50:00Z").toEpochMilli()
        stopwatch.start("guitar")
        clock.millis = Instant.parse("2026-10-05T00:20:00Z").toEpochMilli()

        stopwatch.stop(keep = true)

        val session = sessions.observeLive().first().single()
        assertEquals(oct4, session.date)
        assertEquals(1_800L, session.durationSeconds)
    }

    @Test
    fun elapsedTimeNeverGoesBelowZeroWhenTheClockMovesBack() = runTest {
        stopwatch.start("guitar")
        val running = stopwatch.observeRunning().first()!!
        clock.millis -= 3_600_000L

        assertEquals(0L, stopwatch.elapsedSeconds(running))
        stopwatch.stop(keep = true)
        // A kept Session is never zero seconds long.
        assertEquals(1L, sessions.observeLive().first().single().durationSeconds)
    }

    @Test
    fun stoppingWhenNothingRunsDoesNothing() = runTest {
        assertNull(stopwatch.stop(keep = true))
        assertEquals(emptyList<Session>(), sessions.observeLive().first())
    }

    @Test
    fun twoStopsAtOnceSaveExactlyOneSession() = runTest {
        stopwatch.start("guitar")
        clock.millis += 120_000L

        val results = withContext(Dispatchers.IO) {
            List(8) { async { stopwatch.stop(keep = true) } }.awaitAll()
        }

        assertEquals(1, results.count { it != null })
        assertEquals(1, sessions.observeLive().first().size)
    }
}
