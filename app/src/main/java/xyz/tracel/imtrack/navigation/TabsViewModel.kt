package xyz.tracel.imtrack.navigation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import xyz.tracel.imtrack.data.ProjectRepository
import xyz.tracel.imtrack.data.SessionRepository
import javax.inject.Inject

/** Snackbar confirmations from the ⋮ menu; the screen turns them into copy. */
sealed interface TabsMessage {
    data class Removed(val durationSeconds: Long, val projectName: String) : TabsMessage
}

/** The ⋮ menu's Undo last Session. */
@HiltViewModel
class TabsViewModel @Inject constructor(
    private val projectRepository: ProjectRepository,
    private val sessionRepository: SessionRepository,
) : ViewModel() {
    private val _messages = Channel<TabsMessage>(Channel.BUFFERED)
    val messages: Flow<TabsMessage> = _messages.receiveAsFlow()

    val canUndo: StateFlow<Boolean> = sessionRepository.observeUndoTarget()
        .map { it != null }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    fun undoLastSession() {
        viewModelScope.launch {
            val session = sessionRepository.undoLast() ?: return@launch
            val projectName = projectRepository.get(session.projectId)?.name.orEmpty()
            _messages.send(TabsMessage.Removed(session.durationSeconds, projectName))
        }
    }
}
