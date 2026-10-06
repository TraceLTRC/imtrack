package xyz.tracel.imtrack.projects

import org.junit.Assert.assertEquals
import org.junit.Test
import xyz.tracel.imtrack.data.Project

class ProjectsPageSortTest {
    @Test
    fun splitsActiveAndArchivedEachSortedAToZ() {
        val list = sortForProjectsPage(
            listOf(
                project("Reading"),
                project("Blender donut course", archived = true),
                project("3D modelling"),
                project("Guitar"),
                project("Accordion", archived = true),
            ),
        )

        assertEquals(listOf("3D modelling", "Guitar", "Reading"), list.active.map { it.name })
        assertEquals(listOf("Accordion", "Blender donut course"), list.archived.map { it.name })
    }

    @Test
    fun ignoresCase() {
        val list = sortForProjectsPage(listOf(project("banjo"), project("Zither"), project("Accordion")))

        assertEquals(listOf("Accordion", "banjo", "Zither"), list.active.map { it.name })
    }

    @Test
    fun noArchivedProjectsMeansAnEmptyArchivedList() {
        val list = sortForProjectsPage(listOf(project("Guitar")))

        assertEquals(emptyList<Project>(), list.archived)
    }

    private fun project(name: String, archived: Boolean = false) = Project(
        id = name,
        name = name,
        color = ProjectColors[0],
        archived = archived,
        createdAt = 0L,
        updatedAt = 0L,
        deletedAt = null,
    )
}
