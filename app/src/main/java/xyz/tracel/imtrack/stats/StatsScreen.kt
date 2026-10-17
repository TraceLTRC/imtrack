package xyz.tracel.imtrack.stats

import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import xyz.tracel.imtrack.R
import xyz.tracel.imtrack.data.Project
import xyz.tracel.imtrack.projects.ProjectColors
import xyz.tracel.imtrack.projects.ProjectMarker
import xyz.tracel.imtrack.sessions.formatDuration
import xyz.tracel.imtrack.ui.theme.ImtrackTheme

@Composable
fun StatsScreen(modifier: Modifier = Modifier, viewModel: StatsViewModel = hiltViewModel()) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    uiState?.let {
        StatsScreen(it, onSelectPeriod = viewModel::selectPeriod, onSelectProject = viewModel::selectProject, modifier = modifier)
    }
}

@Composable
fun StatsScreen(
    state: StatsUiState,
    onSelectPeriod: (PeriodKind) -> Unit,
    onSelectProject: (Project?) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item(key = "periods") { PeriodTabs(state.period, onSelectPeriod, Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) }
        item(key = "filter") { ProjectFilter(state.projects, state.selected, onSelectProject) }
        item(key = "total") { TotalCard(state) }
        state.streak?.let { streak -> item(key = "streak") { StreakCard(streak) } }
        if (state.byProject.isNotEmpty()) {
            item(key = "byProject") {
                StatsCard {
                    CardLabel(stringResource(R.string.by_project))
                    ByProjectRows(state.byProject)
                }
            }
        }
        item(key = "heatmap") { HeatmapCard(state.heatmap, state.selected) }
    }
}

/** `Week` `Month` `Year`. */
@Composable
fun PeriodTabs(selected: PeriodKind, onSelect: (PeriodKind) -> Unit, modifier: Modifier = Modifier) {
    SingleChoiceSegmentedButtonRow(modifier.fillMaxWidth()) {
        PeriodKind.entries.forEachIndexed { index, period ->
            SegmentedButton(
                selected = period == selected,
                onClick = { onSelect(period) },
                shape = SegmentedButtonDefaults.itemShape(index, PeriodKind.entries.size),
                label = { Text(stringResource(period.tabLabel)) },
            )
        }
    }
}

/** `All Projects`, then the Projects with their markers, scrolling sideways. */
@Composable
private fun ProjectFilter(projects: List<Project>, selected: Project?, onSelect: (Project?) -> Unit) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
    ) {
        FilterChip(
            selected = selected == null,
            onClick = { onSelect(null) },
            label = { Text(stringResource(R.string.stats_all_projects)) },
        )
        projects.forEach { project ->
            FilterChip(
                selected = selected?.id == project.id,
                onClick = { onSelect(project) },
                label = { Text(project.name) },
                leadingIcon = { ProjectMarker(color = project.color, archived = project.archived) },
            )
        }
    }
}

@Composable
private fun TotalCard(state: StatsUiState) {
    val period = stringResource(state.period.currentLabel)
    StatsCard {
        CardLabel(state.selected?.let { stringResource(R.string.stats_label_filtered, period, it.name) } ?: period)
        Text(formatDuration(state.totalSeconds), style = MaterialTheme.typography.displaySmall)
        state.comparison?.let { ComparisonTrack(state.totalSeconds, it) }
    }
}

/**
 * The fill is this Period so far, the tick the previous Period at the same point, both scaled to the
 * larger one. Never a minus sign.
 */
@Composable
private fun ComparisonTrack(currentSeconds: Long, comparison: Comparison) {
    val max = maxOf(currentSeconds, comparison.previousSeconds, 1L).toFloat()
    val tickColor = MaterialTheme.colorScheme.onSurfaceVariant
    BoxWithConstraints(
        Modifier
            .padding(top = 12.dp)
            .fillMaxWidth()
            .height(16.dp),
    ) {
        Box(
            Modifier
                .align(Alignment.CenterStart)
                .fillMaxWidth()
                .height(8.dp)
                .background(MaterialTheme.colorScheme.surfaceContainerHigh, RoundedCornerShape(4.dp)),
        ) {
            Box(
                Modifier
                    .fillMaxWidth(currentSeconds / max)
                    .fillMaxHeight()
                    .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(4.dp)),
            )
        }
        Box(
            Modifier
                .offset(x = (maxWidth - 2.dp) * (comparison.previousSeconds / max))
                .width(2.dp)
                .fillMaxHeight()
                .background(tickColor, RoundedCornerShape(1.dp)),
        )
    }
    Text(
        stringResource(
            R.string.comparison_caption,
            comparison.previousLabel.text(LocalResources.current),
            formatDuration(comparison.previousSeconds),
        ),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 6.dp),
    )
}

