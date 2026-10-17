package xyz.tracel.imtrack.sessions

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MinutesTest {
    @Test
    fun wholeMinutesFromOneToADayAreValid() {
        assertEquals(1, parseMinutes("1"))
        assertEquals(30, parseMinutes("30"))
        assertEquals(1_440, parseMinutes("1440"))
        assertEquals(5, parseMinutes("05"))
    }

    @Test
    fun emptyZeroAndOverADayAreInvalid() {
        assertNull(parseMinutes(""))
        assertNull(parseMinutes("0"))
        assertNull(parseMinutes("1441"))
        assertNull(parseMinutes("99999999999"))
    }

    @Test
    fun theFieldKeepsOnlyDigits() {
        assertEquals("30", minutesInput("3-0."))
        assertEquals("1234", minutesInput("123456"))
    }
}
