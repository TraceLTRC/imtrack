package xyz.tracel.imtrack.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "project")
data class Project(
    @PrimaryKey val id: String,
    val name: String,
    /** ARGB. */
    val color: Int,
    val archived: Boolean,
    /** Epoch millis. */
    val createdAt: Long,
    /** Epoch millis, set on every write. */
    val updatedAt: Long,
    /** Epoch millis. Soft delete; v1 never sets it for a Project. */
    val deletedAt: Long?,
)
