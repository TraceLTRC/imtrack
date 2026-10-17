package xyz.tracel.imtrack.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Clock
import java.time.LocalDate
import java.util.UUID
import javax.inject.Inject

/** Writes Sessions, giving each one its ID and timestamps so callers never pass them in. */
class SessionRepository @Inject constructor(
    private val sessionDao: SessionDao,
    private val clock: Clock,
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
        return id
    }

    /** Total seconds of live Sessions on [date], by Project ID. */
    fun observeTotalsOn(date: LocalDate): Flow<Map<String, Long>> =
        sessionDao.observeTotalsOn(date).map { totals -> totals.associate { it.projectId to it.totalSeconds } }

    /** Each Project's latest live Session, for Home's row order. */
    fun observeLatestPerProject(): Flow<List<ProjectActivity>> = sessionDao.observeLatestPerProject()
}
