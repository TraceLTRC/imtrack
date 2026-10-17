package xyz.tracel.imtrack.stats

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class StreakTest {
    private val today = LocalDate.of(2026, 10, 7)

    private fun daysAgo(vararg n: Long) = n.map { today.minusDays(it) }.toSet()

    @Test
    fun noTimeAtAllIsZero() {
        assertEquals(Streak(current = 0, longest = 0), streakOf(emptySet(), today))
    }

    @Test
    fun todayWithTimeCountsToday() {
        assertEquals(Streak(current = 3, longest = 3), streakOf(daysAgo(0, 1, 2), today))
    }

    @Test
    fun todayWithNothingYetCountsFromYesterday() {
        assertEquals(Streak(current = 2, longest = 2), streakOf(daysAgo(1, 2), today))
    }

    @Test
    fun aDayWithNothingEndsIt() {
        assertEquals(Streak(current = 1, longest = 1), streakOf(daysAgo(0, 2), today))
        // Yesterday empty too: the Streak is over.
        assertEquals(Streak(current = 0, longest = 3), streakOf(daysAgo(2, 3, 4), today))
    }

    @Test
    fun longestIsTheLongestRunEver() {
        assertEquals(Streak(current = 2, longest = 5), streakOf(daysAgo(0, 1, 10, 11, 12, 13, 14, 20), today))
    }

    @Test
    fun aRunAcrossAYearBoundaryIsOneRun() {
        val jan2 = LocalDate.of(2027, 1, 2)
        val days = (0L..4L).map { jan2.minusDays(it) }.toSet()
        assertEquals(Streak(current = 5, longest = 5), streakOf(days, jan2))
    }
}
