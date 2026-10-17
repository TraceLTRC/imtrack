package xyz.tracel.imtrack.backup

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
import xyz.tracel.imtrack.data.AppDatabase
import xyz.tracel.imtrack.data.LastCreatedSession
import xyz.tracel.imtrack.data.ProjectRepository
import xyz.tracel.imtrack.data.SessionRepository
import java.time.LocalDate

@RunWith(RobolectricTestRunner::class)
class BackupRepositoryTest {
    private val clock = MutableClock(1_000L)
    private val oct4 = LocalDate.of(2026, 10, 4)
    private lateinit var db: AppDatabase
    private lateinit var lastCreated: LastCreatedSession
    private lateinit var projects: ProjectRepository
    private lateinit var sessions: SessionRepository
    private lateinit var backups: BackupRepository

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java,
        ).allowMainThreadQueries().build()
        lastCreated = LastCreatedSession()
        projects = ProjectRepository(db.projectDao(), clock)
        sessions = SessionRepository(db.sessionDao(), clock, lastCreated)
        backups = BackupRepository(db, clock, lastCreated)
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun exportThenLogThenImportRestoresTheExportedStateExactly() = runTest {
        projects.create("Guitar", 1)
        projects.create("Blender donut course", 2)
        val (donut, guitar) = projects.observeAll().first().sortedBy { it.name }
        clock.millis = 2_000L
        projects.setArchived(donut.id, archived = true)
        sessions.log(guitar.id, oct4, 1_234L)
        val deleted = sessions.log(donut.id, oct4.minusDays(1), 600L)
        clock.millis = 3_000L
        sessions.delete(deleted)
        val exported = backups.snapshot()
        val file = BackupFormat.encode(exported)

        clock.millis = 4_000L
        projects.create("Reading", 3)
        sessions.log(guitar.id, oct4, 60L)
        sessions.update(sessions.observeLive().first().first().id, guitar.id, oct4, 120L)
        val restored = (BackupFormat.decode(file) as BackupDecoding.Valid).backup
        backups.replaceAll(restored)

        val now = backups.snapshot()
        assertEquals(exported.projects.toSet(), now.projects.toSet())
        assertEquals(exported.sessions.toSet(), now.sessions.toSet())
        assertEquals(2, now.projects.size)
        assertEquals(1, now.liveSessionCount)
        assertEquals(3_000L, now.sessions.single { it.id == deleted }.deletedAt)
        assertEquals(3_000L, exported.exportedAt)
    }

    @Test
    fun snapshotIncludesArchivedProjectsAndDeletedSessions() = runTest {
        projects.create("Guitar", 1)
        val guitar = projects.observeAll().first().single()
        projects.setArchived(guitar.id, archived = true)
        sessions.delete(sessions.log(guitar.id, oct4, 60L))

        val backup = backups.snapshot()

        assertEquals(listOf(true), backup.projects.map { it.archived })
        assertEquals(1, backup.sessions.size)
        assertEquals(0, backup.liveSessionCount)
    }

    @Test
    fun importClearsTheUndoTarget() = runTest {
        projects.create("Guitar", 1)
        sessions.log(projects.observeAll().first().single().id, oct4, 60L)
        val backup = backups.snapshot()

        backups.replaceAll(backup)

        assertNull(sessions.observeUndoTarget().first())
    }
}
