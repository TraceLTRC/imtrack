package xyz.tracel.imtrack.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import xyz.tracel.imtrack.data.Project
import xyz.tracel.imtrack.data.ProjectRepository
import xyz.tracel.imtrack.data.Session
import xyz.tracel.imtrack.data.SessionRepository
import xyz.tracel.imtrack.sessions.displayMinutes
import xyz.tracel.imtrack.sessions.isValidMinutes
import xyz.tracel.imtrack.sessions.todayFlow
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject

/** The Edit Session sheet: the [session] being edited and the [projects] offered as chips. */
data class EditSessionSheet(val session: Session, val projects: List<Project>)

sealed interface HistoryUiState {
    data object Loading : HistoryUiState

    /** Every live Session in day cards; no [days] is the empty state. */
    data class Loaded(
        val today: LocalDate,
        val days: List<HistoryDay>,
        val editSheet: EditSessionSheet? = null,
    ) : HistoryUiState
}

/** Snackbar confirmations; the screen turns them into copy. */
sealed interface HistoryMessage {
    data object Updated : HistoryMessage
    data object Deleted : HistoryMessage
}

@HiltViewModel
class HistoryViewModel @Inject constructor(
    projectRepository: ProjectRepository,
    private val sessionRepository: SessionRepository,
    private val clock: Clock,
) : ViewModel() {
    private val editingSessionId = MutableStateFlow<String?>(null)
    private val _messages = Channel<HistoryMessage>(Channel.BUFFERED)
    val messages: Flow<HistoryMessage> = _messages.receiveAsFlow()

    val uiState: StateFlow<HistoryUiState> = combine(
        todayFlow(clock).distinctUntilChanged(),
        projectRepository.observeAll(),
        sessionRepository.observeLive(),
        editingSessionId,
    ) { today, projects, live, editingId ->
        // A Session deleted elsewhere (e.g. Undo last Session) closes its sheet.
        val session = live.firstOrNull { it.id == editingId }
        HistoryUiState.Loaded(
            today = today,
            days = historyDays(live, projects),
            editSheet = session?.let { EditSessionSheet(it, projectChoices(projects, it)) },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HistoryUiState.Loading)

    private val editSheet get() = (uiState.value as? HistoryUiState.Loaded)?.editSheet

    fun openEdit(session: Session) {
        editingSessionId.value = session.id
    }

    fun dismissEdit() {
        editingSessionId.value = null
    }

    /**
     * Saves the Session being edited, then closes the sheet. Minutes left as shown keep the original
     * seconds, so a Stopwatch Session isn't rounded; changed minutes store `minutes × 60`.
     */
    fun save(projectId: String, minutes: Int, date: LocalDate) {
        val sheet = editSheet ?: return
        val session = sheet.session
        if (!isValidMinutes(minutes) || date > LocalDate.now(clock)) return
        if (sheet.projects.none { it.id == projectId }) return
        editingSessionId.value = null
        val seconds = if (minutes.toLong() == displayMinutes(session.durationSeconds)) {
            session.durationSeconds
        } else {
            minutes * 60L
        }
        viewModelScope.launch {
            sessionRepository.update(session.id, projectId, date, seconds)
            _messages.send(HistoryMessage.Updated)
        }
    }

    /** Soft-deletes the Session being edited, then closes the sheet. */
    fun delete() {
        val session = editSheet?.session ?: return
        editingSessionId.value = null
        viewModelScope.launch {
            sessionRepository.delete(session.id)
            _messages.send(HistoryMessage.Deleted)
        }
    }
}

private val byName = compareBy<Project, String>(String.CASE_INSENSITIVE_ORDER) { it.name }

/** Every active Project, plus the Session's own Project if it's archived; A–Z. */
private fun projectChoices(projects: List<Project>, session: Session): List<Project> =
    projects.filter { !it.archived || it.id == session.projectId }.sortedWith(byName)
