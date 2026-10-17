package xyz.tracel.imtrack.stats

import android.content.res.Configuration
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import xyz.tracel.imtrack.R
import xyz.tracel.imtrack.data.Project
import xyz.tracel.imtrack.projects.ProjectColors
import xyz.tracel.imtrack.sessions.formatDuration
import xyz.tracel.imtrack.ui.theme.ImtrackTheme
import java.time.LocalDate

@Composable
fun PastPeriodsScreen(onBack: () -> Unit, viewModel: PastPeriodsViewModel = hiltViewModel()) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    PastPeriodsScreen(uiState, onBack = onBack, onSelectPeriod = viewModel::selectPeriod, onToggle = viewModel::toggle)
}

/** Totals for every finished week, month or year; a tapped row shows its By Project rows. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PastPeriodsScreen(
    state: PastPeriodsUiState?,
    onBack: () -> Unit,
    onSelectPeriod: (PeriodKind) -> Unit,
    onToggle: (PastPeriod) -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.title_past_periods)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(painterResource(R.drawable.ic_arrow_back), stringResource(R.string.back))
                    }
                },
            )
        },
    ) { innerPadding ->
        if (state == null) return@Scaffold
        val largest = state.rows.maxOfOrNull { it.totalSeconds }?.coerceAtLeast(1L) ?: 1L
        // A lazy list: years of weeks can be long.
        LazyColumn(contentPadding = PaddingValues(top = innerPadding.calculateTopPadding(), bottom = innerPadding.calculateBottomPadding() + 16.dp)) {
            item(key = "periods") {
                PeriodTabs(state.period, onSelectPeriod, Modifier.padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 12.dp))
            }
            if (state.rows.isEmpty()) {
                item(key = "empty") { Empty(state.period) }
            }
            itemsIndexed(state.rows, key = { _, row -> row.range.start.toString() }) { index, row ->
                if (index > 0) HorizontalDivider()
                PastPeriodRow(row, largest, expanded = row.range.start == state.expanded, onClick = { onToggle(row) })
            }
        }
    }
}

@Composable
private fun PastPeriodRow(row: PastPeriod, largest: Long, expanded: Boolean, onClick: () -> Unit) {
    val expandedText = stringResource(R.string.expanded)
    val collapsedText = stringResource(R.string.collapsed)
    Column(Modifier.fillMaxWidth()) {
        Column(
            Modifier
                .fillMaxWidth()
                // A Period with no time has no By Project rows to open.
                .clickable(enabled = row.totalSeconds > 0L, role = Role.Button, onClick = onClick)
                .semantics { if (row.totalSeconds > 0L) stateDescription = if (expanded) expandedText else collapsedText }
                .padding(horizontal = 16.dp, vertical = 12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(row.label.text(LocalResources.current), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                Text(
                    formatDuration(row.totalSeconds),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            DurationBar(row.totalSeconds.toFloat() / largest, MaterialTheme.colorScheme.primary, Modifier.padding(top = 6.dp))
        }
        if (expanded && row.byProject.isNotEmpty()) {
            ByProjectRows(row.byProject, Modifier.padding(start = 16.dp, end = 16.dp, bottom = 8.dp))
        }
    }
}

@Composable
private fun Empty(period: PeriodKind) {
    Text(
        stringResource(period.nothingBeforeLabel),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 40.dp),
    )
}

private val previewProjects = listOf("3D modelling", "Guitar", "Reading")
    .mapIndexed { index, name -> Project(name, name, ProjectColors[index], false, createdAt = 0L, updatedAt = 0L, deletedAt = null) }

private val previewWeeks: List<PastPeriod> = run {
    val today = LocalDate.of(2026, 10, 7)
    val totals = listOf(20_400L, 9_000L, 0L, 14_400L, 6_000L, 11_400L)
    var range = PeriodKind.Week.rangeContaining(today).previous(PeriodKind.Week)
    totals.map { total ->
        val rows = if (total == 0L) emptyList() else listOf(
            ByProjectRow(previewProjects[0], total / 2),
            ByProjectRow(previewProjects[1], total / 3),
            ByProjectRow(previewProjects[2], total - total / 2 - total / 3),
        )
        PastPeriod(range, periodLabel(PeriodKind.Week, range, today), total, rows).also { range = range.previous(PeriodKind.Week) }
    }
}

@Composable
private fun PastPeriodsPreview(state: PastPeriodsUiState) {
    ImtrackTheme {
        PastPeriodsScreen(state, onBack = {}, onSelectPeriod = {}, onToggle = {})
    }
}

@Preview(name = "Weeks", showBackground = true)
@Preview(name = "Weeks, dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun PastPeriodsWeeksPreview() {
    PastPeriodsPreview(PastPeriodsUiState(PeriodKind.Week, previewWeeks, expanded = null))
}

@Preview(name = "A row expanded", showBackground = true)
@Composable
private fun PastPeriodsExpandedPreview() {
    PastPeriodsPreview(PastPeriodsUiState(PeriodKind.Week, previewWeeks, expanded = previewWeeks[1].range.start))
}

@Preview(name = "Empty", showBackground = true)
@Preview(name = "Empty, dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun PastPeriodsEmptyPreview() {
    PastPeriodsPreview(PastPeriodsUiState(PeriodKind.Month, emptyList(), expanded = null))
}
