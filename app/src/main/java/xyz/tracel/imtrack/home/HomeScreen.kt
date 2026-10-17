package xyz.tracel.imtrack.home

import android.Manifest
import android.content.res.Configuration
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import xyz.tracel.imtrack.R
import xyz.tracel.imtrack.data.Project
import xyz.tracel.imtrack.projects.ColourPicker
import xyz.tracel.imtrack.projects.ProjectColors
import xyz.tracel.imtrack.projects.ProjectMarker
import xyz.tracel.imtrack.projects.ProjectNameField
import xyz.tracel.imtrack.sessions.formatDuration
import xyz.tracel.imtrack.sessions.loggedText
import xyz.tracel.imtrack.stopwatch.RunningStopwatchCard
import xyz.tracel.imtrack.stopwatch.UnderAMinuteDialog
import xyz.tracel.imtrack.stopwatch.needsNotificationPermission
import xyz.tracel.imtrack.ui.theme.ImtrackTheme
import java.time.LocalDate

@Composable
fun HomeScreen(modifier: Modifier = Modifier, viewModel: HomeViewModel = hiltViewModel()) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current
    val resources = LocalResources.current
    LaunchedEffect(viewModel) {
        viewModel.messages.collect { message ->
            val text = when (message) {
                is HomeMessage.Logged ->
                    loggedText(resources, message.durationSeconds, message.projectName, message.day)
                is HomeMessage.Switched ->
                    resources.getString(R.string.switched_snackbar, message.stoppedName, message.startedName)
            }
            snackbarHostState.showSnackbar(text)
        }
    }

    val underAMinute by viewModel.underAMinute.collectAsStateWithLifecycle()
    // The Stopwatch starts whatever the answer; MainActivity reconciles the notification on resume.
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}
    val start = { project: Project ->
        if (needsNotificationPermission(context) && viewModel.shouldAskForNotifications()) {
            permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        viewModel.start(project)
    }
    Box(modifier) {
        HomeScreen(
            uiState = uiState,
            onAddProject = viewModel::addProject,
            onLog = viewModel::openLog,
            onStart = start,
            onStop = viewModel::stop,
            now = viewModel::nowMillis,
        )
        SnackbarHost(snackbarHostState, Modifier.align(Alignment.BottomCenter))
    }
    underAMinute?.let {
        UnderAMinuteDialog(it, onKeep = viewModel::keepUnderAMinute, onDiscard = viewModel::discardUnderAMinute)
    }
    (uiState as? HomeUiState.Projects)?.let { state ->
        state.logSheet?.let { project ->
            LogSheet(project = project, today = state.today, onSave = viewModel::log, onDismiss = viewModel::dismissLog)
        }
    }
}

@Composable
fun HomeScreen(
    uiState: HomeUiState,
    onAddProject: (name: String, color: Int) -> Unit,
    onLog: (Project) -> Unit,
    onStart: (Project) -> Unit,
    onStop: () -> Unit,
    now: () -> Long,
    modifier: Modifier = Modifier,
) {
    when (uiState) {
        HomeUiState.Loading -> Unit
        is HomeUiState.Empty -> HomeEmptyState(uiState, onAddProject, modifier)
        is HomeUiState.Projects -> HomeProjects(uiState, onLog, onStart, onStop, now, modifier)
    }
}

@Composable
private fun HomeEmptyState(
    state: HomeUiState.Empty,
    onAddProject: (name: String, color: Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    var name by rememberSaveable { mutableStateOf("") }
    var color by rememberSaveable(state.defaultColor) { mutableIntStateOf(state.defaultColor) }

    Column(
        verticalArrangement = Arrangement.spacedBy(14.dp),
        modifier = modifier
            .fillMaxSize()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 24.dp),
    ) {
        Text(
            stringResource(state.kind.heading),
            style = MaterialTheme.typography.headlineSmall,
        )
        Text(
            stringResource(state.kind.body),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        ProjectNameField(name = name, onNameChange = { name = it })
        ColourPicker(selected = color, onSelect = { color = it })
        Button(
            onClick = { onAddProject(name, color) },
            enabled = name.isNotBlank(),
            modifier = Modifier.align(Alignment.End),
        ) {
            Text(stringResource(R.string.add_project))
        }
    }
}

/** The normal Home: the summary tiles, the running card if a Stopwatch runs, then one row per active Project. */
@Composable
private fun HomeProjects(
    state: HomeUiState.Projects,
    onLog: (Project) -> Unit,
    onStart: (Project) -> Unit,
    onStop: () -> Unit,
    now: () -> Long,
    modifier: Modifier = Modifier,
) {
    LazyColumn(modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 16.dp)) {
        item(key = "summary") {
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 12.dp),
            ) {
                SummaryTile(
                    label = stringResource(R.string.home_today_tile),
                    value = formatDuration(state.todaySeconds),
                    modifier = Modifier.weight(1f),
                )
                SummaryTile(
                    label = stringResource(R.string.home_streak_tile),
                    value = pluralStringResource(R.plurals.streak_days, state.streakDays, state.streakDays),
                    modifier = Modifier.weight(1f),
                )
            }
        }
        state.running?.let { running ->
            item(key = "running") {
                RunningStopwatchCard(
                    project = running.project,
                    startedAt = running.startedAt,
                    now = now,
                    onStop = onStop,
                    modifier = Modifier
                        .animateItem()
                        .fillMaxWidth()
                        .padding(start = 16.dp, end = 16.dp, bottom = 12.dp),
                )
            }
        }
        item(key = "section") {
            Text(
                stringResource(R.string.home_projects_section),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
        }
        items(state.rows, key = { it.project.id }) { row ->
            HomeProjectRow(row, onLog, onStart, onStop, Modifier.animateItem())
        }
    }
}

