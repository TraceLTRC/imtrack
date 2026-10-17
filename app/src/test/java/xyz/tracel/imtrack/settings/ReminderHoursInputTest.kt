package xyz.tracel.imtrack.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ReminderHoursInputTest {
    @Test
    fun onlyWholeHoursFromOneUpAreValid() {
        assertEquals(1, parseReminderHours("1"))
        assertEquals(12, parseReminderHours("12"))
        assertNull(parseReminderHours("0"))
        assertNull(parseReminderHours(""))
    }

    @Test
    fun theFieldKeepsAtMostThreeDigits() {
        assertEquals("12", reminderHoursInput("1.2 h"))
        assertEquals("999", reminderHoursInput("99999"))
    }
}
