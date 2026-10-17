package xyz.tracel.imtrack.sessions

import android.content.res.Resources
import xyz.tracel.imtrack.R
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/** How a day is named: `Today`, `Yesterday`, or a date such as `Mon 28 Sep`. */
sealed interface DayLabel {
    data object Today : DayLabel
    data object Yesterday : DayLabel
    data class Date(val date: LocalDate) : DayLabel
}

fun dayLabel(date: LocalDate, today: LocalDate): DayLabel = when (date) {
    today -> DayLabel.Today
    today.minusDays(1) -> DayLabel.Yesterday
    else -> DayLabel.Date(date)
}

/** Weekday, day and month, e.g. `Mon 28 Sep`. */
fun formatShortDate(date: LocalDate, locale: Locale = Locale.getDefault()): String =
    DateTimeFormatter.ofPattern("EEE d MMM", locale).format(date)

fun DayLabel.text(resources: Resources): String = when (this) {
    DayLabel.Today -> resources.getString(R.string.day_today)
    DayLabel.Yesterday -> resources.getString(R.string.day_yesterday)
    is DayLabel.Date -> formatShortDate(date)
}
