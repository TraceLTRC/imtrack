package xyz.tracel.imtrack.stopwatch

import android.content.res.Configuration
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import xyz.tracel.imtrack.R
import xyz.tracel.imtrack.data.Project
import xyz.tracel.imtrack.data.elapsedSeconds
import xyz.tracel.imtrack.projects.ProjectColors
import xyz.tracel.imtrack.projects.ProjectMarker
import xyz.tracel.imtrack.ui.theme.ImtrackTheme

/**
 * Home's card for the running Stopwatch: its Project, the elapsed time ticking every second, and Stop.
 * [now] is the clock in epoch millis.
 */
@Composable
fun RunningStopwatchCard(
    project: Project,
    startedAt: Long,
    now: () -> Long,
    onStop: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val currentNow by rememberUpdatedState(now)
    val seconds by produceState(elapsedSeconds(startedAt, now()), startedAt) {
        while (true) {
            val nowMillis = currentNow()
            val elapsedMillis = nowMillis - startedAt
            value = elapsedSeconds(startedAt, nowMillis)
            // Wake just after the next whole second of the run.
            delay(1_000L - elapsedMillis.mod(1_000L))
        }
    }
    Surface(
        color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        shape = RoundedCornerShape(16.dp),
        modifier = modifier,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.padding(start = 16.dp, end = 12.dp, top = 12.dp, bottom = 12.dp),
        ) {
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    ProjectMarker(color = project.color, archived = false)
                    Text(project.name, style = MaterialTheme.typography.bodyMedium)
                }
                Text(formatElapsed(seconds), style = ElapsedStyle)
            }
            Button(onClick = onStop) {
                Text(stringResource(R.string.stop))
            }
        }
    }
}

private val ElapsedStyle = TextStyle(
    fontFamily = FontFamily.Monospace,
    fontSize = 26.sp,
    fontFeatureSettings = "tnum",
)

@Preview(name = "Running", showBackground = true)
@Preview(name = "Running, dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun RunningCardPreview() {
    RunningCardPreview(elapsedSeconds = 754L)
}

@Preview(name = "Running over an hour", showBackground = true)
@Composable
private fun RunningCardOverAnHourPreview() {
    RunningCardPreview(elapsedSeconds = 3_723L)
}

@Composable
private fun RunningCardPreview(elapsedSeconds: Long) {
    val project = Project("guitar", "Guitar", ProjectColors[2], false, createdAt = 0L, updatedAt = 0L, deletedAt = null)
    ImtrackTheme {
        Surface {
            RunningStopwatchCard(project, startedAt = 0L, now = { elapsedSeconds * 1_000L }, onStop = {}, modifier = Modifier.padding(16.dp))
        }
    }
}
