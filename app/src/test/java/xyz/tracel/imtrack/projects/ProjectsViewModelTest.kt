package xyz.tracel.imtrack.projects

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
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import xyz.tracel.imtrack.data.AppDatabase
import xyz.tracel.imtrack.data.ProjectRepository
import java.time.Clock
import kotlin.random.Random

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class ProjectsViewModelTest {
    private lateinit var db: AppDatabase
    private lateinit var repository: ProjectRepository
    private lateinit var viewModel: ProjectsViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java,
        ).allowMainThreadQueries().build()
        repository = ProjectRepository(db.projectDao(), Clock.systemUTC())
        viewModel = ProjectsViewModel(repository, Random(0))
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
