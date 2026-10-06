package xyz.tracel.imtrack.data

import kotlinx.coroutines.flow.Flow
import java.time.Clock
import java.util.UUID
import javax.inject.Inject

/**
 * Writes Projects, giving each one its ID and timestamps so callers never pass them in.
 * Names are stored trimmed.
 */
class ProjectRepository @Inject constructor(
    private val projectDao: ProjectDao,
    private val clock: Clock,
) {
    fun observeActive(): Flow<List<Project>> = projectDao.observeActive()

    /** Active and archived Projects. */
    fun observeAll(): Flow<List<Project>> = projectDao.observeAll()

    suspend fun create(name: String, color: Int) {
        val now = clock.millis()
        projectDao.insert(
            Project(
                id = UUID.randomUUID().toString(),
                name = name.trim(),
                color = color,
                archived = false,
                createdAt = now,
                updatedAt = now,
                deletedAt = null,
            ),
        )
    }

    suspend fun update(id: String, name: String, color: Int) {
        projectDao.update(id, name.trim(), color, updatedAt = clock.millis())
    }

    suspend fun setArchived(id: String, archived: Boolean) {
        projectDao.setArchived(id, archived, updatedAt = clock.millis())
    }
}
