package xyz.tracel.imtrack.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface RunningStopwatchDao {
    @Query("SELECT * FROM running_stopwatch LIMIT 1")
    fun observe(): Flow<RunningStopwatch?>

    @Query("SELECT * FROM running_stopwatch LIMIT 1")
    suspend fun get(): RunningStopwatch?

    /** Returns -1 when a Stopwatch is already running. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(running: RunningStopwatch): Long

    /** Returns how many rows went: 0 when nothing was running. */
    @Query("DELETE FROM running_stopwatch")
    suspend fun delete(): Int
}
