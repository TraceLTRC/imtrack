package xyz.tracel.imtrack.home

import android.content.res.Configuration
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import xyz.tracel.imtrack.R
import xyz.tracel.imtrack.data.Project
import xyz.tracel.imtrack.projects.ProjectColors
import xyz.tracel.imtrack.projects.ProjectMarker
import xyz.tracel.imtrack.sessions.DateField
import xyz.tracel.imtrack.sessions.MinutesField
import xyz.tracel.imtrack.sessions.parseMinutes
import xyz.tracel.imtrack.ui.theme.ImtrackTheme
import java.time.LocalDate

/** The Log sheet for [project], dated [today] until another day is chosen. Dismissing it saves nothing. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LogSheet(
    project: Project,
    today: LocalDate,
    onSave: (minutes: Int, date: LocalDate) -> Unit,
    onDismiss: () -> Unit,
) {
    var minutes by rememberSaveable(project.id) { mutableStateOf("") }
    var date by rememberSaveable(project.id) { mutableStateOf(today) }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        LogSheetContent(
            project = project,
            today = today,
            minutes = minutes,
            onMinutesChange = { minutes = it },
            date = date,
            onDateChange = { date = it },
            onSave = onSave,
        )
    }
}

@Composable
private fun LogSheetContent(
    project: Project,
    today: LocalDate,
    minutes: String,
    onMinutesChange: (String) -> Unit,
    date: LocalDate,
    onDateChange: (LocalDate) -> Unit,
    onSave: (minutes: Int, date: LocalDate) -> Unit,
) {
    val valid = parseMinutes(minutes)
    Column(
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            ProjectMarker(color = project.color, archived = false)
            Text(project.name, style = MaterialTheme.typography.headlineSmall)
        }
        MinutesField(text = minutes, onTextChange = onMinutesChange)
        DateField(date = date, today = today, onDateChange = onDateChange)
        Row {
            Spacer(Modifier.weight(1f))
            Button(onClick = { valid?.let { onSave(it, date) } }, enabled = valid != null) {
                Text(stringResource(R.string.save))
            }
        }
    }
}

private val previewProject = Project(
    id = "1",
    name = "Guitar",
    color = ProjectColors[2],
    archived = false,
    createdAt = 0L,
    updatedAt = 0L,
    deletedAt = null,
)
private val previewToday = LocalDate.of(2026, 10, 4)

@Composable
private fun LogSheetPreview(minutes: String, date: LocalDate) {
    ImtrackTheme {
        Surface(color = MaterialTheme.colorScheme.surfaceContainerLow) {
            LogSheetContent(previewProject, previewToday, minutes, {}, date, {}, { _, _ -> })
        }
    }
}

@Preview(name = "Default", showBackground = true)
@Preview(name = "Default, dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun LogSheetDefaultPreview() {
    LogSheetPreview(minutes = "", date = previewToday)
}

@Preview(name = "Chip selected", showBackground = true)
@Preview(name = "Chip selected, dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun LogSheetChipSelectedPreview() {
    LogSheetPreview(minutes = "30", date = previewToday.minusDays(1))
}

@Preview(name = "Picked date", showBackground = true)
@Preview(name = "Picked date, dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun LogSheetPickedDatePreview() {
    LogSheetPreview(minutes = "25", date = LocalDate.of(2026, 9, 28))
}
