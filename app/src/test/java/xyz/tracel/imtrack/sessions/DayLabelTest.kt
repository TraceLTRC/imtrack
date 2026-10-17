package xyz.tracel.imtrack.sessions

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.util.Locale

class DayLabelTest {
    private val today = LocalDate.of(2026, 10, 4)

    @Test
    fun todayAndYesterdayAreNamed() {
        assertEquals(DayLabel.Today, dayLabel(today, today))
        assertEquals(DayLabel.Yesterday, dayLabel(today.minusDays(1), today))
    }

    @Test
    fun olderDaysAreDates() {
        val date = LocalDate.of(2026, 9, 28)

        assertEquals(DayLabel.Date(date), dayLabel(date, today))
    }

    @Test
    fun aDateShowsWeekdayDayAndMonth() {
        assertEquals("Mon 28 Sep", formatShortDate(LocalDate.of(2026, 9, 28), Locale.ENGLISH))
        assertEquals("Fri 2 Oct", formatShortDate(LocalDate.of(2026, 10, 2), Locale.ENGLISH))
    }
}
