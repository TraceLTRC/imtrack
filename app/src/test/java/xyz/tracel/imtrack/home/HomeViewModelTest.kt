package xyz.tracel.imtrack.home

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import xyz.tracel.imtrack.data.AppDatabase
import xyz.tracel.imtrack.data.ProjectRepository
import xyz.tracel.imtrack.projects.ProjectColors
import java.time.Clock
import kotlin.random.Random

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class HomeViewModelTest {
    private lateinit var db: AppDatabase
    private lateinit var repository: ProjectRepository
    private lateinit var viewModel: HomeViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java,
        ).allowMainThreadQueries().build()
        repository = ProjectRepository(db.projectDao(), Clock.systemUTC())
        viewModel = HomeViewModel(repository, Random(0))
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
        assertEquals(listOf("Guitar"), state.projects.map { it.name })
        assertEquals(ProjectColors[2], state.projects.single().color)
    }

    @Test
    fun onlyArchivedProjectsIsTheAllArchivedState() = runTest {
        collectState()
        repository.create(name = "Guitar", color = 0)
        val id = repository.observeAll().first().single().id

        repository.setArchived(id, archived = true)

        val state = viewModel.uiState.first { it is HomeUiState.Empty } as HomeUiState.Empty
        assertEquals(EmptyKind.AllArchived, state.kind)
    }

    @Test
    fun blankNamesAreNotAdded() = runTest {
        collectState()

        viewModel.addProject(name = "   ", color = ProjectColors[0])

        assertEquals(emptyList<Any>(), repository.observeAll().first())
    }

    private fun kotlinx.coroutines.test.TestScope.collectState() {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect {} }
    }
}
