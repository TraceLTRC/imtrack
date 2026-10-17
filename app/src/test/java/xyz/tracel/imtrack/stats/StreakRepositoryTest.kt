package xyz.tracel.imtrack.stats

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import xyz.tracel.imtrack.MutableClock
import xyz.tracel.imtrack.data.AppDatabase
import xyz.tracel.imtrack.data.LastCreatedSession
import xyz.tracel.imtrack.data.ProjectRepository
import xyz.tracel.imtrack.data.SessionRepository
import java.time.Instant
import java.time.LocalDate

@RunWith(RobolectricTestRunner::class)
class StreakRepositoryTest {
    /** Midday on Wed 7 Oct 2026, UTC. */
    private val clock = MutableClock(Instant.parse("2026-10-07T12:00:00Z").toEpochMilli())
    private val today = LocalDate.of(2026, 10, 7)
    private lateinit var db: AppDatabase
    private lateinit var projects: ProjectRepository
    private lateinit var sessions: SessionRepository
    private lateinit var streaks: StreakRepository
    private lateinit var guitarId: String

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), AppDatabase::class.java)
            .allowMainThreadQueries().build()
        projects = ProjectRepository(db.projectDao(), clock)
        sessions = SessionRepository(db.sessionDao(), clock, LastCreatedSession())
        streaks = StreakRepository(sessions, clock)
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
    fun consecutiveDaysWithTimeMakeTheStreak() = runTest {
        listOf(0L, 1L, 2L, 4L).forEach { sessions.log(guitarId, today.minusDays(it), 60L) }

        assertEquals(Streak(current = 3, longest = 3), streaks.observe().first())
    }

    @Test
    fun aDeletedSessionThatWasTheDaysOnlyTimeBreaksIt() = runTest {
        sessions.log(guitarId, today, 60L)
        val onlyYesterday = sessions.log(guitarId, today.minusDays(1), 60L)
        sessions.log(guitarId, today.minusDays(2), 60L)

        sessions.delete(onlyYesterday)

        assertEquals(Streak(current = 1, longest = 1), streaks.observe().first())
    }

    @Test
    fun archivedProjectsStillCount() = runTest {
        sessions.log(guitarId, today.minusDays(1), 60L)
        sessions.log(guitarId, today.minusDays(2), 60L)

        projects.setArchived(guitarId, archived = true)

        assertEquals(Streak(current = 2, longest = 2), streaks.observe().first())
    }

    @Test
    fun todayEmptyCountsFromYesterdayButADayWithNothingEndsIt() = runTest {
        sessions.log(guitarId, today.minusDays(1), 60L)
        assertEquals(1, streaks.observe().first().current)

        // Tomorrow, with nothing logged today, the Streak is over.
        clock.millis += 86_400_000L
        assertEquals(Streak(current = 0, longest = 1), streaks.observe().first())
    }
}
