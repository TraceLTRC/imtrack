package xyz.tracel.imtrack.projects

import android.content.res.Configuration
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import xyz.tracel.imtrack.R
import xyz.tracel.imtrack.data.Project
import xyz.tracel.imtrack.ui.theme.ImtrackTheme

@Composable
fun ProjectsScreen(onBack: () -> Unit, viewModel: ProjectsViewModel = hiltViewModel()) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current
    LaunchedEffect(viewModel) {
        viewModel.messages.collect { message ->
            val text = when (message) {
                is ProjectsMessage.Archived -> context.getString(R.string.archived_snackbar, message.name)
                is ProjectsMessage.Unarchived -> context.getString(R.string.unarchived_snackbar, message.name)
            }
            snackbarHostState.showSnackbar(text)
        }
    }

    ProjectsScreen(
        list = uiState.list,
        snackbarHostState = snackbarHostState,
        onBack = onBack,
        onNewProject = viewModel::openNew,
        onProjectClick = viewModel::openEdit,
    )
    uiState.sheet?.let { sheet ->
        ProjectSheet(
            sheet = sheet,
            onSave = viewModel::save,
            onToggleArchived = viewModel::toggleArchived,
            onDismiss = viewModel::dismissSheet,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProjectsScreen(
    list: ProjectsPageList,
    snackbarHostState: SnackbarHostState,
    onBack: () -> Unit,
    onNewProject: () -> Unit,
    onProjectClick: (Project) -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.title_projects)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(painterResource(R.drawable.ic_arrow_back), stringResource(R.string.back))
                    }
                },
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onNewProject,
                icon = { Icon(painterResource(R.drawable.ic_add), contentDescription = null) },
                text = { Text(stringResource(R.string.new_project)) },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { innerPadding ->
        LazyColumn(
            contentPadding = PaddingValues(
                top = innerPadding.calculateTopPadding(),
                // Room for the FAB below the last row.
                bottom = innerPadding.calculateBottomPadding() + 88.dp,
            ),
        ) {
            items(list.active, key = { it.id }) { ProjectRow(it, onProjectClick) }
            if (list.archived.isNotEmpty()) {
                item(key = "archived-header") {
                    Text(
                        stringResource(R.string.archived_section),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    )
                }
                items(list.archived, key = { it.id }) { ProjectRow(it, onProjectClick) }
            }
        }
    }
}

@Composable
private fun ProjectRow(project: Project, onClick: (Project) -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .clickable { onClick(project) }
            .padding(horizontal = 16.dp),
    ) {
        ProjectMarker(color = project.color, archived = project.archived)
        Text(project.name, style = MaterialTheme.typography.bodyLarge)
    }
}

private fun previewProject(name: String, colorIndex: Int, archived: Boolean = false) = Project(
    id = name,
    name = name,
    color = ProjectColors[colorIndex],
    archived = archived,
    createdAt = 0L,
    updatedAt = 0L,
    deletedAt = null,
)

private val previewList = sortForProjectsPage(
    listOf(
        previewProject("3D modelling", 0),
        previewProject("Prop gun build", 1),
        previewProject("Guitar", 2),
        previewProject("Reading", 3),
        previewProject("Blender donut course", 4, archived = true),
        previewProject("Calligraphy", 6, archived = true),
    ),
)

@Preview(name = "Empty", showBackground = true)
@Preview(name = "Empty, dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun ProjectsScreenEmptyPreview() {
    ImtrackTheme {
        ProjectsScreen(ProjectsPageList(emptyList(), emptyList()), SnackbarHostState(), {}, {}, {})
    }
}

@Preview(name = "Populated with archived", showBackground = true)
@Preview(name = "Populated with archived, dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun ProjectsScreenPopulatedPreview() {
    ImtrackTheme {
        ProjectsScreen(previewList, SnackbarHostState(), {}, {}, {})
    }
}
