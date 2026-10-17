package xyz.tracel.imtrack.stopwatch

import org.junit.Assert.assertEquals
import org.junit.Test

class ElapsedTest {
    @Test
    fun underAnHourShowsMinutesAndSeconds() {
        assertEquals("0:00", formatElapsed(0L))
        assertEquals("0:40", formatElapsed(40L))
        assertEquals("12:34", formatElapsed(754L))
        assertEquals("59:59", formatElapsed(3_599L))
    }

    @Test
    fun anHourOrMoreShowsHoursToo() {
        assertEquals("1:00:00", formatElapsed(3_600L))
        assertEquals("1:02:03", formatElapsed(3_723L))
        assertEquals("26:00:05", formatElapsed(93_605L))
    }
}
