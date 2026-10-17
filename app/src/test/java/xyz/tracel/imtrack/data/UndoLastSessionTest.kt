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

@RunWith(RobolectricTestRunner::class)
class UndoLastSessionTest {
    private val clock = MutableClock(1_000L)
    private val oct4 = LocalDate.of(2026, 10, 4)
    private lateinit var db: AppDatabase
    private lateinit var lastCreated: LastCreatedSession
    private lateinit var repository: SessionRepository

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java,
        ).allowMainThreadQueries().build()
        lastCreated = LastCreatedSession()
        repository = SessionRepository(db.sessionDao(), clock, lastCreated)
        runTest {
            db.projectDao().insert(Project("guitar", "Guitar", 0, archived = false, createdAt = 0L, updatedAt = 0L, deletedAt = null))
        }
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun nothingCreatedMeansNothingToUndo() = runTest {
        assertNull(repository.observeUndoTarget().first())
        assertNull(repository.undoLast())
    }

    @Test
    fun creatingASessionMakesItTheTarget() = runTest {
        val id = repository.log("guitar", oct4, 1_800L)

        assertEquals(id, repository.observeUndoTarget().first()?.id)
    }

    @Test
    fun undoSoftDeletesOnlyTheNewestSessionAndClearsTheTarget() = runTest {
        val first = repository.log("guitar", oct4, 600L)
        val second = repository.log("guitar", oct4, 1_800L)
        clock.millis = 5_000L

        val removed = repository.undoLast()

        assertEquals(second, removed?.id)
        assertEquals(1_800L, removed?.durationSeconds)
        assertEquals(listOf(first), repository.observeLive().first().map { it.id })
        assertEquals(5_000L, db.sessionDao().getById(second)!!.deletedAt)
        assertNull(repository.observeUndoTarget().first())
        assertNull(repository.undoLast())
    }

    @Test
    fun aTargetDeletedElsewhereIsIgnored() = runTest {
        val id = repository.log("guitar", oct4, 600L)

        repository.delete(id)

        assertNull(repository.observeUndoTarget().first())
        assertNull(repository.undoLast())
    }

    @Test
    fun anEditedTargetIsStillUndone() = runTest {
        val id = repository.log("guitar", oct4, 600L)
        repository.update(id, "guitar", oct4.minusDays(1), 900L)

        assertEquals(900L, repository.observeUndoTarget().first()?.durationSeconds)
        assertEquals(id, repository.undoLast()?.id)
        assertEquals(emptyList<Session>(), repository.observeLive().first())
    }

    @Test
    fun clearingTheTargetLeavesTheSession() = runTest {
        repository.log("guitar", oct4, 600L)

        lastCreated.clear()

        assertNull(repository.observeUndoTarget().first())
        assertNull(repository.undoLast())
        assertEquals(1, repository.observeLive().first().size)
    }
}
