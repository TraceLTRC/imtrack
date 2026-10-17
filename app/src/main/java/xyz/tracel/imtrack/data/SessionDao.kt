package xyz.tracel.imtrack.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

@Dao
interface SessionDao {
    @Insert
    suspend fun insert(session: Session)

    @Query("SELECT * FROM session WHERE id = :id")
    suspend fun getById(id: String): Session?

    /** Each Project's total seconds of live Sessions on [date]; Projects with none are left out. */
    @Query(
        """
        SELECT projectId, SUM(durationSeconds) AS totalSeconds FROM session
        WHERE date = :date AND deletedAt IS NULL
        GROUP BY projectId
        """,
    )
    fun observeTotalsOn(date: LocalDate): Flow<List<ProjectTotal>>

    /** Each Project's latest live Session; Projects with none are left out. */
    @Query(
        """
        SELECT s.projectId, s.date, s.createdAt FROM session s
        WHERE s.id = (
            SELECT t.id FROM session t
            WHERE t.projectId = s.projectId AND t.deletedAt IS NULL
            ORDER BY t.date DESC, t.createdAt DESC
            LIMIT 1
        )
        """,
    )
    fun observeLatestPerProject(): Flow<List<ProjectActivity>>

    @Query("SELECT * FROM session WHERE deletedAt IS NULL")
    fun observeLive(): Flow<List<Session>>

    /** Edits a live Session; a deleted one stays as it was. */
    @Query(
        """
        UPDATE session SET projectId = :projectId, date = :date, durationSeconds = :durationSeconds,
            updatedAt = :updatedAt
        WHERE id = :id AND deletedAt IS NULL
        """,
    )
    suspend fun update(id: String, projectId: String, date: LocalDate, durationSeconds: Long, updatedAt: Long)

    /** Soft delete (ADR-0002): the row stays, marked with [deletedAt]. */
    @Query("UPDATE session SET deletedAt = :deletedAt, updatedAt = :deletedAt WHERE id = :id")
    suspend fun softDelete(id: String, deletedAt: Long)
}

data class ProjectTotal(val projectId: String, val totalSeconds: Long)
