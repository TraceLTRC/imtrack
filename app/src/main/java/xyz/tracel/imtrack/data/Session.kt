package xyz.tracel.imtrack.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.LocalDate

@Entity(
    tableName = "session",
    foreignKeys = [
        ForeignKey(
            entity = Project::class,
            parentColumns = ["id"],
            childColumns = ["projectId"],
            onDelete = ForeignKey.RESTRICT,
        ),
    ],
    indices = [
        Index("projectId", "date"),
        Index("date"),
    ],
)
data class Session(
    @PrimaryKey val id: String,
    val projectId: String,
    /** Stored as epoch-day: no time, no timezone (ADR-0003). */
    val date: LocalDate,
    /** Must be > 0, enforced in app code rather than SQL. */
    val durationSeconds: Long,
    /** Epoch millis. */
    val createdAt: Long,
    /** Epoch millis, set on every write. */
    val updatedAt: Long,
    /** Epoch millis. Soft delete; deleted Sessions vanish from every screen and total. */
    val deletedAt: Long?,
)
