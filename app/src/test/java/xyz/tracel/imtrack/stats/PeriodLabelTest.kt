package xyz.tracel.imtrack.stats

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth

class PeriodLabelTest {
    private val today = LocalDate.of(2026, 10, 7)

    private fun label(kind: PeriodKind, date: LocalDate) = periodLabel(kind, kind.rangeContaining(date), today)

    @Test
    fun theWeekBeforeThisOneIsLastWeekAndOlderOnesAreNamedByTheirMonday() {
        assertEquals(PeriodLabel.LastWeek, label(PeriodKind.Week, LocalDate.of(2026, 9, 30)))
        assertEquals(PeriodLabel.WeekOf(LocalDate.of(2026, 9, 21), withYear = false), label(PeriodKind.Week, LocalDate.of(2026, 9, 24)))
        assertEquals(PeriodLabel.WeekOf(LocalDate.of(2025, 12, 29), withYear = true), label(PeriodKind.Week, LocalDate.of(2026, 1, 2)))
    }

    @Test
    fun monthsGetTheirYearOutsideTheCurrentOne() {
        assertEquals(PeriodLabel.Month(YearMonth.of(2026, 9), withYear = false), label(PeriodKind.Month, LocalDate.of(2026, 9, 3)))
        assertEquals(PeriodLabel.Month(YearMonth.of(2025, 12), withYear = true), label(PeriodKind.Month, LocalDate.of(2025, 12, 3)))
    }

    @Test
    fun yearsAreTheirNumber() {
        assertEquals(PeriodLabel.Year(2025), label(PeriodKind.Year, LocalDate.of(2025, 6, 1)))
    }
}
