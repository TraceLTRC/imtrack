package xyz.tracel.imtrack.data

import kotlinx.coroutines.flow.Flow
import java.time.Clock
import java.util.UUID
import javax.inject.Inject

/** Writes Projects, giving each one its ID and timestamps so callers never pass them in. */
class ProjectRepository @Inject constructor(
    private val projectDao: ProjectDao,
    private val clock: Clock,
) {
    fun observeActive(): Flow<List<Project>> = projectDao.observeActive()

    suspend fun create(name: String, color: Int) {
        val now = clock.millis()
        projectDao.insert(
            Project(
                id = UUID.randomUUID().toString(),
                name = name,
                color = color,
                archived = false,
                createdAt = now,
                updatedAt = now,
                deletedAt = null,
            ),
        )
    }
}
