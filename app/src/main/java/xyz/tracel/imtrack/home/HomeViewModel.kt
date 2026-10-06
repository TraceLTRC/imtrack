package xyz.tracel.imtrack.home

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import xyz.tracel.imtrack.R
import xyz.tracel.imtrack.data.Project
import xyz.tracel.imtrack.data.ProjectRepository
import xyz.tracel.imtrack.projects.pickDefaultColor
import javax.inject.Inject
import kotlin.random.Random

enum class EmptyKind(@param:StringRes val heading: Int, @param:StringRes val body: Int) {
    FirstLaunch(R.string.home_first_launch_heading, R.string.home_first_launch_body),
    AllArchived(R.string.home_all_archived_heading, R.string.home_all_archived_body),
}

sealed interface HomeUiState {
    data object Loading : HomeUiState

    /** No active Projects: Home offers to add one, starting with [defaultColor]. */
    data class Empty(val kind: EmptyKind, val defaultColor: Int) : HomeUiState

    data class Projects(val projects: List<Project>) : HomeUiState
}

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val projectRepository: ProjectRepository,
    private val random: Random,
) : ViewModel() {
    val uiState: StateFlow<HomeUiState> = projectRepository.observeAll()
        .map { all ->
            val active = all.filterNot { it.archived }
            when {
                active.isNotEmpty() -> HomeUiState.Projects(active)
                else -> HomeUiState.Empty(
                    kind = if (all.isEmpty()) EmptyKind.FirstLaunch else EmptyKind.AllArchived,
                    defaultColor = pickDefaultColor(activeColors = emptyList(), random = random),
                )
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState.Loading)

    fun addProject(name: String, color: Int) {
        if (name.isBlank()) return
        viewModelScope.launch { projectRepository.create(name, color) }
    }
}
