package xyz.tracel.imtrack.projects

import xyz.tracel.imtrack.data.Project

/** The Projects page's two sections. */
data class ProjectsPageList(
    val active: List<Project>,
    val archived: List<Project>,
)

private val byName = compareBy<Project, String>(String.CASE_INSENSITIVE_ORDER) { it.name }

/** Active Projects A–Z, then archived Projects A–Z. */
fun sortForProjectsPage(projects: List<Project>): ProjectsPageList {
    val (archived, active) = projects.partition { it.archived }
    return ProjectsPageList(active = active.sortedWith(byName), archived = archived.sortedWith(byName))
}
