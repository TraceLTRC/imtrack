package xyz.tracel.imtrack.placeholder

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import xyz.tracel.imtrack.data.ProjectRepository
import javax.inject.Inject

@HiltViewModel
class PlaceholderViewModel @Inject constructor(
    private val projectRepository: ProjectRepository,
) : ViewModel() {
    /** Number of active Projects, live from the database. */
    val projectCount: StateFlow<Int> = projectRepository.observeActive()
        .map { it.size }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    fun insertSampleProject() {
        viewModelScope.launch {
            projectRepository.create(name = "Sample Project", color = SAMPLE_COLOR)
        }
    }

    private companion object {
        const val SAMPLE_COLOR = 0xFF3366CC.toInt()
    }
}
