package xyz.tracel.imtrack.data

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import java.time.Clock
import java.time.LocalDate
import java.util.UUID
import javax.inject.Inject

/**
 * Writes Sessions, giving each one its ID and timestamps so callers never pass them in.
 * Every Session it creates becomes the target of Undo last Session.
 */
class SessionRepository @Inject constructor(
    private val sessionDao: SessionDao,
    private val clock: Clock,
    private val lastCreated: LastCreatedSession,
) {
    /** Records a Session after the fact and returns its ID. */
    suspend fun log(projectId: String, date: LocalDate, durationSeconds: Long): String {
        require(durationSeconds > 0) { "A Session lasts more than zero seconds" }
        val now = clock.millis()
        val id = UUID.randomUUID().toString()
        sessionDao.insert(
            Session(
                id = id,
                projectId = projectId,
                date = date,
                durationSeconds = durationSeconds,
                createdAt = now,
                updatedAt = now,
                deletedAt = null,
            ),
        )
        lastCreated.set(id)
        return id
    }

    suspend fun update(id: String, projectId: String, date: LocalDate, durationSeconds: Long) {
        require(durationSeconds > 0) { "A Session lasts more than zero seconds" }
        sessionDao.update(id, projectId, date, durationSeconds, updatedAt = clock.millis())
    }

    /** Soft-deletes the Session: it vanishes from every screen and total, but its row stays (ADR-0002). */
    suspend fun delete(id: String) {
        sessionDao.softDelete(id, deletedAt = clock.millis())
    }

    /** The live Session that Undo last Session would remove, or null when there is none. */
    @OptIn(ExperimentalCoroutinesApi::class)
    fun observeUndoTarget(): Flow<Session?> = lastCreated.id.flatMapLatest { id ->
        if (id == null) flowOf(null) else sessionDao.observeLiveById(id)
    }

    /**
     * Undo last Session: soft-deletes the target and returns it as it was, or returns null when
     * there is no live target. Either way the target is cleared, so it only works once.
     */
    suspend fun undoLast(): Session? {
        val id = lastCreated.take() ?: return null
        val session = sessionDao.getLiveById(id) ?: return null
        delete(id)
        return session
    }

    /** Every live Session, in no particular order. */
    fun observeLive(): Flow<List<Session>> = sessionDao.observeLive()

    /** Every day with more than zero time across all Projects. */
    fun observeDaysWithTime(): Flow<Set<LocalDate>> = sessionDao.observeDaysWithTime().map { it.toSet() }

    /** Total seconds of live Sessions on [date], by Project ID. */
    fun observeTotalsOn(date: LocalDate): Flow<Map<String, Long>> =
        sessionDao.observeTotalsOn(date).map { totals -> totals.associate { it.projectId to it.totalSeconds } }

    /** Total seconds of live Sessions from [from] to [to] inclusive, by Project ID; archived Projects included. */
    fun observeTotalsBetween(from: LocalDate, to: LocalDate): Flow<Map<String, Long>> =
        sessionDao.observeTotalsBetween(from, to).map { totals -> totals.associate { it.projectId to it.totalSeconds } }

    /** Each Project's latest live Session, for Home's row order. */
    fun observeLatestPerProject(): Flow<List<ProjectActivity>> = sessionDao.observeLatestPerProject()
}
