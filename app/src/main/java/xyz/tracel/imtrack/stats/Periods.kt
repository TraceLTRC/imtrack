package xyz.tracel.imtrack.stats

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters

/** Days from [start] to [endInclusive]. */
data class DateRange(val start: LocalDate, val endInclusive: LocalDate)

/** A Period (CONTEXT.md): a calendar week (Monday to Sunday, whatever the locale), month or year. */
enum class PeriodKind {
    Week,
    Month,
    Year,
    ;

    /** The whole Period that [date] falls in. */
    fun rangeContaining(date: LocalDate): DateRange = when (this) {
        Week -> date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).let { DateRange(it, it.plusDays(6)) }
        Month -> DateRange(date.withDayOfMonth(1), date.with(TemporalAdjusters.lastDayOfMonth()))
        Year -> DateRange(date.withDayOfYear(1), date.with(TemporalAdjusters.lastDayOfYear()))
    }

    /**
     * The current Period up to [today], and the previous one cut off at the same point (the Comparison):
     * the same weekday, the same day of the month (clamped to its length) or the same month and day.
     */
    fun soFar(today: LocalDate): SoFar {
        val current = rangeContaining(today)
        val previousStart = current.previous(this).start
        val samePoint = when (this) {
            Week -> previousStart.plusDays(ChronoUnit.DAYS.between(current.start, today))
            // plusMonths and plusYears clamp to the shorter month: 31 Mar → 28 Feb, 29 Feb → 28 Feb.
            Month -> today.minusMonths(1)
            Year -> today.minusYears(1)
        }
        return SoFar(current = DateRange(current.start, today), previous = DateRange(previousStart, samePoint))
    }
}

/** This Period so far and the previous Period up to the same point. */
data class SoFar(val current: DateRange, val previous: DateRange)

/** The whole Period before this one. */
fun DateRange.previous(kind: PeriodKind): DateRange = kind.rangeContaining(start.minusDays(1))
