package xyz.tracel.imtrack.history

import org.junit.Assert.assertEquals
import org.junit.Test
import xyz.tracel.imtrack.data.Project
import xyz.tracel.imtrack.data.Session
import java.time.LocalDate

class HistoryDaysTest {
    private val oct2 = LocalDate.of(2026, 10, 2)
    private val oct3 = LocalDate.of(2026, 10, 3)
    private val oct4 = LocalDate.of(2026, 10, 4)
    private val guitar = project("guitar")
    private val reading = project("reading")

    @Test
    fun oneDayPerDateWithSessionsNewestDayFirst() {
        val days = historyDays(
            listOf(session("a", oct3), session("b", oct4), session("c", oct2), session("d", oct4)),
            listOf(guitar),
        )

        assertEquals(listOf(oct4, oct3, oct2), days.map { it.date })
    }

    @Test
    fun rowsInADayAreNewestCreatedFirst() {
        val days = historyDays(
            listOf(
                session("old", oct4, createdAt = 100L),
                session("new", oct4, createdAt = 300L),
                session("mid", oct4, createdAt = 200L, projectId = "reading"),
            ),
            listOf(guitar, reading),
        )

        val rows = days.single().rows
        assertEquals(listOf("new", "mid", "old"), rows.map { it.session.id })
        assertEquals(listOf(guitar, reading, guitar), rows.map { it.project })
    }

    @Test
    fun aDaysTotalAddsUpTheSecondsBeforeAnyRounding() {
        // Each rounds up to 1 min on its own, but together they are 1 min 30 s: 2 min, not 3.
        val days = historyDays(
            listOf(session("a", oct4, seconds = 30L), session("b", oct4, seconds = 30L), session("c", oct4, seconds = 30L)),
            listOf(guitar),
        )

        assertEquals(90L, days.single().totalSeconds)
    }

    @Test
    fun noSessionsIsNoDays() {
        assertEquals(emptyList<HistoryDay>(), historyDays(emptyList(), listOf(guitar)))
    }

    private fun project(id: String) =
        Project(id, id, color = 0, archived = false, createdAt = 0L, updatedAt = 0L, deletedAt = null)

    private fun session(
        id: String,
        date: LocalDate,
        createdAt: Long = 0L,
        seconds: Long = 60L,
        projectId: String = "guitar",
    ) = Session(id, projectId, date, seconds, createdAt = createdAt, updatedAt = createdAt, deletedAt = null)
}