@Composable
private fun SummaryTile(label: String, value: String, modifier: Modifier = Modifier) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = RoundedCornerShape(16.dp),
        modifier = modifier,
    ) {
        Column(Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {
            Text(
                label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(value, style = MaterialTheme.typography.headlineSmall)
        }
    }
}

@Composable
private fun HomeProjectRow(
    row: HomeRow,
    onLog: (Project) -> Unit,
    onStart: (Project) -> Unit,
    onStop: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val project = row.project
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .padding(start = 16.dp, end = 8.dp),
    ) {
        ProjectMarker(color = project.color, archived = false)
        Column(Modifier.weight(1f)) {
            Text(
                project.name,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = if (row.running) FontWeight.Medium else null,
            )
            Text(
                if (row.todaySeconds > 0) {
                    stringResource(R.string.home_today_subtitle, formatDuration(row.todaySeconds))
                } else {
                    stringResource(R.string.home_nothing_today)
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (row.running) {
            IconButton(onClick = onStop) {
                Icon(painterResource(R.drawable.ic_stop), stringResource(R.string.stop_project, project.name))
            }
        } else {
            IconButton(onClick = { onStart(project) }) {
                Icon(painterResource(R.drawable.ic_play), stringResource(R.string.start_project, project.name))
            }
        }
        IconButton(onClick = { onLog(project) }) {
            Icon(painterResource(R.drawable.ic_add), stringResource(R.string.log_project, project.name))
        }
    }
}

@Preview(name = "First launch", showBackground = true)
@Preview(name = "First launch, dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun HomeFirstLaunchPreview() {
    ImtrackTheme {
        Surface {
            HomeScreen(HomeUiState.Empty(EmptyKind.FirstLaunch, ProjectColors[0]), onAddProject = { _, _ -> }, onLog = {}, onStart = {}, onStop = {}, now = { 0L })
        }
    }
}

@Preview(name = "All archived", showBackground = true)
@Preview(name = "All archived, dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun HomeAllArchivedPreview() {
    ImtrackTheme {
        Surface {
            HomeScreen(HomeUiState.Empty(EmptyKind.AllArchived, ProjectColors[5]), onAddProject = { _, _ -> }, onLog = {}, onStart = {}, onStop = {}, now = { 0L })
        }
    }
}

@Preview(name = "Populated", showBackground = true)
@Preview(name = "Populated, dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun HomePopulatedPreview() {
    val rows = listOf("Prop gun build" to 1_800L, "3D modelling" to 2_700L, "Guitar" to 0L, "Reading" to 0L)
        .mapIndexed { index, (name, todaySeconds) ->
            val project = Project(name, name, ProjectColors[index], false, createdAt = 0L, updatedAt = 0L, deletedAt = null)
            HomeRow(project, todaySeconds)
        }
    val state = HomeUiState.Projects(
        today = LocalDate.of(2026, 10, 4),
        todaySeconds = rows.sumOf { it.todaySeconds },
        streakDays = 12,
        rows = rows,
    )
    ImtrackTheme {
        Surface {
            HomeScreen(state, onAddProject = { _, _ -> }, onLog = {}, onStart = {}, onStop = {}, now = { 0L })
        }
    }
}

@Preview(name = "Stopwatch running", showBackground = true)
@Preview(name = "Stopwatch running, dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun HomeRunningPreview() {
    val rows = listOf("Guitar" to 1_800L, "Reading" to 0L)
        .mapIndexed { index, (name, todaySeconds) ->
            val project = Project(name, name, ProjectColors[index + 2], false, createdAt = 0L, updatedAt = 0L, deletedAt = null)
            HomeRow(project, todaySeconds, running = index == 0)
        }
    val state = HomeUiState.Projects(
        today = LocalDate.of(2026, 10, 4),
        todaySeconds = 1_800L,
        streakDays = 1,
        rows = rows,
        running = RunningCard(rows.first().project, startedAt = 0L),
    )
    ImtrackTheme {
        Surface {
            HomeScreen(state, onAddProject = { _, _ -> }, onLog = {}, onStart = {}, onStop = {}, now = { 754_000L })
        }
    }
}

@Preview(name = "No Streak yet", showBackground = true)
@Preview(name = "No Streak yet, dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun HomeNoStreakPreview() {
    val project = Project("guitar", "Guitar", ProjectColors[2], false, createdAt = 0L, updatedAt = 0L, deletedAt = null)
    val state = HomeUiState.Projects(
        today = LocalDate.of(2026, 10, 4),
        todaySeconds = 0L,
        streakDays = 0,
        rows = listOf(HomeRow(project, todaySeconds = 0L)),
    )
    ImtrackTheme {
        Surface {
            HomeScreen(state, onAddProject = { _, _ -> }, onLog = {}, onStart = {}, onStop = {}, now = { 0L })
        }
    }
}
