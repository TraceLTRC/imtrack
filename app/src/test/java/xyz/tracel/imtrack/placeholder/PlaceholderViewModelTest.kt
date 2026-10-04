package xyz.tracel.imtrack.placeholder

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

class PlaceholderViewModelTest {
    @Test
    fun textShowsTodayFromInjectedClock() {
        val clock = Clock.fixed(Instant.parse("2026-10-04T12:00:00Z"), ZoneOffset.UTC)

        val viewModel = PlaceholderViewModel(clock)

        assertEquals("imtrack is wired up. Today is 2026-10-04.", viewModel.text.value)
    }

    @Test
    fun todayUsesTheClockTimeZone() {
        // 23:30 UTC on Oct 4 is already Oct 5 in Jakarta (UTC+7).
        val clock = Clock.fixed(Instant.parse("2026-10-04T23:30:00Z"), ZoneOffset.ofHours(7))

        val viewModel = PlaceholderViewModel(clock)

        assertEquals("imtrack is wired up. Today is 2026-10-05.", viewModel.text.value)
    }
}
