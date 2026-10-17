package xyz.tracel.imtrack.backup

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import xyz.tracel.imtrack.data.Project
import xyz.tracel.imtrack.data.Session
import java.time.LocalDate

class BackupFormatTest {
    private val guitar = Project("p1", "Guitar", -16_742_021, archived = false, createdAt = 100L, updatedAt = 200L, deletedAt = null)
    private val donut = Project("p2", "Blender \"donut\" course", 0x7c4dff, archived = true, createdAt = 110L, updatedAt = 300L, deletedAt = null)
    private val backup = Backup(
        exportedAt = 1_791_000_000_000L,
        projects = listOf(guitar, donut),
        sessions = listOf(
            Session("s1", "p1", LocalDate.of(2026, 10, 4), 1_234L, createdAt = 400L, updatedAt = 500L, deletedAt = null),
            Session("s2", "p2", LocalDate.of(1999, 12, 31), 60L, createdAt = 410L, updatedAt = 600L, deletedAt = 600L),
        ),
    )

    @Test
    fun aBackupSurvivesTheRoundTripExactly() {
        assertEquals(BackupDecoding.Valid(backup), BackupFormat.decode(BackupFormat.encode(backup)))
    }

    @Test
    fun anEmptyBackupSurvivesTheRoundTrip() {
        val empty = Backup(exportedAt = 1L, projects = emptyList(), sessions = emptyList())

        assertEquals(BackupDecoding.Valid(empty), BackupFormat.decode(BackupFormat.encode(empty)))
    }

    @Test
    fun theFileNamesFieldsAfterTheEntityProperties() {
        val json = BackupFormat.encode(backup)

        listOf(
            "\"formatVersion\": 1", "\"exportedAt\": 1791000000000", "\"projects\"", "\"sessions\"",
            "\"id\"", "\"name\"", "\"color\"", "\"archived\": true", "\"createdAt\"", "\"updatedAt\"",
            "\"deletedAt\": null", "\"projectId\"", "\"date\": \"2026-10-04\"", "\"durationSeconds\": 1234",
        ).forEach { assertTrue("$it not in $json", it in json) }
    }

    @Test
    fun countsLeaveOutSoftDeletedRows() {
        assertEquals(2, backup.liveProjectCount)
        assertEquals(1, backup.liveSessionCount)
    }

    @Test
    fun aFileThatIsNotJsonIsRejected() {
        assertInvalid(BackupError.NotJson, "hello")
        assertInvalid(BackupError.NotJson, "{\"formatVersion\": 1,")
    }

    @Test
    fun jsonThatIsNotABackupIsRejected() {
        assertInvalid(BackupError.NotABackup, "[1, 2]")
        assertInvalid(BackupError.NotABackup, "{\"name\": \"something else\"}")
        assertInvalid(BackupError.NotABackup, "{\"formatVersion\": [1]}")
        assertInvalid(BackupError.NotABackup, "{\"formatVersion\": null}")
    }

    @Test
    fun anUnknownFormatVersionIsRejected() {
        assertInvalid(BackupError.UnknownVersion("2"), file(version = "2"))
        assertInvalid(BackupError.UnknownVersion("1.5"), file(version = "1.5"))
        assertInvalid(BackupError.UnknownVersion("one"), file(version = "\"one\""))
    }

    @Test
    fun aMissingFieldIsRejectedByName() {
        assertInvalid(BackupError.MissingField("exportedAt"), "{\"formatVersion\": 1, \"projects\": [], \"sessions\": []}")
        assertInvalid(
            BackupError.MissingField("deletedAt"),
            file(projects = """{"id": "p1", "name": "Guitar", "color": 1, "archived": false, "createdAt": 1, "updatedAt": 1}"""),
        )
    }

    @Test
    fun aFieldOfTheWrongShapeIsRejected() {
        assertInvalid(BackupError.Malformed, file(projects = project("p1").replace("\"color\": 1", "\"color\": \"red\"")))
        assertInvalid(BackupError.Malformed, file(projects = project("p1"), sessions = session("s1", "p1", date = "4 Oct")))
        assertInvalid(BackupError.Malformed, file(projects = project("p1").replace("}", ", \"extra\": 1}")))
    }

    @Test
    fun aSessionPointingAtAMissingProjectIsRejected() {
        assertInvalid(BackupError.MissingProject, file(projects = project("p1"), sessions = session("s1", "p2")))
    }

    @Test
    fun duplicateIdsAreRejected() {
        assertInvalid(BackupError.DuplicateId, file(projects = project("p1") + "," + project("p1")))
        assertInvalid(
            BackupError.DuplicateId,
            file(projects = project("p1"), sessions = session("s1", "p1") + "," + session("s1", "p1")),
        )
    }

    @Test
    fun aSessionWithoutDurationIsRejected() {
        assertInvalid(BackupError.BadDuration, file(projects = project("p1"), sessions = session("s1", "p1", seconds = 0)))
    }

    private fun assertInvalid(expected: BackupError, json: String) {
        assertEquals(BackupDecoding.Invalid(expected), BackupFormat.decode(json))
    }

    private fun file(version: String = "1", projects: String = "", sessions: String = "") =
        """{"formatVersion": $version, "exportedAt": 1, "projects": [$projects], "sessions": [$sessions]}"""

    private fun project(id: String) =
        """{"id": "$id", "name": "Guitar", "color": 1, "archived": false, "createdAt": 1, "updatedAt": 1, "deletedAt": null}"""

    private fun session(id: String, projectId: String, date: String = "2026-10-04", seconds: Long = 60) =
        """{"id": "$id", "projectId": "$projectId", "date": "$date", "durationSeconds": $seconds, "createdAt": 1, "updatedAt": 1, "deletedAt": null}"""
}
