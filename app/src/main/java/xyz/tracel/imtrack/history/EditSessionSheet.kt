package xyz.tracel.imtrack.history

import android.content.res.Configuration
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import xyz.tracel.imtrack.R
import xyz.tracel.imtrack.data.Project
import xyz.tracel.imtrack.data.Session
import xyz.tracel.imtrack.projects.ProjectColors
import xyz.tracel.imtrack.projects.ProjectMarker
import xyz.tracel.imtrack.sessions.DateField
import xyz.tracel.imtrack.sessions.MinutesField
import xyz.tracel.imtrack.sessions.dayLabel
import xyz.tracel.imtrack.sessions.displayMinutes
import xyz.tracel.imtrack.sessions.formatDuration
import xyz.tracel.imtrack.sessions.parseMinutes
import xyz.tracel.imtrack.sessions.text
import xyz.tracel.imtrack.ui.theme.ImtrackTheme
import java.time.LocalDate

/**
 * The Edit Session sheet, prefilled from the Session. Delete asks for confirmation first.
 * Dismissing the sheet saves nothing.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditSessionSheet(
    sheet: EditSessionSheet,
    today: LocalDate,
    onSave: (projectId: String, minutes: Int, date: LocalDate) -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit,
) {
    val session = sheet.session
    var projectId by rememberSaveable(session.id) { mutableStateOf(session.projectId) }
    var minutes by rememberSaveable(session.id) { mutableStateOf(displayMinutes(session.durationSeconds).toString()) }
    var date by rememberSaveable(session.id) { mutableStateOf(session.date) }
    var confirmingDelete by rememberSaveable(session.id) { mutableStateOf(false) }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        EditSessionSheetContent(
            projects = sheet.projects,
            today = today,
            projectId = projectId,
            onProjectChange = { projectId = it },
            minutes = minutes,
            onMinutesChange = { minutes = it },
            date = date,
            onDateChange = { date = it },
            onSave = onSave,
            onDelete = { confirmingDelete = true },
        )
    }
    if (confirmingDelete) {
        DeleteSessionDialog(
            session = session,
            // The Session's own Project is always among the chips.
            projectName = sheet.projects.first { it.id == session.projectId }.name,
            today = today,
            onConfirm = onDelete,
            onDismiss = { confirmingDelete = false },
        )
    }
}

@Composable
private fun EditSessionSheetContent(
    projects: List<Project>,
    today: LocalDate,
    projectId: String,
    onProjectChange: (String) -> Unit,
    minutes: String,
    onMinutesChange: (String) -> Unit,
    date: LocalDate,
    onDateChange: (LocalDate) -> Unit,
    onSave: (projectId: String, minutes: Int, date: LocalDate) -> Unit,
    onDelete: () -> Unit,
) {
    val valid = parseMinutes(minutes)
    Column(
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text(stringResource(R.string.edit_session), style = MaterialTheme.typography.headlineSmall)
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                stringResource(R.string.project),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                projects.forEach { project ->
                    FilterChip(
                        selected = project.id == projectId,
                        onClick = { onProjectChange(project.id) },
                        leadingIcon = { ProjectMarker(color = project.color, archived = project.archived) },
                        label = { Text(project.name) },
                    )
                }
            }
        }
        MinutesField(text = minutes, onTextChange = onMinutesChange)
        DateField(date = date, today = today, onDateChange = onDateChange)
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(
                onClick = onDelete,
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
            ) {
                Text(stringResource(R.string.delete))
            }
            Spacer(Modifier.weight(1f))
            Button(onClick = { valid?.let { onSave(projectId, it, date) } }, enabled = valid != null) {
                Text(stringResource(R.string.save))
            }
        }
    }
}

/** `Delete Session?`, naming the Session as it is stored, not as edited in the sheet. */
@Composable
private fun DeleteSessionDialog(
    session: Session,
    projectName: String,
    today: LocalDate,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    val resources = LocalResources.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.delete_session_title)) },
        text = {
            Text(
                stringResource(
                    R.string.delete_session_text,
                    formatDuration(session.durationSeconds),
                    projectName,
                    dayLabel(session.date, today).text(resources),
                ),
            )
        },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
            ) {
                Text(stringResource(R.string.delete))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(android.R.string.cancel)) }
        },
    )
}

private val previewToday = LocalDate.of(2026, 10, 4)

private val previewProjects = listOf("3D modelling", "Blender donut course", "Guitar", "Reading").mapIndexed { index, name ->
    Project(name, name, ProjectColors[index], archived = index == 1, createdAt = 0L, updatedAt = 0L, deletedAt = null)
}

private val previewSession = Session(
    id = "s1",
    projectId = "Guitar",
    date = previewToday,
    durationSeconds = 2_700L,
    createdAt = 0L,
    updatedAt = 0L,
    deletedAt = null,
)

@Preview(name = "Edit Session", showBackground = true)
@Preview(name = "Edit Session, dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun EditSessionSheetPreview() {
    ImtrackTheme {
        Surface(color = MaterialTheme.colorScheme.surfaceContainerLow) {
            EditSessionSheetContent(
                projects = previewProjects,
                today = previewToday,
                projectId = previewSession.projectId,
                onProjectChange = {},
                minutes = "45",
                onMinutesChange = {},
                date = previewSession.date,
                onDateChange = {},
                onSave = { _, _, _ -> },
                onDelete = {},
            )
        }
    }
}

@Preview(name = "Delete dialog", showBackground = true)
@Preview(name = "Delete dialog, dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun DeleteSessionDialogPreview() {
    ImtrackTheme {
        DeleteSessionDialog(previewSession, projectName = "Guitar", today = previewToday, onConfirm = {}, onDismiss = {})
    }
}
