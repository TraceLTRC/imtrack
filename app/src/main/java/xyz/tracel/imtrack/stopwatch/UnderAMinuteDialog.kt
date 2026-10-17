package xyz.tracel.imtrack.stopwatch

import android.content.res.Configuration
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.window.DialogProperties
import xyz.tracel.imtrack.R
import xyz.tracel.imtrack.ui.theme.ImtrackTheme

/** Asks whether a Stopwatch stopped in the app before a minute is kept. It can't be dismissed without a choice. */
@Composable
fun UnderAMinuteDialog(underAMinute: UnderAMinute, onKeep: () -> Unit, onDiscard: () -> Unit) {
    val seconds = underAMinute.elapsedSeconds.toInt()
    AlertDialog(
        onDismissRequest = {},
        properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false),
        title = { Text(stringResource(R.string.under_a_minute_title)) },
        text = { Text(pluralStringResource(R.plurals.under_a_minute_text, seconds, seconds, underAMinute.projectName)) },
        confirmButton = {
            TextButton(onClick = onKeep) { Text(stringResource(R.string.keep)) }
        },
        dismissButton = {
            TextButton(onClick = onDiscard) { Text(stringResource(R.string.discard)) }
        },
    )
}

@Preview(name = "Under a minute", showBackground = true)
@Preview(name = "Under a minute, dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun UnderAMinuteDialogPreview() {
    ImtrackTheme {
        Surface {
            UnderAMinuteDialog(UnderAMinute("Guitar", elapsedSeconds = 40L, stoppedAt = 0L), onKeep = {}, onDiscard = {})
        }
    }
}
