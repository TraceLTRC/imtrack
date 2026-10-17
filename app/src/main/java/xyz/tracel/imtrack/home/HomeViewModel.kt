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
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import xyz.tracel.imtrack.R
import xyz.tracel.imtrack.data.Project
import xyz.tracel.imtrack.data.ProjectRepository
import xyz.tracel.imtrack.data.SessionRepository
import xyz.tracel.imtrack.data.StopwatchRepository
import xyz.tracel.imtrack.projects.pickDefaultColor
import xyz.tracel.imtrack.sessions.DayLabel
import xyz.tracel.imtrack.sessions.dayLabel
import xyz.tracel.imtrack.sessions.isValidMinutes
import xyz.tracel.imtrack.sessions.todayFlow
import xyz.tracel.imtrack.stopwatch.InAppStop
import xyz.tracel.imtrack.stopwatch.InAppStopwatch
import xyz.tracel.imtrack.stopwatch.LoggedSession
import xyz.tracel.imtrack.stopwatch.UnderAMinute
import xyz.tracel.imtrack.stopwatch.UnderAMinuteChoice
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject
import kotlin.random.Random

enum class EmptyKind(@param:StringRes val heading: Int, @param:StringRes val body: Int) {
    FirstLaunch(R.string.home_first_launch_heading, R.string.home_first_launch_body),
    AllArchived(R.string.home_all_archived_heading, R.string.home_all_archived_body),
}

/** An active Project on Home, the seconds logged to it today, and whether its Stopwatch is running. */
data class HomeRow(val project: Project, val todaySeconds: Long, val running: Boolean = false)

/** The running card: the Stopwatch's Project and when it started (epoch millis). */
data class RunningCard(val project: Project, val startedAt: Long)

sealed interface HomeUiState {
    data object Loading : HomeUiState

    /** No active Projects: Home offers to add one, starting with [defaultColor]. */
    data class Empty(val kind: EmptyKind, val defaultColor: Int) : HomeUiState

    /**
     * The normal Home: today's total across all Projects and one row per active Project, in Home's order.
     * [running] is the running Stopwatch, if any; [logSheet] is the Project the Log sheet is open for, if any.
     */
    data class Projects(
        val today: LocalDate,
        val todaySeconds: Long,
        val rows: List<HomeRow>,
        val running: RunningCard? = null,
        val logSheet: Project? = null,
    ) : HomeUiState
}

/** Snackbar confirmations; the screen turns them into copy. */
sealed interface HomeMessage {
    data class Logged(val durationSeconds: Long, val projectName: String, val day: DayLabel) : HomeMessage
    data class Switched(val stoppedName: String, val startedName: String) : HomeMessage
}

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class HomeViewModel @Inject constructor(
    private val projectRepository: ProjectRepository,
    private val sessionRepository: SessionRepository,
    private val stopwatchRepository: StopwatchRepository,
    private val inAppStopwatch: InAppStopwatch,
    private val clock: Clock,
    private val random: Random,
) : ViewModel() {
    private val logSheet = MutableStateFlow<Project?>(null)
    private val _messages = Channel<HomeMessage>(Channel.BUFFERED)
    val messages: Flow<HomeMessage> = _messages.receiveAsFlow()

    /** The under-a-minute dialog, while it's open. */
    val underAMinute: StateFlow<UnderAMinute?> = inAppStopwatch.underAMinute

    /** Start, stop and the dialog's answers run one at a time, so a quick double tap can't interleave them. */
    private val stopwatchActions = Mutex()

    private val todayTotals = todayFlow(clock).distinctUntilChanged().flatMapLatest { today ->
        sessionRepository.observeTotalsOn(today).map { today to it }
    }

    val uiState: StateFlow<HomeUiState> = combine(
        projectRepository.observeAll(),
        todayTotals,
        sessionRepository.observeLatestPerProject(),
        stopwatchRepository.observeRunning(),
        logSheet,
    ) { all, (today, totals), latest, running, logSheet ->
        val active = sortForHome(all, latest)
        when {
            active.isNotEmpty() -> HomeUiState.Projects(
                today = today,
                // Archived Projects' Sessions still count towards today.
                todaySeconds = totals.values.sum(),
                rows = active.map {
                    HomeRow(it, todaySeconds = totals[it.id] ?: 0L, running = it.id == running?.projectId)
                },
                running = running?.let { stopwatch ->
                    all.find { it.id == stopwatch.projectId }?.let { RunningCard(it, stopwatch.startedAt) }
                },
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

    /** The clock the running card ticks by, in epoch millis. */
    fun nowMillis(): Long = clock.millis()

    /**
     * ▶ on a row: starts [project]'s Stopwatch, first stopping a running one by the in-app rules.
     * If that one ran under a minute, [project] starts once the dialog is answered.
     */
    fun start(project: Project) = stopwatchAction {
        if (inAppStopwatch.isRunning(project)) return@stopwatchAction
        when (val stop = inAppStopwatch.stop(startNext = project)) {
            InAppStop.NothingRunning -> stopwatchRepository.start(project.id)
            is InAppStop.Logged -> {
                stopwatchRepository.start(project.id)
                _messages.send(HomeMessage.Switched(stop.session.projectName, project.name))
            }
            InAppStop.Asking -> Unit
        }
    }

    /** Stop on the running card, or ■ on its row. */
    fun stop() = stopwatchAction {
        (inAppStopwatch.stop() as? InAppStop.Logged)?.let { _messages.send(it.session.toMessage()) }
    }

    fun keepUnderAMinute() = stopwatchAction {
        inAppStopwatch.keep()?.let { answered(it) }
    }

    fun discardUnderAMinute() = stopwatchAction {
        inAppStopwatch.discard()?.let { answered(it) }
    }

    /** A switch always ends with its own snackbar; a plain stop confirms only a kept Session. */
    private suspend fun answered(choice: UnderAMinuteChoice) {
        val next = choice.startNext
        if (next == null) {
            choice.logged?.let { _messages.send(it.toMessage()) }
        } else {
            stopwatchRepository.start(next.id)
            _messages.send(HomeMessage.Switched(choice.stoppedName, next.name))
        }
    }

    private fun LoggedSession.toMessage() = HomeMessage.Logged(durationSeconds, projectName, day)

    private fun stopwatchAction(action: suspend () -> Unit) {
        viewModelScope.launch { stopwatchActions.withLock { action() } }
    }
}
