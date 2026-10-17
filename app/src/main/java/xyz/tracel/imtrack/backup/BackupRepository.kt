package xyz.tracel.imtrack.backup

import androidx.room.withTransaction
import xyz.tracel.imtrack.data.AppDatabase
import xyz.tracel.imtrack.data.LastCreatedSession
import java.time.Clock
import javax.inject.Inject

/** Reads every row for Export and replaces every row for Import (ADR-0002). */
class BackupRepository @Inject constructor(
    private val database: AppDatabase,
    private val clock: Clock,
    private val lastCreated: LastCreatedSession,
) {
    /** Every Project and Session row, archived and soft-deleted ones included. */
    suspend fun snapshot(): Backup = database.withTransaction {
        Backup(
            exportedAt = clock.millis(),
            projects = database.projectDao().getAll(),
            sessions = database.sessionDao().getAll(),
        )
    }

    /**
     * Import: in one transaction, hard-deletes every row and inserts the backup's rows unchanged,
     * keeping their IDs and timestamps. Also stops a running Stopwatch without saving and clears the
     * Undo last Session target.
     */
    suspend fun replaceAll(backup: Backup) {
        database.withTransaction {
            database.runningStopwatchDao().delete()
            database.sessionDao().deleteAll()
            database.projectDao().deleteAll()
            database.projectDao().insertAll(backup.projects)
            database.sessionDao().insertAll(backup.sessions)
        }
        lastCreated.clear()
    }
}
