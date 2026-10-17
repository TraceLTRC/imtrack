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
import xyz.tracel.imtrack.data.ProjectRepository
import xyz.tracel.imtrack.data.SessionRepository
import java.time.Instant
import java.time.LocalDate

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class PastPeriodsViewModelTest {
    /** Midday on Wed 7 Oct 2026, UTC. */
    private val clock = MutableClock(Instant.parse("2026-10-07T12:00:00Z").toEpochMilli())
    private lateinit var db: AppDatabase
    private lateinit var sessions: SessionRepository
    private lateinit var viewModel: PastPeriodsViewModel
    private lateinit var guitarId: String

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), AppDatabase::class.java)
            .allowMainThreadQueries().build()
        val projects = ProjectRepository(db.projectDao(), clock)
        sessions = SessionRepository(db.sessionDao(), clock, LastCreatedSession())
        runTest {
            projects.create("Guitar", 0)
            guitarId = projects.observeAll().first().single().id
        }
        viewModel = PastPeriodsViewModel(projects, sessions, clock, SavedStateHandle())
    }

    @After
    fun tearDown() {
        db.close()
        Dispatchers.resetMain()
    }

    @Test
    fun weeksByDefaultAndADeletedFirstSessionMovesTheCutOff() = runTest {
        sessions.delete(sessions.log(guitarId, LocalDate.of(2026, 8, 3), 600L))
        sessions.log(guitarId, LocalDate.of(2026, 9, 22), 1_200L)
        collectState()

        val state = state { it.rows.isNotEmpty() }

        assertEquals(PeriodKind.Week, state.period)
        assertEquals(listOf(PeriodLabel.LastWeek, PeriodLabel.WeekOf(LocalDate.of(2026, 9, 21), false)), state.rows.map { it.label })
        assertEquals(listOf(0L, 1_200L), state.rows.map { it.totalSeconds })
    }

    @Test
    fun oneRowOpensAtATimeAndSwitchingThePeriodClosesIt() = runTest {
        sessions.log(guitarId, LocalDate.of(2026, 9, 1), 60L)
        collectState()
        val rows = state { it.rows.size > 2 }.rows

        viewModel.toggle(rows[0])
        assertEquals(rows[0].range.start, state { it.expanded != null }.expanded)
        viewModel.toggle(rows[1])
        assertEquals(rows[1].range.start, state { it.expanded == rows[1].range.start }.expanded)
        viewModel.toggle(rows[1])
        assertNull(state { it.expanded == null }.expanded)

        viewModel.toggle(rows[0])
        viewModel.selectPeriod(PeriodKind.Month)
        val months = state { it.period == PeriodKind.Month }
        assertNull(months.expanded)
        assertEquals(listOf(60L), months.rows.map { it.totalSeconds })
    }

    @Test
    fun nothingBeforeThisPeriodIsEmpty() = runTest {
        sessions.log(guitarId, LocalDate.of(2026, 10, 6), 60L)
        collectState()

        viewModel.selectPeriod(PeriodKind.Year)

        assertEquals(emptyList<PastPeriod>(), state { it.period == PeriodKind.Year }.rows)
    }

    private suspend fun state(predicate: (PastPeriodsUiState) -> Boolean) = viewModel.uiState.filterNotNull().first(predicate)

    private fun TestScope.collectState() {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect {} }
    }
}
