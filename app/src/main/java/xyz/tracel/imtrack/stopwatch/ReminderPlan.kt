package xyz.tracel.imtrack.stopwatch

import xyz.tracel.imtrack.data.RunningStopwatch

/** What the Reminder should do now. */
sealed interface ReminderPlan {
    /** No Stopwatch, or its Reminder was already shown in this run. */
    data object None : ReminderPlan

    /** Due at [at] (epoch millis): set the alarm for then. */
    data class ScheduleAt(val at: Long) : ReminderPlan

    /** Already due, for example after a reboot past the due time. */
    data object ShowNow : ReminderPlan
}

/** The Reminder is due [hours] after [running] started, once per run (CONTEXT.md, Reminder). */
fun reminderPlan(running: RunningStopwatch?, hours: Int, now: Long): ReminderPlan {
    if (running == null || running.reminderShown) return ReminderPlan.None
    val due = running.startedAt + hours * 3_600_000L
    return if (now >= due) ReminderPlan.ShowNow else ReminderPlan.ScheduleAt(due)
}
