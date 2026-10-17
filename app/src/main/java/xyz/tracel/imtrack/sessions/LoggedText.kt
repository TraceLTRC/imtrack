package xyz.tracel.imtrack.sessions

import android.content.res.Resources
import xyz.tracel.imtrack.R

/** The `Logged 30 min · Guitar` snackbar, naming the day unless it's today. */
fun loggedText(resources: Resources, durationSeconds: Long, projectName: String, day: DayLabel): String {
    val duration = formatDuration(durationSeconds)
    return when (day) {
        DayLabel.Today -> resources.getString(R.string.logged_snackbar, duration, projectName)
        else -> resources.getString(R.string.logged_snackbar_dated, duration, projectName, day.text(resources))
    }
}
