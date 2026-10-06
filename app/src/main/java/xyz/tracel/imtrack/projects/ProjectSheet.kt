package xyz.tracel.imtrack.projects

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
import androidx.compose.material3.TextButton
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
import xyz.tracel.imtrack.R
import xyz.tracel.imtrack.data.Project
import xyz.tracel.imtrack.ui.theme.ImtrackTheme

/** The New/Edit Project modal bottom sheet. Dismissing it saves nothing. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProjectSheet(
    sheet: ProjectSheet,
    onSave: (name: String, color: Int) -> Unit,
    onToggleArchived: () -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        ProjectSheetContent(sheet, onSave, onToggleArchived)
    }
}

@Composable
private fun ProjectSheetContent(
    sheet: ProjectSheet,
    onSave: (name: String, color: Int) -> Unit,
    onToggleArchived: () -> Unit,
) {
    val editing = (sheet as? ProjectSheet.Edit)?.project
    var name by rememberSaveable(sheet) { mutableStateOf(editing?.name.orEmpty()) }
    var color by rememberSaveable(sheet) {
        mutableIntStateOf(editing?.color ?: (sheet as ProjectSheet.New).defaultColor)
    }

    Column(
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text(
            stringResource(if (editing == null) R.string.new_project else R.string.edit_project),
            style = MaterialTheme.typography.headlineSmall,
        )
        ProjectNameField(name = name, onNameChange = { name = it })
        ColourPicker(selected = color, onSelect = { color = it })
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (editing != null) {
                TextButton(onClick = onToggleArchived) {
                    Text(stringResource(if (editing.archived) R.string.unarchive else R.string.archive))
                }
            }
            Spacer(Modifier.weight(1f))
            Button(onClick = { onSave(name, color) }, enabled = name.isNotBlank()) {
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

@Preview(name = "New", showBackground = true)
@Preview(name = "New, dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun ProjectSheetNewPreview() {
    ImtrackTheme {
        Surface(color = MaterialTheme.colorScheme.surfaceContainerLow) {
            ProjectSheetContent(ProjectSheet.New(defaultColor = ProjectColors[0]), { _, _ -> }, {})
        }
    }
}

@Preview(name = "Edit", showBackground = true)
@Preview(name = "Edit, dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun ProjectSheetEditPreview() {
    ImtrackTheme {
        Surface(color = MaterialTheme.colorScheme.surfaceContainerLow) {
            ProjectSheetContent(ProjectSheet.Edit(previewProject), { _, _ -> }, {})
        }
    }
}

@Preview(name = "Edit archived", showBackground = true)
@Preview(name = "Edit archived, dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun ProjectSheetEditArchivedPreview() {
    ImtrackTheme {
        Surface(color = MaterialTheme.colorScheme.surfaceContainerLow) {
            ProjectSheetContent(ProjectSheet.Edit(previewProject.copy(archived = true)), { _, _ -> }, {})
        }
    }
}
