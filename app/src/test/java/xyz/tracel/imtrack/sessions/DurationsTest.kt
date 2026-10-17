package xyz.tracel.imtrack.sessions

import org.junit.Assert.assertEquals
import org.junit.Test

class DurationsTest {
    @Test
    fun zeroIsZeroMinutes() {
        assertEquals("0 min", formatDuration(0L))
    }

    @Test
    fun anyPartOfAMinuteRoundsUp() {
        assertEquals("1 min", formatDuration(1L))
        assertEquals("1 min", formatDuration(59L))
        assertEquals("1 min", formatDuration(60L))
        assertEquals("2 min", formatDuration(61L))
    }

    @Test
    fun anHourOrMoreShowsHours() {
        assertEquals("1 h", formatDuration(3_600L))
        assertEquals("1 h 15 min", formatDuration(4_500L))
        assertEquals("1 h 1 min", formatDuration(3_601L))
    }

    @Test
    fun aTotalAddsSecondsBeforeRounding() {
        // Three 20-second Sessions are 1 min together, not 3 × 1 min.
        assertEquals("1 min", formatTotal(listOf(20L, 20L, 20L)))
        assertEquals("1 h 1 min", formatTotal(listOf(1_800L, 1_799L, 2L)))
        assertEquals("0 min", formatTotal(emptyList()))
    }
}
