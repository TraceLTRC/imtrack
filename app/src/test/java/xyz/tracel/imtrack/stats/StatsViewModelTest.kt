package xyz.tracel.imtrack.stats

import androidx.lifecycle.SavedStateHandle
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
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
import xyz.tracel.imtrack.data.Project
import xyz.tracel.imtrack.data.ProjectRepository
import xyz.tracel.imtrack.data.SessionRepository
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class StatsViewModelTest {
    /** Midday on Wed 7 Oct 2026, UTC. */
    private val clock = MutableClock(Instant.parse("2026-10-07T12:00:00Z").toEpochMilli())
    private val today = LocalDate.of(2026, 10, 7)
    private val handle = SavedStateHandle()
    private lateinit var db: AppDatabase
    private lateinit var projects: ProjectRepository
    private lateinit var sessions: SessionRepository
    private lateinit var viewModel: StatsViewModel
    private lateinit var guitar: Project
    private lateinit var reading: Project
    private lateinit var blender: Project

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), AppDatabase::class.java)
            .allowMainThreadQueries().build()
        projects = ProjectRepository(db.projectDao(), clock)
        sessions = SessionRepository(db.sessionDao(), clock, LastCreatedSession())
        runTest {
            listOf("Reading", "Guitar", "Blender").forEach { projects.create(it, 0) }
            val all = projects.observeAll().first().associateBy { it.name }
            projects.setArchived(all.getValue("Blender").id, archived = true)
            guitar = all.getValue("Guitar")
            reading = all.getValue("Reading")
            blender = projects.get(all.getValue("Blender").id)!!
        }
        viewModel = newViewModel()
    }

    @After
    fun tearDown() {
        db.close()
        Dispatchers.resetMain()
    }

    @Test
    fun theWeekSoFarIsComparedWithLastWeekUpToTheSameWeekday() = runTest {
        sampleWeek()
        collectState()

        val state = state { it.totalSeconds == 2_700L }

        assertEquals(PeriodKind.Week, state.period)
        assertEquals(Comparison(previousSeconds = 3_600L, previousLabel = PeriodLabel.LastWeek), state.comparison)
        assertEquals(
            listOf(ByProjectRow(guitar, 1_800L), ByProjectRow(reading, 600L), ByProjectRow(blender, 300L)),
            state.byProject,
        )
        assertEquals(Streak(current = 1, longest = 1), state.streak)
    }

    @Test
    fun chipsAreActiveProjectsAToZThenArchivedOnes() = runTest {
        collectState()

        assertEquals(listOf(guitar, reading, blender), state { it.projects.size == 3 }.projects)
    }

    @Test
    fun noTimeInThePreviousPeriodMeansNoComparison() = runTest {
        sampleWeek()
        collectState()

        viewModel.selectPeriod(PeriodKind.Year)

        val state = state { it.period == PeriodKind.Year && it.totalSeconds > 0L }
        assertNull(state.comparison)
        assertEquals(2_700L + 3_600L + 9_000L, state.totalSeconds)
    }

    @Test
    fun aMonthIsComparedWithTheSameDaysOfTheMonthBefore() = runTest {
        sampleWeek()
        sessions.log(guitar.id, LocalDate.of(2026, 9, 7), 120L)
        sessions.log(guitar.id, LocalDate.of(2026, 9, 8), 999L)
        collectState()

        viewModel.selectPeriod(PeriodKind.Month)

        val state = state { it.period == PeriodKind.Month && it.comparison != null }
        // 1–7 Oct, Thu 1 Oct included.
        assertEquals(2_700L + 9_000L, state.totalSeconds)
        assertEquals(Comparison(120L, PeriodLabel.Month(YearMonth.of(2026, 9), withYear = false)), state.comparison)
    }

    @Test
    fun aProjectFilterNarrowsTheTotalAndHidesStreakAndByProject() = runTest {
        sampleWeek()
        collectState()

        viewModel.selectProject(guitar)

        val state = state { it.selected == guitar }
        assertEquals(1_800L, state.totalSeconds)
        assertEquals(Comparison(3_600L, PeriodLabel.LastWeek), state.comparison)
        assertNull(state.streak)
        assertEquals(emptyList<ByProjectRow>(), state.byProject)

        viewModel.selectProject(null)
        assertEquals(2_700L, state { it.selected == null }.totalSeconds)
    }

    @Test
    fun anArchivedProjectCanBeSelectedAndCountsInTotals() = runTest {
        sampleWeek()
        collectState()

        viewModel.selectProject(blender)

        assertEquals(300L, state { it.selected == blender }.totalSeconds)
    }

    @Test
    fun theSelectionSurvivesANewViewModelOnTheSameSavedState() = runTest {
        viewModel.selectPeriod(PeriodKind.Month)
        viewModel.selectProject(reading)

        viewModel = newViewModel()
        collectState()

        val state = state { it.selected != null }
        assertEquals(PeriodKind.Month, state.period)
        assertEquals(reading, state.selected)
    }

    @Test
    fun aSelectedProjectThatDisappearsFallsBackToAllProjects() = runTest {
        collectState()
        viewModel.selectProject(reading)
        state { it.selected == reading }

        // What Import does.
        db.sessionDao().deleteAll()
        db.projectDao().deleteAll()

        assertNull(state { it.projects.isEmpty() }.selected)
        assertNull(handle.get<String>("projectId"))
    }

    @Test
    fun aNewWeekStartsAtMidnightOnMonday() = runTest {
        clock.millis = Instant.parse("2026-10-11T23:59:00Z").toEpochMilli()
        sessions.log(guitar.id, LocalDate.of(2026, 10, 5), 300L)
        sessions.log(guitar.id, LocalDate.of(2026, 10, 11), 600L)
        viewModel = newViewModel()
        collectState()
        assertEquals(900L, state { it.totalSeconds > 0L }.totalSeconds)

        clock.millis = Instant.parse("2026-10-12T00:00:01Z").toEpochMilli()

        // Monday is compared with last Monday only.
        val monday = state { it.totalSeconds == 0L && it.comparison != null }
        assertEquals(Comparison(300L, PeriodLabel.LastWeek), monday.comparison)
    }

    @Test
    fun theHeatmapCoversSixMonthsAndFollowsTheFilter() = runTest {
        sampleWeek()
        sessions.log(guitar.id, LocalDate.of(2026, 4, 12), 600L) // The day before the first column.
        collectState()

        val all = state { it.heatmap.seconds.isNotEmpty() }.heatmap
        assertEquals(LocalDate.of(2026, 4, 13), all.layout.mondays.first())
        assertEquals(
            mapOf(
                LocalDate.of(2026, 9, 28) to 3_600L,
                LocalDate.of(2026, 10, 1) to 9_000L,
                LocalDate.of(2026, 10, 5) to 1_800L,
                today to 900L,
            ),
            all.seconds,
        )

        viewModel.selectProject(reading)

        assertEquals(
            mapOf(LocalDate.of(2026, 10, 1) to 9_000L, today to 600L),
            state { it.selected == reading && it.heatmap.seconds[today] == 600L }.heatmap.seconds,
        )
    }

    @Test
    fun deletedSessionsAreLeftOut() = runTest {
        sessions.log(guitar.id, today, 600L)
        sessions.delete(sessions.log(guitar.id, today, 6_000L))
        collectState()

        assertEquals(listOf(ByProjectRow(guitar, 600L)), state { it.totalSeconds > 0L }.byProject)
    }

    /**
     * This week (from Mon 5 Oct): Guitar 30 min, Reading 10 min, archived Blender 5 min.
     * Last week: Mon 28 Sep 1 h (before the same point), Thu 1 Oct 2.5 h (after it).
     */
    private suspend fun sampleWeek() {
        sessions.log(guitar.id, LocalDate.of(2026, 10, 5), 1_800L)
        sessions.log(reading.id, today, 600L)
        sessions.log(blender.id, today, 300L)
        sessions.log(guitar.id, LocalDate.of(2026, 9, 28), 3_600L)
        sessions.log(reading.id, LocalDate.of(2026, 10, 1), 9_000L)
    }

    private fun newViewModel() =
        StatsViewModel(projects, sessions, StreakRepository(sessions, clock), clock, handle)

    private suspend fun state(predicate: (StatsUiState) -> Boolean) = viewModel.uiState.filterNotNull().first(predicate)

    private fun TestScope.collectState() {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect {} }
    }
}
