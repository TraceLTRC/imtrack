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
}
