package xyz.tracel.imtrack.data

import java.time.LocalDate

/** A Project's latest live Session: the one with the highest [date], then the highest [createdAt]. */
data class ProjectActivity(
    val projectId: String,
    val date: LocalDate,
    val createdAt: Long,
)
