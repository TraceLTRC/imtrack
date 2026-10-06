package xyz.tracel.imtrack.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query

@Dao
interface SessionDao {
    @Insert
    suspend fun insert(session: Session)

    @Query("SELECT * FROM session WHERE id = :id")
    suspend fun getById(id: String): Session?
}
