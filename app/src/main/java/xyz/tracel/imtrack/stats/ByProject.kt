package xyz.tracel.imtrack.stats

import xyz.tracel.imtrack.data.Project

/** A By Project row: a Project with time in a Period (so far, on Stats; finished, on Past Periods). */
data class ByProjectRow(val project: Project, val seconds: Long)

/** Projects with time, most first (then A–Z); archived ones included. */
fun byProject(seconds: Map<String, Long>, projects: List<Project>): List<ByProjectRow> =
    projects.mapNotNull { project -> seconds[project.id]?.takeIf { it > 0L }?.let { ByProjectRow(project, it) } }
        .sortedWith(compareByDescending<ByProjectRow> { it.seconds }.thenBy(String.CASE_INSENSITIVE_ORDER) { it.project.name })
