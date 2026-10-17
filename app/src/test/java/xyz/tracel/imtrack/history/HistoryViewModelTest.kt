package xyz.tracel.imtrack.history

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import xyz.tracel.imtrack.MutableClock
import xyz.tracel.imtrack.data.AppDatabase
import xyz.tracel.imtrack.data.Project
import xyz.tracel.imtrack.data.ProjectRepository
import xyz.tracel.imtrack.data.SessionRepository
import java.time.Instant
import java.time.LocalDate

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class HistoryViewModelTest {
    /** Midday on Sun 4 Oct 2026, UTC. */
    private val clock = MutableClock(Instant.parse("2026-10-04T12:00:00Z").toEpochMilli())
    private val today = LocalDate.of(2026, 10, 4)
    private lateinit var db: AppDatabase
    private lateinit var projects: ProjectRepository
    private lateinit var sessions: SessionRepository
    private lateinit var viewModel: HistoryViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java,
        ).allowMainThreadQueries().build()
        projects = ProjectRepository(db.projectDao(), clock)
        sessions = SessionRepository(db.sessionDao(), clock)
        viewModel = HistoryViewModel(projects, sessions, clock)
    }

    @After
    fun tearDown() {
        db.close()
        Dispatchers.resetMain()
    }

    @Test
    fun anEditWithUnchangedMinutesKeepsTheExactSeconds() = runTest {
        collectState()
        val guitar = createProject("Guitar")
        // A Stopwatch Session: 20 min 34 s, shown as 21 min.
        sessions.log(guitar.id, today, 1_234L)
        val row = loaded { it.days.isNotEmpty() }.days.single().rows.single()

        viewModel.openEdit(row.session)
        loaded { it.editSheet != null }
        viewModel.save(projectId = guitar.id, minutes = 21, date = today.minusDays(1))

        val state = loaded { it.days.singleOrNull()?.date == today.minusDays(1) }
        assertNull(state.editSheet)
        assertEquals(1_234L, state.days.single().totalSeconds)
        assertEquals(HistoryMessage.Updated, viewModel.messages.first())
    }

    @Test
    fun changedMinutesStoreWholeMinutesAndMoveTheSessionToItsNewProject() = runTest {
        collectState()
        val guitar = createProject("Guitar")
        val reading = createProject("Reading")
        sessions.log(guitar.id, today, 1_234L)
        val session = loaded { it.days.isNotEmpty() }.days.single().rows.single().session

        viewModel.openEdit(session)
        loaded { it.editSheet != null }
        viewModel.save(projectId = reading.id, minutes = 45, date = today)

        val row = loaded { it.days.singleOrNull()?.rows?.single()?.project == reading }.days.single().rows.single()
        assertEquals(2_700L, row.session.durationSeconds)
        assertEquals(mapOf(reading.id to 2_700L), sessions.observeTotalsOn(today).first())
    }

    @Test
    fun theSheetOffersActiveProjectsPlusTheSessionsOwnArchivedProject() = runTest {
        collectState()
        val guitar = createProject("Guitar")
        val reading = createProject("reading")
        val blender = createProject("Blender")
        val calligraphy = createProject("Calligraphy")
        sessions.log(blender.id, today, 60L)
        sessions.log(guitar.id, today, 60L)
        projects.setArchived(blender.id, archived = true)
        projects.setArchived(calligraphy.id, archived = true)
        val rows = loaded { state -> state.days.flatMap { it.rows }.any { it.project.archived } }.days.single().rows

        viewModel.openEdit(rows.single { it.project.id == blender.id }.session)
        val archivedOwn = loaded { it.editSheet?.session?.projectId == blender.id }.editSheet!!
        viewModel.openEdit(rows.single { it.project.id == guitar.id }.session)
        val activeOwn = loaded { it.editSheet?.session?.projectId == guitar.id }.editSheet!!

        assertEquals(listOf("Blender", "Guitar", "reading"), archivedOwn.projects.map { it.name })
        assertEquals(listOf("Guitar", "reading"), activeOwn.projects.map { it.name })
        assertEquals(listOf(guitar.id, reading.id), activeOwn.projects.map { it.id })
    }

    @Test
    fun deletingSoftDeletesTheSessionAndClosesTheSheet() = runTest {
        collectState()
        val guitar = createProject("Guitar")
        sessions.log(guitar.id, today, 600L)
        val session = loaded { it.days.isNotEmpty() }.days.single().rows.single().session

        viewModel.openEdit(session)
        loaded { it.editSheet != null }
        viewModel.delete()

        val state = loaded { it.days.isEmpty() }
        assertNull(state.editSheet)
        assertEquals(emptyMap<String, Long>(), sessions.observeTotalsOn(today).first())
        assertEquals(HistoryMessage.Deleted, viewModel.messages.first())
        assertNotNull(db.sessionDao().getById(session.id)!!.deletedAt)
    }

    @Test
    fun invalidMinutesFutureDatesAndDismissingSaveNothing() = runTest {
        collectState()
        val guitar = createProject("Guitar")
        sessions.log(guitar.id, today, 600L)
        val session = loaded { it.days.isNotEmpty() }.days.single().rows.single().session

        viewModel.openEdit(session)
        loaded { it.editSheet != null }
        viewModel.save(guitar.id, minutes = 0, date = today)
        viewModel.save(guitar.id, minutes = 1_441, date = today)
        viewModel.save(guitar.id, minutes = 30, date = today.plusDays(1))
        loaded { it.editSheet != null }
        viewModel.dismissEdit()
        viewModel.save(guitar.id, minutes = 30, date = today)

        loaded { it.editSheet == null }
        assertEquals(session, db.sessionDao().getById(session.id))
    }

    private suspend fun createProject(name: String): Project {
        projects.create(name, color = 0)
        return projects.observeAll().first { all -> all.any { it.name == name } }.single { it.name == name }
    }

    /** Waits for a loaded state matching [predicate]: Room re-emits on its own threads. */
    private suspend fun loaded(predicate: (HistoryUiState.Loaded) -> Boolean) =
        viewModel.uiState.filterIsInstance<HistoryUiState.Loaded>().first(predicate)

    private fun TestScope.collectState() {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect {} }
    }
}
