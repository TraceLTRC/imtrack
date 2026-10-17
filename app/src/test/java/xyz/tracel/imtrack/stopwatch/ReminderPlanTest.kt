package xyz.tracel.imtrack.stopwatch

import org.junit.Assert.assertEquals
import org.junit.Test
import xyz.tracel.imtrack.data.RunningStopwatch
import java.time.Instant
import java.time.LocalDate

class ReminderPlanTest {
    private val start = Instant.parse("2026-10-04T12:00:00Z").toEpochMilli()
    private val running = RunningStopwatch("guitar", startedAt = start, startDate = LocalDate.of(2026, 10, 4))
    private val hour = 3_600_000L

    @Test
    fun beforeTheDueTimeItIsScheduledForStartPlusTheHours() {
        assertEquals(ReminderPlan.ScheduleAt(start + 4 * hour), reminderPlan(running, hours = 4, now = start + 1_000L))
        assertEquals(ReminderPlan.ScheduleAt(start + hour), reminderPlan(running, hours = 1, now = start + hour - 1))
    }

    @Test
    fun onceDueItShowsNow() {
        assertEquals(ReminderPlan.ShowNow, reminderPlan(running, hours = 4, now = start + 4 * hour))
        // After a reboot long past the due time.
        assertEquals(ReminderPlan.ShowNow, reminderPlan(running, hours = 4, now = start + 30 * hour))
    }

    @Test
    fun aShownReminderIsNeverShownOrScheduledAgainInTheSameRun() {
        assertEquals(ReminderPlan.None, reminderPlan(running.copy(reminderShown = true), hours = 4, now = start + 30 * hour))
        assertEquals(ReminderPlan.None, reminderPlan(running.copy(reminderShown = true), hours = 8, now = start))
    }

    @Test
    fun noStopwatchMeansNoReminder() {
        assertEquals(ReminderPlan.None, reminderPlan(null, hours = 4, now = start))
    }

    @Test
    fun changingTheHoursReschedulesFromTheStart() {
        val now = start + 2 * hour
        assertEquals(ReminderPlan.ScheduleAt(start + 3 * hour), reminderPlan(running, hours = 3, now = now))
        assertEquals(ReminderPlan.ShowNow, reminderPlan(running, hours = 1, now = now))
    }
}
