package xyz.tracel.imtrack.sessions

/** Whole minutes, rounded up: 1 s is 1 min, 61 s is 2 min. */
fun displayMinutes(seconds: Long): Long = (seconds + 59) / 60

/** `0 min`, `45 min`, `1 h` or `1 h 15 min`, from seconds rounded up to whole minutes. */
fun formatDuration(seconds: Long): String {
    val minutes = displayMinutes(seconds)
    val hours = minutes / 60
    val rest = minutes % 60
    return when {
        hours == 0L -> "$rest min"
        rest == 0L -> "$hours h"
        else -> "$hours h $rest min"
    }
}

/** A total of several Sessions: the seconds are added up first, then rounded once. */
fun formatTotal(seconds: Iterable<Long>): String = formatDuration(seconds.sum())
