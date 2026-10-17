package xyz.tracel.imtrack.stopwatch

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import xyz.tracel.imtrack.data.Project
import xyz.tracel.imtrack.data.ProjectRepository
import xyz.tracel.imtrack.data.StopwatchRepository
import xyz.tracel.imtrack.data.StoppedStopwatch
import xyz.tracel.imtrack.sessions.DayLabel
import xyz.tracel.imtrack.sessions.dayLabel
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject

/** Stopwatches stopped in the app before this ask whether to keep the Session. */
const val UnderAMinuteSeconds = 60L

/**
 * The under-a-minute dialog: Stop was tapped at [stoppedAt] (epoch millis), [elapsedSeconds] in.
 * The Stopwatch keeps running until the answer. [startNext] starts afterwards, when this came from a switch.
 */
data class UnderAMinute(
    val projectName: String,
    val elapsedSeconds: Long,
    val stoppedAt: Long,
    val startNext: Project? = null,
)

/** A Session a stop saved, for the `Logged 32 min · Guitar` snackbar. */
data class LoggedSession(val durationSeconds: Long, val projectName: String, val day: DayLabel)

/** What stopping in the app did. */
sealed interface InAppStop {
    data object NothingRunning : InAppStop

    data class Logged(val session: LoggedSession) : InAppStop

    /** It ran under a minute, so [InAppStopwatch.underAMinute] now asks. */
    data object Asking : InAppStop
}

/** The dialog's answer: the Session it saved, if any, the stopped Project's name, and the Project to start next. */
data class UnderAMinuteChoice(val logged: LoggedSession?, val stoppedName: String, val startNext: Project?)

/**
 * Stopping the Stopwatch in the app (CONTEXT.md, Stopwatch): a run of a minute or more is saved,
 * a shorter one asks whether to keep it. Each ViewModel gets its own instance, holding its dialog.
 */
class InAppStopwatch @Inject constructor(
    private val stopwatchRepository: StopwatchRepository,
    private val projectRepository: ProjectRepository,
    private val clock: Clock,
) {
    private val _underAMinute = MutableStateFlow<UnderAMinute?>(null)
    val underAMinute: StateFlow<UnderAMinute?> = _underAMinute.asStateFlow()

    /** Whether [project]'s Stopwatch is the one running. */
    suspend fun isRunning(project: Project): Boolean = stopwatchRepository.getRunning()?.projectId == project.id

    /** Stops the running Stopwatch. If the dialog appears, [startNext] is handed back once it's answered. */
    suspend fun stop(startNext: Project? = null): InAppStop {
        val running = stopwatchRepository.getRunning() ?: return InAppStop.NothingRunning
        val stoppedAt = clock.millis()
        val seconds = stopwatchRepository.elapsedSeconds(running)
        if (seconds < UnderAMinuteSeconds) {
            _underAMinute.value = UnderAMinute(projectName(running.projectId), seconds, stoppedAt, startNext)
            return InAppStop.Asking
        }
        // Null when it was stopped elsewhere (the notification) in the meantime.
        val stopped = stopwatchRepository.stop(keep = true, stoppedAt) ?: return InAppStop.NothingRunning
        return InAppStop.Logged(stopped.toLogged())
    }

    /** Keep: saves the run up to when Stop was tapped. Null when the dialog isn't open. */
    suspend fun keep(): UnderAMinuteChoice? = answer(keep = true)

    /** Discard: stops the run without saving. Null when the dialog isn't open. */
    suspend fun discard(): UnderAMinuteChoice? = answer(keep = false)

    private suspend fun answer(keep: Boolean): UnderAMinuteChoice? {
        val pending = _underAMinute.value ?: return null
        val stopped = stopwatchRepository.stop(keep, pending.stoppedAt)
        _underAMinute.value = null
        return UnderAMinuteChoice(stopped?.takeIf { it.saved }?.toLogged(), pending.projectName, pending.startNext)
    }

    private suspend fun projectName(id: String) = projectRepository.get(id)?.name.orEmpty()

    private suspend fun StoppedStopwatch.toLogged() =
        LoggedSession(elapsedSeconds, projectName(projectId), dayLabel(startDate, LocalDate.now(clock)))
}
