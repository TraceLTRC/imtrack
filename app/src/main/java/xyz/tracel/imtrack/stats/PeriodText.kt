package xyz.tracel.imtrack.stats

import android.content.res.Resources
import androidx.annotation.StringRes
import xyz.tracel.imtrack.R
import java.time.format.DateTimeFormatter
import java.util.Locale

/** `Last week`, `Week of 21 Sep`, `September`, `December 2025` or `2025`. */
fun PeriodLabel.text(resources: Resources, locale: Locale = Locale.getDefault()): String = when (this) {
    PeriodLabel.LastWeek -> resources.getString(R.string.last_week)
    is PeriodLabel.WeekOf -> resources.getString(
        R.string.week_of,
        DateTimeFormatter.ofPattern(if (withYear) "d MMM yyyy" else "d MMM", locale).format(monday),
    )
    // LLLL is the month's standalone name, as a heading uses it.
    is PeriodLabel.Month -> DateTimeFormatter.ofPattern(if (withYear) "LLLL yyyy" else "LLLL", locale).format(month)
    is PeriodLabel.Year -> year.toString()
}

/** The segmented button's label: `Week`, `Month`, `Year`. */
@get:StringRes
val PeriodKind.tabLabel: Int
    get() = when (this) {
        PeriodKind.Week -> R.string.period_week
        PeriodKind.Month -> R.string.period_month
        PeriodKind.Year -> R.string.period_year
    }

/** Past Periods with nothing before the current Period: `Nothing before this week yet.` */
@get:StringRes
val PeriodKind.nothingBeforeLabel: Int
    get() = when (this) {
        PeriodKind.Week -> R.string.past_periods_empty_week
        PeriodKind.Month -> R.string.past_periods_empty_month
        PeriodKind.Year -> R.string.past_periods_empty_year
    }

/** The current Period's name on the total card: `This week`, `This month`, `This year`. */
@get:StringRes
val PeriodKind.currentLabel: Int
    get() = when (this) {
        PeriodKind.Week -> R.string.this_week
        PeriodKind.Month -> R.string.this_month
        PeriodKind.Year -> R.string.this_year
    }
