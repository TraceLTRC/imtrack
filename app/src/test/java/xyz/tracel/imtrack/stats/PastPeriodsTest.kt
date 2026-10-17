package xyz.tracel.imtrack.stats

import org.junit.Assert.assertEquals
import org.junit.Test
import xyz.tracel.imtrack.data.DayProjectTotal
import xyz.tracel.imtrack.data.Project
import java.time.LocalDate
import java.time.YearMonth

class PastPeriodsTest {
    /** Wed 7 Oct 2026. */
    private val today = LocalDate.of(2026, 10, 7)
    private val guitar = project("guitar")
    private val reading = project("reading")
    private val blender = project("blender", archived = true)
    private val projects = listOf(guitar, reading, blender)

    private fun project(id: String, archived: Boolean = false) =
        Project(id, id, 0, archived, createdAt = 0L, updatedAt = 0L, deletedAt = null)

    private fun day(m: Int, d: Int, projectId: String, seconds: Long, y: Int = 2026) =
        DayProjectTotal(LocalDate.of(y, m, d), projectId, seconds)

    @Test
    fun everyFinishedWeekNewestFirstBackToTheFirstSessionsWeekWithEmptyOnesIncluded() {
        val rows = pastPeriods(
            PeriodKind.Week,
            today,
            listOf(day(9, 16, "guitar", 600L), day(9, 30, "guitar", 1_800L), day(10, 1, "reading", 900L)),
            projects,
        )

        assertEquals(
            listOf(
                DateRange(LocalDate.of(2026, 9, 28), LocalDate.of(2026, 10, 4)),
                DateRange(LocalDate.of(2026, 9, 21), LocalDate.of(2026, 9, 27)),
                DateRange(LocalDate.of(2026, 9, 14), LocalDate.of(2026, 9, 20)),
            ),
            rows.map { it.range },
        )
        assertEquals(listOf(2_700L, 0L, 600L), rows.map { it.totalSeconds })
        assertEquals(
            listOf(PeriodLabel.LastWeek, PeriodLabel.WeekOf(LocalDate.of(2026, 9, 21), false), PeriodLabel.WeekOf(LocalDate.of(2026, 9, 14), false)),
            rows.map { it.label },
        )
    }

    @Test
    fun theCurrentPeriodIsLeftOut() {
        val rows = pastPeriods(PeriodKind.Week, today, listOf(day(10, 5, "guitar", 600L), day(9, 29, "guitar", 60L)), projects)

        assertEquals(listOf(60L), rows.map { it.totalSeconds })
    }

    @Test
    fun nothingBeforeTheCurrentPeriodIsEmpty() {
        assertEquals(emptyList<PastPeriod>(), pastPeriods(PeriodKind.Week, today, listOf(day(10, 6, "guitar", 600L)), projects))
        assertEquals(emptyList<PastPeriod>(), pastPeriods(PeriodKind.Year, today, emptyList(), projects))
    }

    @Test
    fun eachRowHasItsByProjectRowsMostFirstArchivedIncluded() {
        val rows = pastPeriods(
            PeriodKind.Month,
            today,
            listOf(day(9, 2, "reading", 600L), day(9, 3, "blender", 1_200L), day(9, 20, "reading", 900L), day(9, 21, "guitar", 60L)),
            projects,
        )

        assertEquals(PeriodLabel.Month(YearMonth.of(2026, 9), withYear = false), rows.single().label)
        assertEquals(
            listOf(ByProjectRow(reading, 1_500L), ByProjectRow(blender, 1_200L), ByProjectRow(guitar, 60L)),
            rows.single().byProject,
        )
    }

    @Test
    fun yearsGoBackAcrossTheNewYearAndMonthsOutsideThisYearGetTheirYear() {
        val years = pastPeriods(PeriodKind.Year, today, listOf(day(12, 30, "guitar", 60L, y = 2024)), projects)
        assertEquals(listOf(PeriodLabel.Year(2025), PeriodLabel.Year(2024)), years.map { it.label })

        val months = pastPeriods(PeriodKind.Month, LocalDate.of(2027, 1, 10), listOf(day(11, 30, "guitar", 60L)), projects)
        assertEquals(
            listOf(PeriodLabel.Month(YearMonth.of(2026, 12), true), PeriodLabel.Month(YearMonth.of(2026, 11), true)),
            months.map { it.label },
        )
    }

    @Test
    fun weeksOutsideThisYearGetTheirYear() {
        // Sat 16 Jan 2027: last week is 4–10 Jan, then the week of Mon 28 Dec 2026.
        val rows = pastPeriods(PeriodKind.Week, LocalDate.of(2027, 1, 16), listOf(day(12, 29, "guitar", 60L)), projects)

        assertEquals(
            listOf(
                PeriodLabel.LastWeek,
                PeriodLabel.WeekOf(LocalDate.of(2026, 12, 28), withYear = true),
            ),
            rows.map { it.label },
        )
        assertEquals(listOf(0L, 60L), rows.map { it.totalSeconds })
    }
}
