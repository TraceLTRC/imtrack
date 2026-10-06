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
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
class ProjectRepositoryTest {
    private val clock = Clock.fixed(Instant.ofEpochMilli(1_234_567L), ZoneOffset.UTC)
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
}
