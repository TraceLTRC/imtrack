package xyz.tracel.imtrack.navigation

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class TabsViewModelTest {
    private val clock = MutableClock(1_000L)
    private val oct4 = LocalDate.of(2026, 10, 4)
    private lateinit var db: AppDatabase
    private lateinit var projects: ProjectRepository
    private lateinit var sessions: SessionRepository
    private lateinit var viewModel: TabsViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java,
        ).allowMainThreadQueries().build()
        projects = ProjectRepository(db.projectDao(), clock)
        sessions = SessionRepository(db.sessionDao(), clock, LastCreatedSession())
        viewModel = TabsViewModel(projects, sessions)
    }

    @After
    fun tearDown() {
        db.close()
        Dispatchers.resetMain()
    }

    @Test
    fun undoIsDisabledUntilASessionIsCreated() = runTest {
        collectState()
        assertFalse(viewModel.canUndo.value)

        projects.create("Guitar", 0)
        sessions.log(projects.observeAll().first().single().id, oct4, 1_800L)

        viewModel.canUndo.first { it }
    }

    @Test
    fun undoRemovesTheSessionNamesItAndDisablesItself() = runTest {
        collectState()
        projects.create("Guitar", 0)
        sessions.log(projects.observeAll().first().single().id, oct4, 1_790L)
        viewModel.canUndo.first { it }

        viewModel.undoLastSession()

        assertEquals(TabsMessage.Removed(1_790L, "Guitar"), viewModel.messages.first())
        viewModel.canUndo.first { !it }
        assertEquals(emptyList<Any>(), sessions.observeLive().first())
    }

    private fun TestScope.collectState() {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.canUndo.collect {} }
    }
}
