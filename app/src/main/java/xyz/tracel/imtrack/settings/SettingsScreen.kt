package xyz.tracel.imtrack.settings

import android.content.res.Configuration
import android.content.res.Resources
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.annotation.StringRes
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import xyz.tracel.imtrack.R
import xyz.tracel.imtrack.backup.BackupError
import xyz.tracel.imtrack.ui.theme.ImtrackTheme

/** Any JSON document; some file managers label `.json` files as plain binary. */
private val importTypes = arrayOf("application/json", "application/octet-stream")

@Composable
fun SettingsScreen(onBack: () -> Unit, modifier: Modifier = Modifier, viewModel: SettingsViewModel = hiltViewModel()) {
    val importConfirm by viewModel.importConfirm.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val resources = LocalResources.current
    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        uri?.let(viewModel::export)
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let(viewModel::import)
    }
    LaunchedEffect(viewModel) {
        viewModel.messages.collect { message ->
            snackbarHostState.showSnackbar(message.text(resources))
        }
    }

    SettingsScreen(
        onBack = onBack,
        onExport = { exportLauncher.launch(viewModel.exportFileName()) },
        onImport = { importLauncher.launch(importTypes) },
        snackbarHostState = snackbarHostState,
        modifier = modifier,
    )
    importConfirm?.let { confirm ->
        ReplaceAllDialog(confirm, onConfirm = viewModel::confirmImport, onDismiss = viewModel::cancelImport)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onExport: () -> Unit,
    onImport: () -> Unit,
    modifier: Modifier = Modifier,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.title_settings)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(painterResource(R.drawable.ic_arrow_back), stringResource(R.string.back))
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { innerPadding ->
        Column(
            Modifier
                .padding(innerPadding)
                .verticalScroll(rememberScrollState()),
        ) {
            // The Stopwatch section's `Reminder after` row goes here (#4).
            Text(
                stringResource(R.string.settings_backup_section),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
            SettingsRow(R.string.settings_export, R.string.settings_export_text, onExport)
            SettingsRow(R.string.settings_import, R.string.settings_import_text, onImport)
        }
    }
}

@Composable
private fun SettingsRow(@StringRes headline: Int, @StringRes supporting: Int, onClick: () -> Unit) {
    ListItem(
        headlineContent = { Text(stringResource(headline)) },
        supportingContent = { Text(stringResource(supporting)) },
        modifier = Modifier.clickable(onClick = onClick),
    )
}

/**
 * Import's confirm step; the data only changes on Replace.
 * The Stopwatch slice (#11) adds `The running Stopwatch will be stopped without saving.` when one runs.
 */
@Composable
fun ReplaceAllDialog(confirm: ImportConfirm, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.replace_all_title)) },
        text = {
            Text(
                stringResource(
                    R.string.replace_all_text,
                    pluralStringResource(R.plurals.project_count, confirm.projectCount, confirm.projectCount),
                    pluralStringResource(R.plurals.session_count, confirm.sessionCount, confirm.sessionCount),
                ),
            )
        },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
            ) {
                Text(stringResource(R.string.replace))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(android.R.string.cancel)) }
        },
    )
}

private fun Resources.counts(projectCount: Int, sessionCount: Int): Array<String> = arrayOf(
    getQuantityString(R.plurals.project_count, projectCount, projectCount),
    getQuantityString(R.plurals.session_count, sessionCount, sessionCount),
)

private fun SettingsMessage.text(resources: Resources): String = when (this) {
    is SettingsMessage.Exported -> resources.getString(R.string.exported_snackbar, *resources.counts(projectCount, sessionCount))
    is SettingsMessage.ExportFailed -> resources.getString(
        R.string.export_failed_snackbar,
        reason ?: resources.getString(R.string.backup_error_unwritable),
    )
    is SettingsMessage.Imported -> resources.getString(R.string.imported_snackbar, *resources.counts(projectCount, sessionCount))
    is SettingsMessage.ImportFailed -> resources.getString(R.string.import_failed_snackbar, error.text(resources))
}

private fun BackupError.text(resources: Resources): String = when (this) {
    BackupError.NotJson -> resources.getString(R.string.backup_error_not_json)
    BackupError.NotABackup -> resources.getString(R.string.backup_error_not_a_backup)
    is BackupError.UnknownVersion -> resources.getString(R.string.backup_error_unknown_version, version)
    is BackupError.MissingField -> resources.getString(R.string.backup_error_missing_field, name)
    BackupError.Malformed -> resources.getString(R.string.backup_error_malformed)
    BackupError.MissingProject -> resources.getString(R.string.backup_error_missing_project)
    BackupError.DuplicateId -> resources.getString(R.string.backup_error_duplicate_id)
    BackupError.BadDuration -> resources.getString(R.string.backup_error_bad_duration)
    is BackupError.Failed -> detail ?: resources.getString(R.string.backup_error_failed)
}

@Preview(showBackground = true)
@Preview(name = "Dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun SettingsScreenPreview() {
    ImtrackTheme {
        SettingsScreen(onBack = {}, onExport = {}, onImport = {})
    }
}

@Preview(name = "Replace all data", showBackground = true)
@Preview(name = "Replace all data, dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun ReplaceAllDialogPreview() {
    ImtrackTheme {
        Surface {
            ReplaceAllDialog(ImportConfirm(projectCount = 4, sessionCount = 120), onConfirm = {}, onDismiss = {})
        }
    }
}
