package xyz.tracel.imtrack.data

import android.database.sqlite.SQLiteConstraintException
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.LocalDate

@RunWith(RobolectricTestRunner::class)
class DaoTest {
    private lateinit var db: AppDatabase
    private lateinit var projectDao: ProjectDao
    private lateinit var sessionDao: SessionDao

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java,
        ).allowMainThreadQueries().build()
        projectDao = db.projectDao()
        sessionDao = db.sessionDao()
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun insertedProjectIsEmittedByObserveActive() = runTest {
        val project = project("p1")
        val emitted = async { projectDao.observeActive().first { it.isNotEmpty() } }

        projectDao.insert(project)

        assertEquals(listOf(project), emitted.await())
    }

    @Test
    fun observeActiveExcludesArchivedAndSoftDeletedProjects() = runTest {
        val active = project("active")
        projectDao.insert(active)
        projectDao.insert(project("archived").copy(archived = true))
        projectDao.insert(project("deleted").copy(deletedAt = 3_000L))

        assertEquals(listOf(active), projectDao.observeActive().first())
    }

    @Test
    fun sessionRoundTripsWithItsDateIntact() = runTest {
        projectDao.insert(project("p1"))
        val session = Session(
            id = "s1",
            projectId = "p1",
            date = LocalDate.of(2026, 10, 6),
            durationSeconds = 1_500L,
            createdAt = 1_000L,
            updatedAt = 2_000L,
            deletedAt = null,
        )

        sessionDao.insert(session)

        assertEquals(session, sessionDao.getById("s1"))
    }

    @Test
    fun insertingSessionWithUnknownProjectFails() = runTest {
        val orphan = Session(
            id = "s1",
            projectId = "missing",
            date = LocalDate.of(2026, 10, 6),
            durationSeconds = 60L,
            createdAt = 1_000L,
            updatedAt = 1_000L,
            deletedAt = null,
        )

        val error = assertThrows(SQLiteConstraintException::class.java) {
            runBlocking { sessionDao.insert(orphan) }
        }
        assertTrue(error.message.orEmpty().contains("FOREIGN KEY"))
    }

    private fun project(id: String) = Project(
        id = id,
        name = "Project $id",
        color = 0xFF3366CC.toInt(),
        archived = false,
        createdAt = 1_000L,
        updatedAt = 2_000L,
        deletedAt = null,
    )
}
