package xyz.tracel.imtrack.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ProjectDao {
    @Insert
    suspend fun insert(project: Project)

    /** Projects that are neither archived nor deleted. */
    @Query("SELECT * FROM project WHERE archived = 0 AND deletedAt IS NULL")
    fun observeActive(): Flow<List<Project>>

    /** Active and archived Projects; only deleted ones are left out. */
    @Query("SELECT * FROM project WHERE deletedAt IS NULL")
    fun observeAll(): Flow<List<Project>>

    @Query("UPDATE project SET name = :name, color = :color, updatedAt = :updatedAt WHERE id = :id")
    suspend fun update(id: String, name: String, color: Int, updatedAt: Long)

    @Query("UPDATE project SET archived = :archived, updatedAt = :updatedAt WHERE id = :id")
    suspend fun setArchived(id: String, archived: Boolean, updatedAt: Long)
}
