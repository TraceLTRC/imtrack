package xyz.tracel.imtrack.history

import android.content.res.Configuration
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import xyz.tracel.imtrack.R
import xyz.tracel.imtrack.data.Project
import xyz.tracel.imtrack.data.Session
import xyz.tracel.imtrack.projects.ProjectColors
import xyz.tracel.imtrack.projects.ProjectMarker
import xyz.tracel.imtrack.sessions.dayLabel
import xyz.tracel.imtrack.sessions.formatDuration
import xyz.tracel.imtrack.sessions.text
import xyz.tracel.imtrack.ui.theme.ImtrackTheme
import java.time.LocalDate

@Composable
fun HistoryScreen(modifier: Modifier = Modifier, viewModel: HistoryViewModel = hiltViewModel()) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val resources = LocalResources.current
    LaunchedEffect(viewModel) {
        viewModel.messages.collect { message ->
            val text = when (message) {
                HistoryMessage.Updated -> resources.getString(R.string.session_updated_snackbar)
                HistoryMessage.Deleted -> resources.getString(R.string.session_deleted_snackbar)
            }
            snackbarHostState.showSnackbar(text)
        }
    }

    Box(modifier) {
        (uiState as? HistoryUiState.Loaded)?.let { HistoryScreen(it, onSessionClick = viewModel::openEdit) }
        SnackbarHost(snackbarHostState, Modifier.align(Alignment.BottomCenter))
    }
    (uiState as? HistoryUiState.Loaded)?.let { state ->
        state.editSheet?.let { sheet ->
            EditSessionSheet(
                sheet = sheet,
                today = state.today,
                onSave = viewModel::save,
                onDelete = viewModel::delete,
                onDismiss = viewModel::dismissEdit,
            )
        }
    }
}

@Composable
fun HistoryScreen(state: HistoryUiState.Loaded, onSessionClick: (Session) -> Unit, modifier: Modifier = Modifier) {
    if (state.days.isEmpty()) {
        Text(
            stringResource(R.string.history_empty),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 40.dp),
        )
        return
    }
    LazyColumn(
        modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items(state.days, key = { it.date.toEpochDay() }) { day ->
            DayCard(day, state.today, onSessionClick, Modifier.animateItem())
        }
    }
}

@Composable
private fun DayCard(day: HistoryDay, today: LocalDate, onSessionClick: (Session) -> Unit, modifier: Modifier = Modifier) {
    val resources = LocalResources.current
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = RoundedCornerShape(16.dp),
        modifier = modifier.fillMaxWidth(),
    ) {
        Column {
            Row(Modifier.padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 6.dp)) {
                Text(
                    dayLabel(day.date, today).text(resources),
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    formatDuration(day.totalSeconds),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            day.rows.forEachIndexed { index, row ->
                if (index > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                SessionRow(row, onSessionClick)
            }
        }
    }
}

@Composable
private fun SessionRow(row: HistoryRow, onClick: (Session) -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp)
            .clickable { onClick(row.session) }
            .padding(horizontal = 16.dp),
    ) {
        ProjectMarker(color = row.project.color, archived = row.project.archived)
        Text(row.project.name, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Text(
            formatDuration(row.session.durationSeconds),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private val previewToday = LocalDate.of(2026, 10, 4)

private val previewDays: List<HistoryDay> = run {
    val projects = listOf("3D modelling", "Prop gun build", "Guitar", "Reading", "Blender donut course")
        .mapIndexed { index, name ->
            Project(name, name, ProjectColors[index], archived = index == 4, createdAt = 0L, updatedAt = 0L, deletedAt = null)
        }
    // Project index, minutes, days before today: the prototype's sample data.
    val sessions = listOf(3 to 20, 2 to 30, 0 to 60, 4 to 40, 1 to 90, 0 to 25, 2 to 15, 1 to 30, 0 to 45)
        .zip(listOf(5L, 4L, 3L, 3L, 2L, 1L, 1L, 0L, 0L))
        .mapIndexed { index, (session, daysAgo) ->
            val (project, minutes) = session
            Session(
                id = "s$index",
                projectId = projects[project].id,
                date = previewToday.minusDays(daysAgo),
                durationSeconds = minutes * 60L,
                createdAt = index.toLong(),
                updatedAt = index.toLong(),
                deletedAt = null,
            )
        }
    historyDays(sessions, projects)
}

@Preview(name = "Populated", showBackground = true)
@Preview(name = "Populated, dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun HistoryPopulatedPreview() {
    ImtrackTheme {
        Surface {
            HistoryScreen(HistoryUiState.Loaded(previewToday, previewDays), onSessionClick = {})
        }
    }
}

@Preview(name = "Empty", showBackground = true)
@Preview(name = "Empty, dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun HistoryEmptyPreview() {
    ImtrackTheme {
        Surface {
            HistoryScreen(HistoryUiState.Loaded(previewToday, days = emptyList()), onSessionClick = {})
        }
    }
}
