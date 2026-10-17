package xyz.tracel.imtrack.projects

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import xyz.tracel.imtrack.data.AppDatabase
import xyz.tracel.imtrack.data.ProjectRepository
import xyz.tracel.imtrack.MutableClock
import xyz.tracel.imtrack.data.LastCreatedSession
import xyz.tracel.imtrack.data.SessionRepository
import xyz.tracel.imtrack.data.StopwatchRepository
import xyz.tracel.imtrack.sessions.DayLabel
import xyz.tracel.imtrack.stopwatch.InAppStopwatch
import java.time.Instant
import java.time.LocalDate
import kotlin.random.Random

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class ProjectsViewModelTest {
    /** Midday on Sun 4 Oct 2026, UTC. */
    private val clock = MutableClock(Instant.parse("2026-10-04T12:00:00Z").toEpochMilli())
    private val today = LocalDate.of(2026, 10, 4)
    private lateinit var db: AppDatabase
    private lateinit var repository: ProjectRepository
    private lateinit var sessions: SessionRepository
    private lateinit var stopwatch: StopwatchRepository
    private lateinit var viewModel: ProjectsViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java,
        ).allowMainThreadQueries().build()
        repository = ProjectRepository(db.projectDao(), clock)
        sessions = SessionRepository(db.sessionDao(), clock, LastCreatedSession())
        stopwatch = StopwatchRepository(db, db.runningStopwatchDao(), sessions, clock)
        viewModel = ProjectsViewModel(repository, InAppStopwatch(stopwatch, repository, clock), Random(0))
    }

    @After
    fun tearDown() {
        db.close()
        Dispatchers.resetMain()
    }

    @Test
    fun newSheetDefaultsToAColourNoActiveProjectUses() = runTest {
        collectState()
        ProjectColors.dropLast(1).forEach { repository.create(name = "P", color = it) }
        viewModel.uiState.first { it.list.active.size == 7 }

        viewModel.openNew()

        val sheet = viewModel.uiState.first { it.sheet != null }.sheet as ProjectSheet.New
        assertEquals(ProjectColors.last(), sheet.defaultColor)
    }

    @Test
    fun savingTheNewSheetCreatesAProjectAndClosesTheSheet() = runTest {
        collectState()
        viewModel.openNew()
        viewModel.uiState.first { it.sheet != null }

        viewModel.save(name = "Guitar", color = ProjectColors[1])

        val state = viewModel.uiState.first { it.list.active.isNotEmpty() }
        assertNull(state.sheet)
        assertEquals("Guitar", state.list.active.single().name)
        assertEquals(ProjectColors[1], state.list.active.single().color)
    }

    @Test
    fun savingTheEditSheetRenamesAndRecolours() = runTest {
        collectState()
        repository.create(name = "Guitar", color = ProjectColors[0])
        val project = viewModel.uiState.first { it.list.active.isNotEmpty() }.list.active.single()
        viewModel.openEdit(project)

        viewModel.save(name = "Bass", color = ProjectColors[4])

        val edited = viewModel.uiState.first { it.list.active.single().name == "Bass" }
        assertEquals(ProjectColors[4], edited.list.active.single().color)
        assertNull(edited.sheet)
    }

    @Test
    fun archiveTogglesTheEditedProjectAndReportsIt() = runTest {
        collectState()
        val messages = mutableListOf<ProjectsMessage>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.messages.collect { messages += it } }
        repository.create(name = "Guitar", color = ProjectColors[0])
        val project = viewModel.uiState.first { it.list.active.isNotEmpty() }.list.active.single()

        viewModel.openEdit(project)
        viewModel.toggleArchived()

        val archived = viewModel.uiState.first { it.list.archived.isNotEmpty() }
        assertNull(archived.sheet)
        assertTrue(archived.list.active.isEmpty())

        viewModel.openEdit(archived.list.archived.single())
        viewModel.toggleArchived()

        viewModel.uiState.first { it.list.active.isNotEmpty() }
        assertEquals(listOf(ProjectsMessage.Archived("Guitar"), ProjectsMessage.Unarchived("Guitar")), messages)
    }

    @Test
    fun archivingTheRunningProjectStopsAndSavesItsStopwatchFirst() = runTest {
        collectState()
        repository.create(name = "Guitar", color = ProjectColors[0])
        val project = viewModel.uiState.first { it.list.active.isNotEmpty() }.list.active.single()
        stopwatch.start(project.id)
        clock.millis += 600_000L

        viewModel.openEdit(project)
        viewModel.toggleArchived()

        assertEquals(
            listOf(ProjectsMessage.Logged(600L, "Guitar", DayLabel.Today), ProjectsMessage.Archived("Guitar")),
            viewModel.messages.take(2).toList(),
        )
        assertNull(stopwatch.observeRunning().first())
        assertEquals(mapOf(project.id to 600L), sessions.observeTotalsOn(today).first())
    }

    @Test
    fun archivingTheRunningProjectUnderAMinuteAsks() = runTest {
        collectState()
        repository.create(name = "Guitar", color = ProjectColors[0])
        val project = viewModel.uiState.first { it.list.active.isNotEmpty() }.list.active.single()
        stopwatch.start(project.id)
        clock.millis += 20_000L

        viewModel.openEdit(project)
        viewModel.toggleArchived()

        assertEquals(20L, viewModel.underAMinute.first { it != null }!!.elapsedSeconds)
        // The Project is archived only once the dialog is answered.
        assertEquals(listOf(project), viewModel.uiState.value.list.active)

        viewModel.keepUnderAMinute()

        assertEquals(
            listOf(ProjectsMessage.Logged(20L, "Guitar", DayLabel.Today), ProjectsMessage.Archived("Guitar")),
            viewModel.messages.take(2).toList(),
        )
        viewModel.uiState.first { it.list.archived.isNotEmpty() }
        assertNull(stopwatch.observeRunning().first())
        assertEquals(mapOf(project.id to 20L), sessions.observeTotalsOn(today).first())
    }

    @Test
    fun archivingAnotherProjectLeavesTheStopwatchRunning() = runTest {
        collectState()
        repository.create(name = "Guitar", color = ProjectColors[0])
        repository.create(name = "Reading", color = ProjectColors[1])
        val (guitar, reading) = viewModel.uiState.first { it.list.active.size == 2 }.list.active
        stopwatch.start(reading.id)

        viewModel.openEdit(guitar)
        viewModel.toggleArchived()

        viewModel.uiState.first { it.list.archived.isNotEmpty() }
        assertEquals(reading.id, stopwatch.observeRunning().first()?.projectId)
    }

    @Test
    fun dismissingTheSheetSavesNothing() = runTest {
        collectState()
        viewModel.openNew()
        viewModel.uiState.first { it.sheet != null }

        viewModel.dismissSheet()

        assertNull(viewModel.uiState.value.sheet)
        assertTrue(repository.observeAll().first().isEmpty())
    }

    private fun TestScope.collectState() {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect {} }
    }
}
