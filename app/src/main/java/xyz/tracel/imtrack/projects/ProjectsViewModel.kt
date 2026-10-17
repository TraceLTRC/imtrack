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
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import xyz.tracel.imtrack.data.Project
import xyz.tracel.imtrack.data.ProjectRepository
import xyz.tracel.imtrack.sessions.DayLabel
import xyz.tracel.imtrack.stopwatch.InAppStop
import xyz.tracel.imtrack.stopwatch.InAppStopwatch
import xyz.tracel.imtrack.stopwatch.LoggedSession
import xyz.tracel.imtrack.stopwatch.UnderAMinute
import xyz.tracel.imtrack.stopwatch.UnderAMinuteChoice
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

    /** Archiving the running Project stopped and saved its Stopwatch. */
    data class Logged(val durationSeconds: Long, val projectName: String, val day: DayLabel) : ProjectsMessage
}

data class ProjectsUiState(
    val list: ProjectsPageList = ProjectsPageList(active = emptyList(), archived = emptyList()),
    val sheet: ProjectSheet? = null,
)

@HiltViewModel
class ProjectsViewModel @Inject constructor(
    private val projectRepository: ProjectRepository,
    private val inAppStopwatch: InAppStopwatch,
    private val random: Random,
) : ViewModel() {
    private val sheet = MutableStateFlow<ProjectSheet?>(null)
    private val _messages = Channel<ProjectsMessage>(Channel.BUFFERED)
    val messages: Flow<ProjectsMessage> = _messages.receiveAsFlow()

    /** The under-a-minute dialog, when archiving stopped a short Stopwatch. */
    val underAMinute: StateFlow<UnderAMinute?> = inAppStopwatch.underAMinute

    /** The Project to archive once the under-a-minute dialog is answered. */
    private var pendingArchive: Project? = null

    /** Archiving and the dialog's answers run one at a time. */
    private val stopwatchActions = Mutex()

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

    /**
     * Archives or unarchives the Project being edited. Archiving first stops its Stopwatch by the in-app
     * rules; if that asks about a run under a minute, the Project is archived once the dialog is answered.
     */
    fun toggleArchived() {
        val project = (sheet.value as? ProjectSheet.Edit)?.project ?: return
        sheet.value = null
        stopwatchAction {
            val archive = !project.archived
            if (archive && inAppStopwatch.isRunning(project)) {
                when (val stop = inAppStopwatch.stop()) {
                    is InAppStop.Logged -> _messages.send(stop.session.toMessage())
                    InAppStop.Asking -> {
                        pendingArchive = project
                        return@stopwatchAction
                    }
                    InAppStop.NothingRunning -> Unit
                }
            }
            setArchived(project, archive)
        }
    }

    fun keepUnderAMinute() = stopwatchAction {
        inAppStopwatch.keep()?.let { answered(it) }
    }

    fun discardUnderAMinute() = stopwatchAction {
        inAppStopwatch.discard()?.let { answered(it) }
    }

    private suspend fun answered(choice: UnderAMinuteChoice) {
        choice.logged?.let { _messages.send(it.toMessage()) }
        pendingArchive?.let { setArchived(it, archive = true) }
        pendingArchive = null
    }

    private suspend fun setArchived(project: Project, archive: Boolean) {
        projectRepository.setArchived(project.id, archive)
        _messages.send(if (archive) ProjectsMessage.Archived(project.name) else ProjectsMessage.Unarchived(project.name))
    }

    private fun LoggedSession.toMessage() = ProjectsMessage.Logged(durationSeconds, projectName, day)

    private fun stopwatchAction(action: suspend () -> Unit) {
        viewModelScope.launch { stopwatchActions.withLock { action() } }
    }
}
