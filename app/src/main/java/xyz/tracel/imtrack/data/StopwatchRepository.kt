package xyz.tracel.imtrack.data

import androidx.room.withTransaction
import kotlinx.coroutines.flow.Flow
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject

/** A Stopwatch that has just stopped. [elapsedSeconds] is what was saved, if [saved]. */
data class StoppedStopwatch(
    val projectId: String,
    val startDate: LocalDate,
    val elapsedSeconds: Long,
    val saved: Boolean,
)

/** Starts and stops the Stopwatch, which is a stored start time (ADR-0005). */
class StopwatchRepository @Inject constructor(
    private val database: AppDatabase,
    private val dao: RunningStopwatchDao,
    private val sessionRepository: SessionRepository,
    private val clock: Clock,
) {
    fun observeRunning(): Flow<RunningStopwatch?> = dao.observe()

    suspend fun getRunning(): RunningStopwatch? = dao.get()

    /** Starts a Stopwatch for [projectId]; returns false, changing nothing, when one is already running. */
    suspend fun start(projectId: String): Boolean =
        dao.insert(RunningStopwatch(projectId, startedAt = clock.millis(), startDate = LocalDate.now(clock))) != -1L

    /** Whole seconds since [running] started, never below zero (the clock may be moved back). */
    fun elapsedSeconds(running: RunningStopwatch): Long = elapsedSeconds(running.startedAt, clock.millis())

    /**
     * Stops the Stopwatch as it was at [stoppedAt] (epoch millis), in one transaction: deletes the running
     * row and, if [keep], saves its Session dated the start day. Returns null when nothing was running,
     * so of two stops at once only one saves a Session. Whether to keep is the caller's decision.
     */
    suspend fun stop(keep: Boolean, stoppedAt: Long = clock.millis()): StoppedStopwatch? = database.withTransaction {
        val running = dao.get() ?: return@withTransaction null
        dao.delete()
        // A Session can't be zero seconds long, so a kept run that short counts as one second.
        val seconds = elapsedSeconds(running.startedAt, stoppedAt).let { if (keep) it.coerceAtLeast(1L) else it }
        if (keep) sessionRepository.log(running.projectId, running.startDate, seconds)
        StoppedStopwatch(running.projectId, running.startDate, seconds, saved = keep)
    }
}

/** Whole seconds from [startedAt] to [now] (epoch millis), never below zero. */
fun elapsedSeconds(startedAt: Long, now: Long): Long = ((now - startedAt) / 1_000L).coerceAtLeast(0L)
