package xyz.tracel.imtrack.home

import xyz.tracel.imtrack.data.Project
import xyz.tracel.imtrack.data.ProjectActivity

private val byRecency = compareByDescending<ProjectActivity> { it.date }.thenByDescending { it.createdAt }
private val byName = compareBy<Project, String>(String.CASE_INSENSITIVE_ORDER) { it.name }

/**
 * Home's row order: active Projects with Sessions first, newest latest Session first;
 * then active Projects without Sessions, A–Z. Archived Projects are left out.
 */
fun sortForHome(projects: List<Project>, latest: List<ProjectActivity>): List<Project> {
    val latestById = latest.associateBy { it.projectId }
    val (used, unused) = projects.filterNot { it.archived }.partition { it.id in latestById }
    return used.sortedWith(compareBy(byRecency) { latestById.getValue(it.id) }) + unused.sortedWith(byName)
}
