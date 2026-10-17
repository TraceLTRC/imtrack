package xyz.tracel.imtrack.stats

import xyz.tracel.imtrack.sessions.displayMinutes
import java.time.LocalDate
import java.time.Month

/** The Heatmap's week columns. */
const val HeatmapWeeks = 26

/** A short month name above the column whose Monday is one of the month's first seven days. */
data class MonthLabel(val column: Int, val month: Month)

/** The Heatmap grid: [HeatmapWeeks] Monday-first columns ending with the week of [today]. */
data class HeatmapLayout(val mondays: List<LocalDate>, val today: LocalDate, val monthLabels: List<MonthLabel>) {
    /** The days the grid shows, up to today. */
    val range: DateRange get() = DateRange(mondays.first(), today)

    /** The day in [column] (a week) and [row] (0 is Monday); null for a future day, which stays blank. */
    fun dayAt(column: Int, row: Int): LocalDate? = mondays[column].plusDays(row.toLong()).takeIf { it <= today }
}

fun heatmapLayout(today: LocalDate): HeatmapLayout {
    val thisMonday = PeriodKind.Week.rangeContaining(today).start
    val mondays = (HeatmapWeeks - 1 downTo 0).map { thisMonday.minusWeeks(it.toLong()) }
    val labels = mondays.mapIndexedNotNull { column, monday ->
        MonthLabel(column, monday.month).takeIf { monday.dayOfMonth <= 7 }
    }
    return HeatmapLayout(mondays, today, labels)
}

/**
 * A day's colour level, from its total: 0 for nothing, then 1–29 min, 30–59, 60–119 and 120+ as 1 to 4.
 * Fixed amounts, so a day's colour never changes as habits change; minutes round up as everywhere.
 */
fun heatmapLevel(seconds: Long): Int {
    val minutes = displayMinutes(seconds)
    return when {
        minutes <= 0L -> 0
        minutes < 30L -> 1
        minutes < 60L -> 2
        minutes < 120L -> 3
        else -> 4
    }
}
