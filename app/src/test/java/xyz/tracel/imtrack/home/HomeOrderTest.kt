package xyz.tracel.imtrack.home

import org.junit.Assert.assertEquals
import org.junit.Test
import xyz.tracel.imtrack.data.Project
import xyz.tracel.imtrack.data.ProjectActivity
import xyz.tracel.imtrack.projects.ProjectColors
import java.time.LocalDate

class HomeOrderTest {
    private val oct1 = LocalDate.of(2026, 10, 1)
    private val oct2 = LocalDate.of(2026, 10, 2)

    @Test
    fun projectsWithSessionsComeFirstNewestDateFirst() {
        val sorted = sortForHome(
            listOf(project("Guitar"), project("Reading"), project("3D modelling")),
            listOf(ProjectActivity("Guitar", oct1, createdAt = 500L), ProjectActivity("Reading", oct2, createdAt = 100L)),
        )

        assertEquals(listOf("Reading", "Guitar", "3D modelling"), sorted.map { it.name })
    }

    @Test
    fun theSameDateFallsBackToTheNewestCreatedSession() {
        val sorted = sortForHome(
            listOf(project("Guitar"), project("Reading")),
            listOf(ProjectActivity("Guitar", oct2, createdAt = 100L), ProjectActivity("Reading", oct2, createdAt = 200L)),
        )

        assertEquals(listOf("Reading", "Guitar"), sorted.map { it.name })
    }

    @Test
    fun projectsWithoutSessionsComeLastAToZIgnoringCase() {
        val sorted = sortForHome(
            listOf(project("zither"), project("Banjo"), project("accordion"), project("Guitar")),
            listOf(ProjectActivity("Guitar", oct1, createdAt = 0L)),
        )

        assertEquals(listOf("Guitar", "accordion", "Banjo", "zither"), sorted.map { it.name })
    }

    @Test
    fun archivedProjectsNeverAppear() {
        val sorted = sortForHome(
            listOf(project("Guitar"), project("Reading", archived = true)),
            listOf(ProjectActivity("Reading", oct2, createdAt = 0L)),
        )

        assertEquals(listOf("Guitar"), sorted.map { it.name })
    }

    /** The id is the name, so activity can refer to Projects by name. */
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
