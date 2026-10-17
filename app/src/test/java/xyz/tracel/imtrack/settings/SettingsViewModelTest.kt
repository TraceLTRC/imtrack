package xyz.tracel.imtrack.settings

import android.net.Uri
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import xyz.tracel.imtrack.MutableClock
import xyz.tracel.imtrack.backup.BackupError
import xyz.tracel.imtrack.backup.BackupFiles
import xyz.tracel.imtrack.backup.BackupRepository
import xyz.tracel.imtrack.data.AppDatabase
import xyz.tracel.imtrack.data.LastCreatedSession
import xyz.tracel.imtrack.data.ProjectRepository
import xyz.tracel.imtrack.data.SessionRepository
import java.time.Instant
import java.time.LocalDate

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class SettingsViewModelTest {
    @get:Rule val folder = TemporaryFolder()

    /** Midday on Sun 4 Oct 2026, UTC. */
    private val clock = MutableClock(Instant.parse("2026-10-04T12:00:00Z").toEpochMilli())
    private val today = LocalDate.of(2026, 10, 4)
    private lateinit var db: AppDatabase
    private lateinit var projects: ProjectRepository
    private lateinit var sessions: SessionRepository
    private lateinit var backups: BackupRepository
    private lateinit var viewModel: SettingsViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java,
        ).allowMainThreadQueries().build()
        val lastCreated = LastCreatedSession()
        projects = ProjectRepository(db.projectDao(), clock)
        sessions = SessionRepository(db.sessionDao(), clock, lastCreated)
        backups = BackupRepository(db, clock, lastCreated)
        viewModel = SettingsViewModel(backups, BackupFiles(ApplicationProvider.getApplicationContext()), clock)
    }

    @After
    fun tearDown() {
        db.close()
        Dispatchers.resetMain()
    }

    @Test
    fun theSuggestedNameCarriesTodaysDate() {
        assertEquals("imtrack-backup-2026-10-04.json", viewModel.exportFileName())
    }

    @Test
    fun exportThenImportAfterChangesRestoresTheExportAndCountsOnlyLiveRows() = runTest {
        projects.create("Guitar", 1)
        val guitar = projects.observeAll().first().single()
        sessions.log(guitar.id, today, 1_800L)
        sessions.delete(sessions.log(guitar.id, today, 60L))
        val exported = backups.snapshot()
        val file = Uri.fromFile(folder.newFile("backup.json"))

        viewModel.export(file)
        assertEquals(SettingsMessage.Exported(1, 1), viewModel.messages.first())

        projects.create("Reading", 2)
        sessions.log(guitar.id, today, 900L)
        viewModel.import(file)
        assertEquals(ImportConfirm(projectCount = 1, sessionCount = 1), viewModel.importConfirm.first { it != null })
        viewModel.confirmImport()

        assertEquals(SettingsMessage.Imported(1, 1), viewModel.messages.first())
        assertNull(viewModel.importConfirm.value)
        assertEquals(exported.projects.toSet(), backups.snapshot().projects.toSet())
        assertEquals(exported.sessions.toSet(), backups.snapshot().sessions.toSet())
    }

    @Test
    fun cancellingTheConfirmChangesNothing() = runTest {
        val file = folder.newFile("empty.json")
        file.writeText("""{"formatVersion": 1, "exportedAt": 1, "projects": [], "sessions": []}""")
        projects.create("Guitar", 1)

        viewModel.import(Uri.fromFile(file))
        viewModel.importConfirm.first { it != null }
        viewModel.cancelImport()
        viewModel.confirmImport()

        assertNull(viewModel.importConfirm.value)
        assertEquals(listOf("Guitar"), projects.observeAll().first().map { it.name })
    }

    @Test
    fun anInvalidFileIsRejectedWithItsReasonAndChangesNothing() = runTest {
        val file = folder.newFile("foreign.json")
        file.writeText("""{"formatVersion": 7}""")
        projects.create("Guitar", 1)

        viewModel.import(Uri.fromFile(file))

        assertEquals(SettingsMessage.ImportFailed(BackupError.UnknownVersion("7")), viewModel.messages.first())
        assertNull(viewModel.importConfirm.value)
        assertEquals(listOf("Guitar"), projects.observeAll().first().map { it.name })
    }

    @Test
    fun aMissingFileFails() = runTest {
        viewModel.import(Uri.fromFile(folder.root.resolve("gone.json")))

        val message = viewModel.messages.first() as SettingsMessage.ImportFailed
        assertTrue(message.error is BackupError.Failed)
    }
}
