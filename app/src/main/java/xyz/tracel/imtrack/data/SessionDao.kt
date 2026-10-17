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

    @Insert
    suspend fun insertAll(sessions: List<Session>)

    /** Every row, deleted ones too: for Export. */
    @Query("SELECT * FROM session")
    suspend fun getAll(): List<Session>

    /** Hard delete; only Import uses it (ADR-0002). */
    @Query("DELETE FROM session")
    suspend fun deleteAll()

    @Query("SELECT * FROM session WHERE id = :id")
    suspend fun getById(id: String): Session?

    @Query("SELECT * FROM session WHERE id = :id AND deletedAt IS NULL")
    suspend fun getLiveById(id: String): Session?

    /** The Session with [id] while it's live; null once it's deleted. */
    @Query("SELECT * FROM session WHERE id = :id AND deletedAt IS NULL")
    fun observeLiveById(id: String): Flow<Session?>

    /** Each Project's total seconds of live Sessions on [date]; Projects with none are left out. */
    @Query(
        """
        SELECT projectId, SUM(durationSeconds) AS totalSeconds FROM session
        WHERE date = :date AND deletedAt IS NULL
        GROUP BY projectId
        """,
    )
    fun observeTotalsOn(date: LocalDate): Flow<List<ProjectTotal>>

    /** Each Project's total seconds of live Sessions from [from] to [to], inclusive; Projects with none are left out. */
    @Query(
        """
        SELECT projectId, SUM(durationSeconds) AS totalSeconds FROM session
        WHERE date BETWEEN :from AND :to AND deletedAt IS NULL
        GROUP BY projectId
        """,
    )
    fun observeTotalsBetween(from: LocalDate, to: LocalDate): Flow<List<ProjectTotal>>

    /**
     * Each day's total seconds of live Sessions from [from] to [to], inclusive, for [projectId] or all
     * Projects when it's null; days with none are left out.
     */
    @Query(
        """
        SELECT date, SUM(durationSeconds) AS totalSeconds FROM session
        WHERE date BETWEEN :from AND :to AND deletedAt IS NULL AND (:projectId IS NULL OR projectId = :projectId)
        GROUP BY date
        """,
    )
    fun observeDayTotalsBetween(from: LocalDate, to: LocalDate, projectId: String?): Flow<List<DayTotal>>

    /** Every day's live seconds per Project, for Past Periods. */
    @Query(
        """
        SELECT date, projectId, SUM(durationSeconds) AS seconds FROM session
        WHERE deletedAt IS NULL
        GROUP BY date, projectId
        """,
    )
    fun observeDayProjectTotals(): Flow<List<DayProjectTotal>>

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

    /** Every day whose live Sessions add up to more than zero, archived Projects included. */
    @Query("SELECT date FROM session WHERE deletedAt IS NULL GROUP BY date HAVING SUM(durationSeconds) > 0")
    fun observeDaysWithTime(): Flow<List<LocalDate>>

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

data class DayTotal(val date: LocalDate, val totalSeconds: Long)

/** A day's live seconds for one Project. */
data class DayProjectTotal(val date: LocalDate, val projectId: String, val seconds: Long)
