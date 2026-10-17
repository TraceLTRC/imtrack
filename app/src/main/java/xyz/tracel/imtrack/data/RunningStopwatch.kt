package xyz.tracel.imtrack.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import java.time.LocalDate

/**
 * The running Stopwatch: at most one row, kept on this device only. Unlike Projects and Sessions it
 * has no UUID, updatedAt or soft delete, is never exported and never syncs (ADR-0002).
 */
@Entity(tableName = "running_stopwatch")
data class RunningStopwatch(
    val projectId: String,
    /** Epoch millis, for the elapsed time. */
    val startedAt: Long,
    /** The local date at start; the Session is dated to it. */
    val startDate: LocalDate,
    /** Whether this run's Reminder has been shown. */
    @ColumnInfo(defaultValue = "0") val reminderShown: Boolean = false,
    /** Always [SingleRowId], so a second row can't exist. */
    @PrimaryKey val id: Int = SingleRowId,
) {
    companion object {
        const val SingleRowId = 0
    }
}
