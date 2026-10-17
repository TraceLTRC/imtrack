package xyz.tracel.imtrack.home

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
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
import xyz.tracel.imtrack.MutableClock
import xyz.tracel.imtrack.data.AppDatabase
import xyz.tracel.imtrack.data.LastCreatedSession
import xyz.tracel.imtrack.data.ProjectRepository
import xyz.tracel.imtrack.data.SessionRepository
import xyz.tracel.imtrack.projects.ProjectColors
import xyz.tracel.imtrack.sessions.DayLabel
import java.time.Instant
import java.time.LocalDate
import kotlin.random.Random

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class HomeViewModelTest {
    /** Midday on Sun 4 Oct 2026, UTC. */
    private val clock = MutableClock(Instant.parse("2026-10-04T12:00:00Z").toEpochMilli())
    private val today = LocalDate.of(2026, 10, 4)
    private lateinit var db: AppDatabase
    private lateinit var projects: ProjectRepository
    private lateinit var sessions: SessionRepository
    private lateinit var viewModel: HomeViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java,
        ).allowMainThreadQueries().build()
        projects = ProjectRepository(db.projectDao(), clock)
        sessions = SessionRepository(db.sessionDao(), clock, LastCreatedSession())
        viewModel = HomeViewModel(projects, sessions, clock, Random(0))
    }

    @After
    fun tearDown() {
        db.close()
        Dispatchers.resetMain()
    }

    @Test
    fun noProjectsAtAllIsTheFirstLaunchState() = runTest {
        collectState()

        val state = viewModel.uiState.first { it !is HomeUiState.Loading }

        assertEquals(EmptyKind.FirstLaunch, (state as HomeUiState.Empty).kind)
        assertTrue(state.defaultColor in ProjectColors)
    }

    @Test
    fun addingAProjectShowsTheNormalState() = runTest {
        collectState()

        viewModel.addProject(name = " Guitar ", color = ProjectColors[2])

        val state = viewModel.uiState.first { it is HomeUiState.Projects } as HomeUiState.Projects
        assertEquals(listOf("Guitar"), state.rows.map { it.project.name })
        assertEquals(ProjectColors[2], state.rows.single().project.color)
        assertEquals(today, state.today)
    }

    @Test
    fun onlyArchivedProjectsIsTheAllArchivedState() = runTest {
        collectState()
        projects.create(name = "Guitar", color = 0)
        val id = projects.observeAll().first().single().id

        projects.setArchived(id, archived = true)

        val state = viewModel.uiState.first { it is HomeUiState.Empty } as HomeUiState.Empty
        assertEquals(EmptyKind.AllArchived, state.kind)
    }

    @Test
    fun blankNamesAreNotAdded() = runTest {
        collectState()

        viewModel.addProject(name = "   ", color = ProjectColors[0])

        assertEquals(emptyList<Any>(), projects.observeAll().first())
    }

    @Test
    fun rowsShowTodaysSecondsAndTheTileAddsUpAllOfToday() = runTest {
        collectState()
        projects.create(name = "Guitar", color = 0)
        projects.create(name = "Reading", color = 0)
        val (guitar, reading) = projects.observeAll().first().sortedBy { it.name }
        sessions.log(guitar.id, today, 1_800L)
        sessions.log(guitar.id, today.minusDays(1), 900L)
        sessions.log(reading.id, today, 2_700L)

        val state = viewModel.uiState.first { it is HomeUiState.Projects && it.todaySeconds == 4_500L }
            as HomeUiState.Projects

        assertEquals(mapOf("Guitar" to 1_800L, "Reading" to 2_700L), state.rows.associate { it.project.name to it.todaySeconds })
    }

    @Test
    fun loggingSavesWholeMinutesClosesTheSheetAndMovesTheProjectToTheTop() = runTest {
        collectState()
        projects.create(name = "Guitar", color = 0)
        projects.create(name = "Reading", color = 0)
        val reading = rows().first { it.size == 2 }.single { it.project.name == "Reading" }.project
        sessions.log(reading.id, today, 60L)
        val guitar = rows().first { it.first().project.name == "Reading" }.single { it.project.name == "Guitar" }.project
        clock.millis += 1_000L

        viewModel.openLog(guitar)
        projectsState { it.logSheet == guitar }
        viewModel.log(minutes = 30, date = today)

        // The order and the totals come from separate queries, so wait for both.
        val state = viewModel.uiState.first {
            it is HomeUiState.Projects && it.rows.first().project == guitar && it.rows.first().todaySeconds > 0L
        } as HomeUiState.Projects
        assertNull(state.logSheet)
        assertEquals(1_800L, state.rows.first().todaySeconds)
        assertEquals(1_860L, state.todaySeconds)
        assertEquals(HomeMessage.Logged(1_800L, "Guitar", DayLabel.Today), viewModel.messages.first())
    }

    @Test
    fun aBackDatedSessionNamesItsDay() = runTest {
        collectState()
        projects.create(name = "Guitar", color = 0)
        val guitar = rows().first { it.isNotEmpty() }.single().project
        val sep28 = LocalDate.of(2026, 9, 28)

        viewModel.openLog(guitar)
        viewModel.log(minutes = 45, date = today.minusDays(1))
        viewModel.openLog(guitar)
        viewModel.log(minutes = 15, date = sep28)

        val state = viewModel.uiState.first { it is HomeUiState.Projects && it.rows.single().todaySeconds == 0L }
        assertEquals(0L, (state as HomeUiState.Projects).todaySeconds)
        assertEquals(
            listOf(
                HomeMessage.Logged(2_700L, "Guitar", DayLabel.Yesterday),
                HomeMessage.Logged(900L, "Guitar", DayLabel.Date(sep28)),
            ),
            viewModel.messages.take(2).toList(),
        )
    }

    @Test
    fun invalidMinutesAndFutureDatesSaveNothing() = runTest {
        collectState()
        val messages = collectMessages()
        projects.create(name = "Guitar", color = 0)
        val guitar = rows().first { it.isNotEmpty() }.single().project

        viewModel.openLog(guitar)
        viewModel.log(minutes = 0, date = today)
        viewModel.log(minutes = 1_441, date = today)
        viewModel.log(minutes = 30, date = today.plusDays(1))

        projectsState { it.logSheet == guitar }
        assertEquals(emptyList<HomeMessage>(), messages)
        assertEquals(emptyMap<String, Long>(), sessions.observeTotalsOn(today).first())
    }

    @Test
    fun dismissingTheLogSheetSavesNothing() = runTest {
        collectState()
        projects.create(name = "Guitar", color = 0)
        val guitar = rows().first { it.isNotEmpty() }.single().project

        viewModel.openLog(guitar)
        viewModel.dismissLog()
        viewModel.log(minutes = 30, date = today)

        projectsState { it.logSheet == null }
        assertEquals(emptyList<Any>(), sessions.observeLatestPerProject().first())
    }

    /** Waits for a normal-Home state matching [predicate]: Room re-emits on its own threads. */
    private suspend fun projectsState(predicate: (HomeUiState.Projects) -> Boolean) =
        viewModel.uiState.filterIsInstance<HomeUiState.Projects>().first(predicate)

    private fun rows() = viewModel.uiState.filterIsInstance<HomeUiState.Projects>().map { it.rows }

    private fun TestScope.collectState() {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect {} }
    }

    /** Only for checking that nothing is sent: a message sent after a Room write arrives on another thread. */
    private fun TestScope.collectMessages(): List<HomeMessage> {
        val messages = mutableListOf<HomeMessage>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.messages.collect { messages += it } }
        return messages
    }
}
