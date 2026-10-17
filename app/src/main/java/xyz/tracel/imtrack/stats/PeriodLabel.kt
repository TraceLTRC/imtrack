package xyz.tracel.imtrack.stats

import java.time.LocalDate
import java.time.YearMonth

/** How a finished Period is named: on the Comparison caption and on Past Periods. */
sealed interface PeriodLabel {
    data object LastWeek : PeriodLabel

    /** `Week of 21 Sep`, by its Monday; the year is added outside the current year. */
    data class WeekOf(val monday: LocalDate, val withYear: Boolean) : PeriodLabel

    /** `September`, or `December 2025` outside the current year. */
    data class Month(val month: YearMonth, val withYear: Boolean) : PeriodLabel

    data class Year(val year: Int) : PeriodLabel
}

/** The label of the finished Period [range] of [kind], seen from [today]. */
fun periodLabel(kind: PeriodKind, range: DateRange, today: LocalDate): PeriodLabel = when (kind) {
    PeriodKind.Week ->
        if (range == kind.rangeContaining(today).previous(kind)) {
            PeriodLabel.LastWeek
        } else {
            PeriodLabel.WeekOf(range.start, withYear = range.start.year != today.year)
        }
    PeriodKind.Month -> PeriodLabel.Month(YearMonth.from(range.start), withYear = range.start.year != today.year)
    PeriodKind.Year -> PeriodLabel.Year(range.start.year)
}
