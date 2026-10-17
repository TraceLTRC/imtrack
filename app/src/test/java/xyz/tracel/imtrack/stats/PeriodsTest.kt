package xyz.tracel.imtrack.stats

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class PeriodsTest {
    private fun d(y: Int, m: Int, day: Int) = LocalDate.of(y, m, day)

    @Test
    fun weeksRunMondayToSunday() {
        // Wed 7 Oct 2026.
        assertEquals(DateRange(d(2026, 10, 5), d(2026, 10, 11)), PeriodKind.Week.rangeContaining(d(2026, 10, 7)))
        assertEquals(DateRange(d(2026, 10, 5), d(2026, 10, 11)), PeriodKind.Week.rangeContaining(d(2026, 10, 5)))
        assertEquals(DateRange(d(2026, 10, 5), d(2026, 10, 11)), PeriodKind.Week.rangeContaining(d(2026, 10, 11)))
    }

    @Test
    fun monthsAndYearsAreCalendarOnes() {
        assertEquals(DateRange(d(2028, 2, 1), d(2028, 2, 29)), PeriodKind.Month.rangeContaining(d(2028, 2, 10)))
        assertEquals(DateRange(d(2026, 1, 1), d(2026, 12, 31)), PeriodKind.Year.rangeContaining(d(2026, 10, 7)))
    }

    @Test
    fun theRangeBeforeIsTheWholePreviousPeriod() {
        assertEquals(DateRange(d(2026, 9, 28), d(2026, 10, 4)), PeriodKind.Week.rangeContaining(d(2026, 10, 7)).previous(PeriodKind.Week))
        assertEquals(DateRange(d(2026, 2, 1), d(2026, 2, 28)), PeriodKind.Month.rangeContaining(d(2026, 3, 31)).previous(PeriodKind.Month))
        assertEquals(DateRange(d(2025, 1, 1), d(2025, 12, 31)), PeriodKind.Year.rangeContaining(d(2026, 1, 1)).previous(PeriodKind.Year))
    }

    @Test
    fun midWeekIsComparedWithLastWeekUpToTheSameWeekday() {
        val wed = d(2026, 10, 7)
        assertEquals(
            SoFar(current = DateRange(d(2026, 10, 5), wed), previous = DateRange(d(2026, 9, 28), d(2026, 9, 30))),
            PeriodKind.Week.soFar(wed),
        )
    }

    @Test
    fun mondayAndSundayAreTheEdgesOfTheWeek() {
        assertEquals(DateRange(d(2026, 9, 28), d(2026, 9, 28)), PeriodKind.Week.soFar(d(2026, 10, 5)).previous)
        assertEquals(DateRange(d(2026, 9, 28), d(2026, 10, 4)), PeriodKind.Week.soFar(d(2026, 10, 11)).previous)
    }

    @Test
    fun theThirtyFirstOfMarchIsComparedWithAllOfFebruary() {
        assertEquals(DateRange(d(2026, 2, 1), d(2026, 2, 28)), PeriodKind.Month.soFar(d(2026, 3, 31)).previous)
        assertEquals(DateRange(d(2026, 9, 1), d(2026, 9, 7)), PeriodKind.Month.soFar(d(2026, 10, 7)).previous)
    }

    @Test
    fun the29thOfFebruaryIsComparedWithTheYearBeforeUpTo28February() {
        assertEquals(
            SoFar(current = DateRange(d(2028, 1, 1), d(2028, 2, 29)), previous = DateRange(d(2027, 1, 1), d(2027, 2, 28))),
            PeriodKind.Year.soFar(d(2028, 2, 29)),
        )
        assertEquals(DateRange(d(2028, 2, 1), d(2028, 2, 29)), PeriodKind.Month.soFar(d(2028, 3, 29)).previous)
    }

    @Test
    fun the1stOfJanuaryIsComparedWith1January() {
        assertEquals(
            SoFar(current = DateRange(d(2027, 1, 1), d(2027, 1, 1)), previous = DateRange(d(2026, 1, 1), d(2026, 1, 1))),
            PeriodKind.Year.soFar(d(2027, 1, 1)),
        )
        assertEquals(DateRange(d(2026, 12, 1), d(2026, 12, 1)), PeriodKind.Month.soFar(d(2027, 1, 1)).previous)
        // Fri 1 Jan 2027: its week started in 2026.
        assertEquals(DateRange(d(2026, 12, 28), d(2027, 1, 1)), PeriodKind.Week.soFar(d(2027, 1, 1)).current)
    }
}
