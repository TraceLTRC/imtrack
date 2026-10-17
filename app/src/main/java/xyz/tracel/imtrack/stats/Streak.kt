package xyz.tracel.imtrack.stats

import java.time.LocalDate

/** The current Streak and the Longest Streak, in days (CONTEXT.md). */
data class Streak(val current: Int, val longest: Int)

/**
 * The Streak from the days that have more than zero time across all Projects. A today with nothing
 * yet doesn't break it: the count then starts from yesterday.
 */
fun streakOf(daysWithTime: Set<LocalDate>, today: LocalDate): Streak {
    val from = if (today in daysWithTime) today else today.minusDays(1)
    val current = generateSequence(from) { it.minusDays(1) }.takeWhile { it in daysWithTime }.count()
    var longest = 0
    var run = 0
    var previous: LocalDate? = null
    for (day in daysWithTime.sorted()) {
        run = if (previous != null && day == previous.plusDays(1)) run + 1 else 1
        longest = maxOf(longest, run)
        previous = day
    }
    // The current run is one of the runs, so longest is never below it.
    return Streak(current, longest)
}
