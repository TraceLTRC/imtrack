package xyz.tracel.imtrack.history

import xyz.tracel.imtrack.data.Project
import xyz.tracel.imtrack.data.Session
import java.time.LocalDate

/** One row of a History day card: a Session and the Project it was spent on. */
data class HistoryRow(val session: Session, val project: Project)

/** A History day card: the day's Sessions, newest created first, and their [totalSeconds]. */
data class HistoryDay(val date: LocalDate, val totalSeconds: Long, val rows: List<HistoryRow>)

/**
 * Groups live Sessions into History's day cards, newest day first. Totals add up seconds; rounding is
 * left to display. Sessions whose Project isn't in [projects] are left out.
 */
fun historyDays(sessions: List<Session>, projects: List<Project>): List<HistoryDay> {
    val projectsById = projects.associateBy { it.id }
    return sessions
        .mapNotNull { session -> projectsById[session.projectId]?.let { HistoryRow(session, it) } }
        .groupBy { it.session.date }
        .toSortedMap(compareByDescending { it })
        .map { (date, rows) ->
            HistoryDay(
                date = date,
                totalSeconds = rows.sumOf { it.session.durationSeconds },
                rows = rows.sortedByDescending { it.session.createdAt },
            )
        }
}
