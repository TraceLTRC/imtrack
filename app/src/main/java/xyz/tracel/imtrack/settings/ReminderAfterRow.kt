package xyz.tracel.imtrack.settings

import android.content.res.Configuration
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ListItem
import androidx.compose.material3.Surface
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import xyz.tracel.imtrack.R
import xyz.tracel.imtrack.ui.theme.ImtrackTheme

/** What the hours field keeps of typed or pasted text: digits only, at most 3 of them. */
fun reminderHoursInput(text: String): String = text.filter { it in '0'..'9' }.take(3)

/** The hours field's text as a valid **Reminder after**, or null if it isn't (empty, or under 1). */
fun parseReminderHours(text: String): Int? = text.toIntOrNull()?.takeIf { it >= ReminderSettings.MinHours }

/**
 * **Reminder after**: a number field in hours. A value is saved on Done, when the field loses focus, or
 * when the page closes, so typing `12` doesn't save `1` on the way. An invalid value goes back to [hours].
 */
@Composable
fun ReminderAfterRow(hours: Int, onHoursChange: (Int) -> Unit, modifier: Modifier = Modifier) {
    var text by rememberSaveable(hours) { mutableStateOf(hours.toString()) }
    val focusManager = LocalFocusManager.current
    val commit = {
        val parsed = parseReminderHours(text)
        if (parsed == null) text = hours.toString() else if (parsed != hours) onHoursChange(parsed)
    }
    val currentCommit by rememberUpdatedState(commit)
    DisposableEffect(Unit) { onDispose { currentCommit() } }
    val label = stringResource(R.string.settings_reminder_after)
    ListItem(
        headlineContent = { Text(label) },
        supportingContent = { Text(stringResource(R.string.settings_reminder_after_text)) },
        trailingContent = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = reminderHoursInput(it) },
                suffix = { Text(stringResource(R.string.hours_suffix)) },
                isError = parseReminderHours(text) == null,
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                modifier = Modifier
                    .width(96.dp)
                    .semantics { contentDescription = label }
                    .onFocusChanged { if (!it.isFocused) commit() },
            )
        },
        modifier = modifier,
    )
}

@Preview(name = "Reminder after", showBackground = true)
@Preview(name = "Reminder after, dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun ReminderAfterRowPreview() {
    ImtrackTheme {
        Surface {
            ReminderAfterRow(hours = 4, onHoursChange = {})
        }
    }
}
