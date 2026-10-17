package xyz.tracel.imtrack.stats

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate
import java.time.Month

class HeatmapTest {
    @Test
    fun levelsAreFixedMinuteAmounts() {
        assertEquals(0, heatmapLevel(0L))
        assertEquals(1, heatmapLevel(1L))
        assertEquals(1, heatmapLevel(29 * 60L))
        assertEquals(2, heatmapLevel(30 * 60L))
        assertEquals(2, heatmapLevel(59 * 60L))
        assertEquals(3, heatmapLevel(60 * 60L))
        assertEquals(3, heatmapLevel(119 * 60L))
        assertEquals(4, heatmapLevel(120 * 60L))
        assertEquals(4, heatmapLevel(10 * 3_600L))
    }

    @Test
    fun aPartMinuteRoundsUpLikeEveryDuration() {
        // 29 min 1 s shows as 30 min, so it's in the 30–59 level.
        assertEquals(2, heatmapLevel(29 * 60L + 1))
    }

    @Test
    fun twentySixWeekColumnsEndWithTheCurrentWeek() {
        // Wed 7 Oct 2026.
        val layout = heatmapLayout(LocalDate.of(2026, 10, 7))

        assertEquals(26, layout.mondays.size)
        assertEquals(LocalDate.of(2026, 10, 5), layout.mondays.last())
        assertEquals(LocalDate.of(2026, 4, 13), layout.mondays.first())
        assertEquals(DateRange(LocalDate.of(2026, 4, 13), LocalDate.of(2026, 10, 7)), layout.range)
    }

    @Test
    fun futureDaysInTheCurrentWeekAreBlank() {
        val layout = heatmapLayout(LocalDate.of(2026, 10, 7))

        assertEquals(LocalDate.of(2026, 10, 7), layout.dayAt(column = 25, row = 2))
        assertNull(layout.dayAt(column = 25, row = 3))
        assertNull(layout.dayAt(column = 25, row = 6))
        assertEquals(LocalDate.of(2026, 4, 19), layout.dayAt(column = 0, row = 6))
    }

    @Test
    fun aMonthIsLabelledAboveTheColumnWhoseMondayIsItsFirstSevenDays() {
        val layout = heatmapLayout(LocalDate.of(2026, 10, 7))

        assertEquals(
            listOf(
                MonthLabel(3, Month.MAY), // Mon 4 May
                MonthLabel(7, Month.JUNE), // Mon 1 Jun
                MonthLabel(12, Month.JULY), // Mon 6 Jul
                MonthLabel(16, Month.AUGUST), // Mon 3 Aug
                MonthLabel(21, Month.SEPTEMBER), // Mon 7 Sep
                MonthLabel(25, Month.OCTOBER), // Mon 5 Oct
            ),
            layout.monthLabels,
        )
    }

    @Test
    fun aRangeAcrossTheNewYearLabelsJanuary() {
        // Sat 20 Feb 2027: the columns start on Mon 24 Aug 2026.
        val layout = heatmapLayout(LocalDate.of(2027, 2, 20))

        assertEquals(LocalDate.of(2026, 8, 24), layout.mondays.first())
        assertEquals(
            listOf(Month.SEPTEMBER, Month.OCTOBER, Month.NOVEMBER, Month.DECEMBER, Month.JANUARY, Month.FEBRUARY),
            layout.monthLabels.map { it.month },
        )
        // Mon 4 Jan 2027.
        assertEquals(MonthLabel(19, Month.JANUARY), layout.monthLabels[4])
        // Fri 1 Jan 2027 is in the week of Mon 28 Dec.
        assertEquals(LocalDate.of(2027, 1, 1), layout.dayAt(column = 18, row = 4))
    }
}
