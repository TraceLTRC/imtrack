package xyz.tracel.imtrack.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
class ProjectRepositoryTest {
    private val clock = MutableClock(1_234_567L)
    private lateinit var db: AppDatabase
    private lateinit var repository: ProjectRepository

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java,
        ).allowMainThreadQueries().build()
        repository = ProjectRepository(db.projectDao(), clock)
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun createStoresAnActiveProjectStampedByTheClock() = runTest {
        repository.create(name = "3D modelling", color = 0xFF00FF00.toInt())

        val project = repository.observeActive().first().single()
        assertEquals("3D modelling", project.name)
        assertEquals(0xFF00FF00.toInt(), project.color)
        assertFalse(project.archived)
        assertEquals(1_234_567L, project.createdAt)
        assertEquals(1_234_567L, project.updatedAt)
        assertNull(project.deletedAt)
        UUID.fromString(project.id)
    }

    @Test
    fun eachProjectGetsItsOwnId() = runTest {
        repository.create(name = "A", color = 0)
        repository.create(name = "B", color = 0)

        val (a, b) = repository.observeActive().first()
        assertNotEquals(a.id, b.id)
    }

    @Test
    fun createTrimsTheName() = runTest {
        repository.create(name = "  Guitar \n", color = 0)

        assertEquals("Guitar", repository.observeActive().first().single().name)
    }

    @Test
    fun updateChangesNameAndColourAndStampsUpdatedAt() = runTest {
        repository.create(name = "Guitar", color = 0)
        val created = repository.observeAll().first().single()
        clock.millis = 2_000_000L

        repository.update(created.id, name = " Bass guitar ", color = 7)

        val updated = repository.observeAll().first().single()
        assertEquals(created.copy(name = "Bass guitar", color = 7, updatedAt = 2_000_000L), updated)
    }

    @Test
    fun setArchivedMovesAProjectOutOfActiveAndBack() = runTest {
        repository.create(name = "Guitar", color = 0)
        val id = repository.observeAll().first().single().id
        clock.millis = 3_000_000L

        repository.setArchived(id, archived = true)

        assertTrue(repository.observeActive().first().isEmpty())
        val archived = repository.observeAll().first().single()
        assertTrue(archived.archived)
        assertEquals(3_000_000L, archived.updatedAt)

        repository.setArchived(id, archived = false)

        assertEquals(id, repository.observeActive().first().single().id)
    }

    @Test
    fun observeAllIncludesArchivedButNotSoftDeletedProjects() = runTest {
        repository.create(name = "Active", color = 0)
        repository.create(name = "Archived", color = 0)
        val archivedId = repository.observeAll().first().single { it.name == "Archived" }.id
        repository.setArchived(archivedId, archived = true)
        db.projectDao().insert(
            Project("deleted", "Deleted", 0, false, 0L, 0L, deletedAt = 1L),
        )

        assertEquals(setOf("Active", "Archived"), repository.observeAll().first().map { it.name }.toSet())
    }

    private class MutableClock(var millis: Long) : Clock() {
        override fun getZone(): ZoneId = ZoneOffset.UTC
        override fun withZone(zone: ZoneId?): Clock = this
        override fun instant(): Instant = Instant.ofEpochMilli(millis)
    }
}
