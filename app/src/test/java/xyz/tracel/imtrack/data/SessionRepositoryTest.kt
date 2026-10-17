package xyz.tracel.imtrack.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import xyz.tracel.imtrack.MutableClock
import java.time.LocalDate
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
class SessionRepositoryTest {
    private val clock = MutableClock(1_000L)
    private val oct4 = LocalDate.of(2026, 10, 4)
    private val oct3 = LocalDate.of(2026, 10, 3)
    private lateinit var db: AppDatabase
    private lateinit var repository: SessionRepository

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java,
        ).allowMainThreadQueries().build()
        repository = SessionRepository(db.sessionDao(), clock)
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
    fun logStoresALiveSessionStampedByTheClock() = runTest {
        val id = repository.log(projectId = "guitar", date = oct3, durationSeconds = 1_800L)

        val session = db.sessionDao().getById(id)!!
        assertEquals("guitar", session.projectId)
        assertEquals(oct3, session.date)
        assertEquals(1_800L, session.durationSeconds)
        assertEquals(1_000L, session.createdAt)
        assertEquals(1_000L, session.updatedAt)
        assertNull(session.deletedAt)
        UUID.fromString(session.id)
    }

    @Test
    fun totalsOnADayAddUpSecondsPerProject() = runTest {
        repository.log("guitar", oct4, 20L)
        repository.log("guitar", oct4, 20L)
        repository.log("reading", oct4, 600L)
        repository.log("guitar", oct3, 900L)

        assertEquals(mapOf("guitar" to 40L, "reading" to 600L), repository.observeTotalsOn(oct4).first())
    }

    @Test
    fun softDeletedSessionsAreLeftOut() = runTest {
        repository.log("guitar", oct4, 60L)
        db.sessionDao().insert(
            Session("deleted", "reading", oct4, 600L, createdAt = 2_000L, updatedAt = 2_000L, deletedAt = 3_000L),
        )

        assertEquals(mapOf("guitar" to 60L), repository.observeTotalsOn(oct4).first())
        assertEquals(listOf("guitar"), repository.observeLatestPerProject().first().map { it.projectId })
    }

    @Test
    fun latestPerProjectIsTheHighestDateThenTheNewestCreated() = runTest {
        clock.millis = 5_000L
        repository.log("guitar", oct4, 60L)
        clock.millis = 9_000L
        repository.log("guitar", oct3, 60L)
        clock.millis = 6_000L
        repository.log("reading", oct4, 60L)
        clock.millis = 7_000L
        repository.log("reading", oct4, 60L)

        assertEquals(
            setOf(ProjectActivity("guitar", oct4, 5_000L), ProjectActivity("reading", oct4, 7_000L)),
            repository.observeLatestPerProject().first().toSet(),
        )
    }

    @Test
    fun updateChangesProjectDateAndSecondsAndStampsUpdatedAt() = runTest {
        val id = repository.log("guitar", oct4, 1_234L)
        clock.millis = 2_000L

        repository.update(id, projectId = "reading", date = oct3, durationSeconds = 600L)

        assertEquals(
            listOf(Session(id, "reading", oct3, 600L, createdAt = 1_000L, updatedAt = 2_000L, deletedAt = null)),
            repository.observeLive().first(),
        )
    }

    @Test
    fun deleteHidesTheSessionButKeepsTheRowWithDeletedAt() = runTest {
        val id = repository.log("guitar", oct4, 600L)
        repository.log("reading", oct4, 60L)
        clock.millis = 3_000L

        repository.delete(id)

        assertEquals(listOf("reading"), repository.observeLive().first().map { it.projectId })
        assertEquals(mapOf("reading" to 60L), repository.observeTotalsOn(oct4).first())
        val row = db.sessionDao().getById(id)!!
        assertEquals(3_000L, row.deletedAt)
        assertEquals(3_000L, row.updatedAt)
    }
}
