package xyz.tracel.imtrack.placeholder

import android.content.res.Configuration
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import xyz.tracel.imtrack.ui.theme.ImtrackTheme

@Composable
fun PlaceholderScreen(viewModel: PlaceholderViewModel = hiltViewModel()) {
    val projectCount by viewModel.projectCount.collectAsStateWithLifecycle()
    PlaceholderScreen(
        projectCount = projectCount,
        onInsertSampleProject = viewModel::insertSampleProject,
    )
}

@Composable
fun PlaceholderScreen(
    projectCount: Int,
    onInsertSampleProject: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(modifier = modifier.fillMaxSize()) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(text = "$projectCount projects", style = MaterialTheme.typography.bodyLarge)
            Button(onClick = onInsertSampleProject) {
                Text("Insert sample Project")
            }
        }
    }
}

@Preview(showBackground = true, name = "No Projects")
@Preview(showBackground = true, name = "No Projects, dark", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun PlaceholderScreenEmptyPreview() {
    ImtrackTheme {
        PlaceholderScreen(projectCount = 0, onInsertSampleProject = {})
    }
}

@Preview(showBackground = true, name = "With Projects")
@Preview(showBackground = true, name = "With Projects, dark", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun PlaceholderScreenPopulatedPreview() {
    ImtrackTheme {
        PlaceholderScreen(projectCount = 3, onInsertSampleProject = {})
    }
}
