package xyz.tracel.imtrack.home

import android.content.res.Configuration
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
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
import xyz.tracel.imtrack.ui.theme.ImtrackTheme

@Composable
fun HomeScreen(modifier: Modifier = Modifier, viewModel: HomeViewModel = hiltViewModel()) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    HomeScreen(uiState = uiState, onAddProject = viewModel::addProject, modifier = modifier)
}

@Composable
fun HomeScreen(
    uiState: HomeUiState,
    onAddProject: (name: String, color: Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    when (uiState) {
        HomeUiState.Loading -> Unit
        is HomeUiState.Empty -> HomeEmptyState(uiState, onAddProject, modifier)
        is HomeUiState.Projects -> HomeProjectList(uiState.projects, modifier)
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

/** A stand-in list of active Project names; the Home slice replaces it. */
@Composable
private fun HomeProjectList(projects: List<Project>, modifier: Modifier = Modifier) {
    LazyColumn(modifier.fillMaxSize()) {
        item {
            Text(
                stringResource(R.string.home_projects_section),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
        }
        items(projects, key = { it.id }) { project ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 56.dp)
                    .padding(horizontal = 16.dp),
            ) {
                ProjectMarker(color = project.color, archived = false)
                Text(project.name, style = MaterialTheme.typography.bodyLarge)
            }
        }
    }
}

@Preview(name = "First launch", showBackground = true)
@Preview(name = "First launch, dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun HomeFirstLaunchPreview() {
    ImtrackTheme {
        Surface {
            HomeScreen(HomeUiState.Empty(EmptyKind.FirstLaunch, ProjectColors[0]), onAddProject = { _, _ -> })
        }
    }
}

@Preview(name = "All archived", showBackground = true)
@Preview(name = "All archived, dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun HomeAllArchivedPreview() {
    ImtrackTheme {
        Surface {
            HomeScreen(HomeUiState.Empty(EmptyKind.AllArchived, ProjectColors[5]), onAddProject = { _, _ -> })
        }
    }
}

@Preview(name = "Populated", showBackground = true)
@Preview(name = "Populated, dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun HomePopulatedPreview() {
    val projects = listOf("3D modelling", "Prop gun build", "Guitar").mapIndexed { index, name ->
        Project(name, name, ProjectColors[index], archived = false, createdAt = 0L, updatedAt = 0L, deletedAt = null)
    }
    ImtrackTheme {
        Surface {
            HomeScreen(HomeUiState.Projects(projects), onAddProject = { _, _ -> })
        }
    }
}
