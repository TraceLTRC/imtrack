package xyz.tracel.imtrack.home

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import xyz.tracel.imtrack.R
import xyz.tracel.imtrack.data.Project
import xyz.tracel.imtrack.data.ProjectRepository
import xyz.tracel.imtrack.data.SessionRepository
import xyz.tracel.imtrack.projects.pickDefaultColor
import xyz.tracel.imtrack.sessions.DayLabel
import xyz.tracel.imtrack.sessions.dayLabel
import xyz.tracel.imtrack.sessions.isValidMinutes
import xyz.tracel.imtrack.sessions.todayFlow
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject
import kotlin.random.Random

enum class EmptyKind(@param:StringRes val heading: Int, @param:StringRes val body: Int) {
    FirstLaunch(R.string.home_first_launch_heading, R.string.home_first_launch_body),
    AllArchived(R.string.home_all_archived_heading, R.string.home_all_archived_body),
}

/** An active Project on Home and the seconds logged to it today. */
data class HomeRow(val project: Project, val todaySeconds: Long)

sealed interface HomeUiState {
    data object Loading : HomeUiState

    /** No active Projects: Home offers to add one, starting with [defaultColor]. */
    data class Empty(val kind: EmptyKind, val defaultColor: Int) : HomeUiState

    /**
     * The normal Home: today's total across all Projects and one row per active Project, in Home's order.
     * [logSheet] is the Project the Log sheet is open for, if any.
     */
    data class Projects(
        val today: LocalDate,
        val todaySeconds: Long,
        val rows: List<HomeRow>,
        val logSheet: Project? = null,
    ) : HomeUiState
}

/** Snackbar confirmations; the screen turns them into copy. */
sealed interface HomeMessage {
    data class Logged(val durationSeconds: Long, val projectName: String, val day: DayLabel) : HomeMessage
}

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class HomeViewModel @Inject constructor(
    private val projectRepository: ProjectRepository,
    private val sessionRepository: SessionRepository,
    private val clock: Clock,
    private val random: Random,
) : ViewModel() {
    private val logSheet = MutableStateFlow<Project?>(null)
    private val _messages = Channel<HomeMessage>(Channel.BUFFERED)
    val messages: Flow<HomeMessage> = _messages.receiveAsFlow()

    private val todayTotals = todayFlow(clock).distinctUntilChanged().flatMapLatest { today ->
        sessionRepository.observeTotalsOn(today).map { today to it }
    }

    val uiState: StateFlow<HomeUiState> = combine(
        projectRepository.observeAll(),
        todayTotals,
        sessionRepository.observeLatestPerProject(),
        logSheet,
    ) { all, (today, totals), latest, logSheet ->
        val active = sortForHome(all, latest)
        when {
            active.isNotEmpty() -> HomeUiState.Projects(
                today = today,
                // Archived Projects' Sessions still count towards today.
                todaySeconds = totals.values.sum(),
                rows = active.map { HomeRow(it, todaySeconds = totals[it.id] ?: 0L) },
                logSheet = logSheet,
            )
            else -> HomeUiState.Empty(
                kind = if (all.isEmpty()) EmptyKind.FirstLaunch else EmptyKind.AllArchived,
                defaultColor = pickDefaultColor(activeColors = emptyList(), random = random),
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState.Loading)

    fun addProject(name: String, color: Int) {
        if (name.isBlank()) return
        viewModelScope.launch { projectRepository.create(name, color) }
    }

    fun openLog(project: Project) {
        logSheet.value = project
    }

    fun dismissLog() {
        logSheet.value = null
    }

    /** Logs [minutes] on [date] to the Project the Log sheet is open for, then closes it. */
    fun log(minutes: Int, date: LocalDate) {
        val project = logSheet.value ?: return
        val today = LocalDate.now(clock)
        if (!isValidMinutes(minutes) || date > today) return
        logSheet.value = null
        viewModelScope.launch {
            val seconds = minutes * 60L
            sessionRepository.log(project.id, date, seconds)
            _messages.send(HomeMessage.Logged(seconds, project.name, dayLabel(date, today)))
        }
    }
}
