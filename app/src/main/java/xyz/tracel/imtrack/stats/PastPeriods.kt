package xyz.tracel.imtrack.stats

import xyz.tracel.imtrack.data.DayProjectTotal
import xyz.tracel.imtrack.data.Project
import java.time.LocalDate

/** A finished Period on Past Periods, with its By Project rows. */
data class PastPeriod(
    val range: DateRange,
    val label: PeriodLabel,
    val totalSeconds: Long,
    val byProject: List<ByProjectRow>,
)

/**
 * Every finished Period of [kind] before the one holding [today], newest first, back to the Period of
 * the first live Session in [days]. Periods with no time are included.
 */
fun pastPeriods(kind: PeriodKind, today: LocalDate, days: List<DayProjectTotal>, projects: List<Project>): List<PastPeriod> {
    val first = days.minOfOrNull { it.date } ?: return emptyList()
    val current = kind.rangeContaining(today)
    val byPeriod = days.filter { it.date < current.start }.groupBy { kind.rangeContaining(it.date) }
    return generateSequence(current.previous(kind)) { it.previous(kind) }
        .takeWhile { it.endInclusive >= first }
        .map { range ->
            val seconds = byPeriod[range].orEmpty().groupingBy { it.projectId }.fold(0L) { sum, day -> sum + day.seconds }
            PastPeriod(range, periodLabel(kind, range, today), seconds.values.sum(), byProject(seconds, projects))
        }
        .toList()
}
