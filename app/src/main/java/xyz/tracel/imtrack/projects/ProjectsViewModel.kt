package xyz.tracel.imtrack.projects

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import xyz.tracel.imtrack.data.Project
import xyz.tracel.imtrack.data.ProjectRepository
import javax.inject.Inject
import kotlin.random.Random

/** What the Project sheet is open for. */
sealed interface ProjectSheet {
    data class New(val defaultColor: Int) : ProjectSheet
    data class Edit(val project: Project) : ProjectSheet
}

/** Snackbar confirmations; the screen turns them into copy. */
sealed interface ProjectsMessage {
    data class Archived(val name: String) : ProjectsMessage
    data class Unarchived(val name: String) : ProjectsMessage
}

data class ProjectsUiState(
    val list: ProjectsPageList = ProjectsPageList(active = emptyList(), archived = emptyList()),
    val sheet: ProjectSheet? = null,
)

@HiltViewModel
class ProjectsViewModel @Inject constructor(
    private val projectRepository: ProjectRepository,
    private val random: Random,
) : ViewModel() {
    private val sheet = MutableStateFlow<ProjectSheet?>(null)
    private val _messages = Channel<ProjectsMessage>(Channel.BUFFERED)
    val messages: Flow<ProjectsMessage> = _messages.receiveAsFlow()

    val uiState: StateFlow<ProjectsUiState> =
        combine(projectRepository.observeAll(), sheet) { projects, sheet ->
            ProjectsUiState(list = sortForProjectsPage(projects), sheet = sheet)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ProjectsUiState())

    fun openNew() {
        viewModelScope.launch {
            val activeColors = projectRepository.observeActive().first().map { it.color }
            sheet.value = ProjectSheet.New(defaultColor = pickDefaultColor(activeColors, random))
        }
    }

    fun openEdit(project: Project) {
        sheet.value = ProjectSheet.Edit(project)
    }

    fun dismissSheet() {
        sheet.value = null
    }

    fun save(name: String, color: Int) {
        val target = sheet.value ?: return
        if (name.isBlank()) return
        sheet.value = null
        viewModelScope.launch {
            when (target) {
                is ProjectSheet.New -> projectRepository.create(name, color)
                is ProjectSheet.Edit -> projectRepository.update(target.project.id, name, color)
            }
        }
    }

    /** Archives or unarchives the Project being edited. */
    fun toggleArchived() {
        val project = (sheet.value as? ProjectSheet.Edit)?.project ?: return
        sheet.value = null
        viewModelScope.launch {
            val archive = !project.archived
            projectRepository.setArchived(project.id, archive)
            _messages.send(
                if (archive) ProjectsMessage.Archived(project.name) else ProjectsMessage.Unarchived(project.name),
            )
        }
    }
}