@Composable
private fun StreakCard(streak: Streak) {
    StatsCard {
        Row {
            StreakColumn(stringResource(R.string.stats_streak), streak.current, Modifier.weight(1f))
            StreakColumn(stringResource(R.string.stats_longest), streak.longest, Modifier.weight(1f))
        }
    }
}

@Composable
private fun StreakColumn(label: String, days: Int, modifier: Modifier = Modifier) {
    Column(modifier) {
        CardLabel(label)
        Text(pluralStringResource(R.plurals.streak_days, days, days), style = MaterialTheme.typography.headlineMedium)
    }
}

/**
 * By Project rows, most time first: marker (hollow if archived), name and total, with a 6 dp bar in the
 * Project's colour scaled to the top row. Past Periods reuses them.
 */
@Composable
fun ByProjectRows(rows: List<ByProjectRow>, modifier: Modifier = Modifier) {
    val top = rows.maxOfOrNull { it.seconds }?.coerceAtLeast(1L) ?: 1L
    Column(modifier) {
        rows.forEach { row ->
            Column(Modifier.padding(vertical = 8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    ProjectMarker(color = row.project.color, archived = row.project.archived)
                    Text(row.project.name, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                    Text(
                        formatDuration(row.seconds),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.height(4.dp))
                // Lines up with the name, after the marker.
                DurationBar(row.seconds.toFloat() / top, Color(row.project.color), Modifier.padding(start = 22.dp))
            }
        }
    }
}

/** A 6 dp bar, [fraction] of the width, for a total scaled to the largest in its list. */
@Composable
fun DurationBar(fraction: Float, color: Color, modifier: Modifier = Modifier) {
    Box(
        modifier
            .fillMaxWidth(fraction)
            .height(6.dp)
            .background(color, RoundedCornerShape(3.dp)),
    )
}

@Composable
fun StatsCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = RoundedCornerShape(16.dp),
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
    ) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) { content() }
    }
}

@Composable
fun CardLabel(text: String) {
    Text(text, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

private val previewProjects = listOf("3D modelling", "Guitar", "Prop gun build", "Reading", "Blender donut course")
    .mapIndexed { index, name -> Project(name, name, ProjectColors[index], archived = index == 4, createdAt = 0L, updatedAt = 0L, deletedAt = null) }

private val populated = StatsUiState(
    period = PeriodKind.Week,
    projects = previewProjects,
    selected = null,
    totalSeconds = 12_300L,
    comparison = Comparison(previousSeconds = 20_400L, previousLabel = PeriodLabel.LastWeek),
    streak = Streak(current = 12, longest = 14),
    byProject = listOf(
        ByProjectRow(previewProjects[0], 5_400L),
        ByProjectRow(previewProjects[2], 3_600L),
        ByProjectRow(previewProjects[1], 2_700L),
        ByProjectRow(previewProjects[4], 600L),
    ),
    heatmap = previewHeatmap(),
)

@Composable
private fun StatsPreview(state: StatsUiState) {
    ImtrackTheme {
        Surface {
            StatsScreen(state, onSelectPeriod = {}, onSelectProject = {})
        }
    }
}

@Preview(name = "Populated", showBackground = true, widthDp = 360, heightDp = 960)
@Preview(name = "Populated, dark", showBackground = true, widthDp = 360, heightDp = 960, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun StatsPopulatedPreview() {
    StatsPreview(populated)
}

@Preview(name = "Past the previous total", showBackground = true)
@Composable
private fun StatsPastPreviousPreview() {
    StatsPreview(
        populated.copy(
            period = PeriodKind.Month,
            totalSeconds = 120_000L,
            comparison = Comparison(99_600L, PeriodLabel.Month(java.time.YearMonth.of(2026, 9), withYear = false)),
        ),
    )
}

@Preview(name = "A Project filtered", showBackground = true)
@Composable
private fun StatsFilteredPreview() {
    StatsPreview(
        populated.copy(
            selected = previewProjects[1],
            totalSeconds = 2_700L,
            comparison = Comparison(3_600L, PeriodLabel.LastWeek),
            streak = null,
            byProject = emptyList(),
        ),
    )
}

@Preview(name = "No Sessions at all", showBackground = true)
@Composable
private fun StatsEmptyPreview() {
    StatsPreview(
        StatsUiState(
            period = PeriodKind.Week,
            projects = previewProjects.take(1),
            selected = null,
            totalSeconds = 0L,
            comparison = null,
            streak = Streak(current = 0, longest = 0),
            byProject = emptyList(),
            heatmap = previewHeatmap(empty = true),
        ),
    )
}
