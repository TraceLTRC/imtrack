package xyz.tracel.imtrack.backup

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.MissingFieldException
import kotlinx.serialization.SerializationException
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull
import xyz.tracel.imtrack.data.Project
import xyz.tracel.imtrack.data.Session
import java.time.LocalDate
import java.time.format.DateTimeParseException

/** Every Project and Session row, soft-deleted ones included, as written to or read from a backup file. */
data class Backup(val exportedAt: Long, val projects: List<Project>, val sessions: List<Session>) {
    val liveProjectCount get() = projects.count { it.deletedAt == null }
    val liveSessionCount get() = sessions.count { it.deletedAt == null }
}

/** Why a file can't be imported; the screen turns it into the snackbar's reason. */
sealed interface BackupError {
    data object NotJson : BackupError
    data object NotABackup : BackupError
    data class UnknownVersion(val version: String) : BackupError
    data class MissingField(val name: String) : BackupError
    /** A field has the wrong type or format, or there's a field imtrack doesn't know. */
    data object Malformed : BackupError
    data object MissingProject : BackupError
    data object DuplicateId : BackupError
    data object BadDuration : BackupError
    /** Reading the file or writing the database failed; [detail] is the system's reason, if it gave one. */
    data class Failed(val detail: String?) : BackupError
}

sealed interface BackupDecoding {
    data class Valid(val backup: Backup) : BackupDecoding
    data class Invalid(val error: BackupError) : BackupDecoding
}

/**
 * The JSON backup format (ADR-0002): `{ "formatVersion": 1, "exportedAt": <epoch millis>,
 * "projects": [...], "sessions": [...] }`, with fields named after the entity properties.
 * A Session's `date` is an ISO date such as `2026-10-04`.
 */
object BackupFormat {
    const val VERSION = 1

    private val json = Json { prettyPrint = true }

    fun encode(backup: Backup): String = json.encodeToString(
        BackupJson(
            formatVersion = VERSION,
            exportedAt = backup.exportedAt,
            projects = backup.projects.map { it.toJson() },
            sessions = backup.sessions.map { it.toJson() },
        ),
    )

    /** Parses and validates a whole file, so nothing is written unless all of it is good. */
    @OptIn(ExperimentalSerializationApi::class)
    fun decode(text: String): BackupDecoding {
        val element = try {
            json.parseToJsonElement(text)
        } catch (_: SerializationException) {
            return BackupDecoding.Invalid(BackupError.NotJson)
        }
        if (element.isUnquotedWord()) return BackupDecoding.Invalid(BackupError.NotJson)
        val version = ((element as? JsonObject)?.get("formatVersion") as? JsonPrimitive)
            ?.takeUnless { it is JsonNull }
            ?: return BackupDecoding.Invalid(BackupError.NotABackup)
        if (version.isString || version.content != VERSION.toString()) {
            return BackupDecoding.Invalid(BackupError.UnknownVersion(version.content))
        }
        val file = try {
            json.decodeFromJsonElement(BackupJson.serializer(), element)
        } catch (e: MissingFieldException) {
            return BackupDecoding.Invalid(BackupError.MissingField(e.missingFields.first()))
        } catch (_: IllegalArgumentException) {
            // SerializationException is an IllegalArgumentException.
            return BackupDecoding.Invalid(BackupError.Malformed)
        }
        val backup = try {
            Backup(file.exportedAt, file.projects.map { it.toProject() }, file.sessions.map { it.toSession() })
        } catch (_: DateTimeParseException) {
            return BackupDecoding.Invalid(BackupError.Malformed)
        }
        return validate(backup)?.let { BackupDecoding.Invalid(it) } ?: BackupDecoding.Valid(backup)
    }

    private fun validate(backup: Backup): BackupError? {
        val projectIds = backup.projects.map { it.id }.toSet()
        return when {
            projectIds.size != backup.projects.size -> BackupError.DuplicateId
            backup.sessions.distinctBy { it.id }.size != backup.sessions.size -> BackupError.DuplicateId
            backup.sessions.any { it.projectId !in projectIds } -> BackupError.MissingProject
            backup.sessions.any { it.durationSeconds <= 0 } -> BackupError.BadDuration
            else -> null
        }
    }
}

/** The parser accepts a bare word such as `hello` as a literal, but it isn't JSON. */
private fun JsonElement.isUnquotedWord() =
    this is JsonPrimitive && !isString && this != JsonNull && booleanOrNull == null && doubleOrNull == null

@Serializable
private class BackupJson(
    val formatVersion: Int,
    val exportedAt: Long,
    val projects: List<ProjectJson>,
    val sessions: List<SessionJson>,
)

@Serializable
private class ProjectJson(
    val id: String,
    val name: String,
    val color: Int,
    val archived: Boolean,
    val createdAt: Long,
    val updatedAt: Long,
    val deletedAt: Long?,
)

@Serializable
private class SessionJson(
    val id: String,
    val projectId: String,
    val date: String,
    val durationSeconds: Long,
    val createdAt: Long,
    val updatedAt: Long,
    val deletedAt: Long?,
)

private fun Project.toJson() = ProjectJson(id, name, color, archived, createdAt, updatedAt, deletedAt)

private fun ProjectJson.toProject() = Project(id, name, color, archived, createdAt, updatedAt, deletedAt)

private fun Session.toJson() =
    SessionJson(id, projectId, date.toString(), durationSeconds, createdAt, updatedAt, deletedAt)

private fun SessionJson.toSession() =
    Session(id, projectId, LocalDate.parse(date), durationSeconds, createdAt, updatedAt, deletedAt)
