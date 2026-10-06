package xyz.tracel.imtrack.projects

import android.content.res.Configuration
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import xyz.tracel.imtrack.ui.theme.ImtrackTheme

private val MarkerSize = 12.dp
private val RingWidth = 2.dp

/** A 12 dp dot in the Project's colour: filled when active, a hollow ring when archived. */
@Composable
fun ProjectMarker(color: Int, archived: Boolean, modifier: Modifier = Modifier) {
    Canvas(modifier.size(MarkerSize)) {
        if (archived) {
            val ring = RingWidth.toPx()
            drawCircle(Color(color), radius = (size.minDimension - ring) / 2, style = Stroke(ring))
        } else {
            drawCircle(Color(color))
        }
    }
}

@Preview(name = "Filled and ring")
@Preview(name = "Filled and ring, dark", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun ProjectMarkerPreview() {
    ImtrackTheme {
        Surface {
            Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ProjectColors.forEach { ProjectMarker(color = it, archived = false) }
            }
            Row(Modifier.padding(16.dp, 40.dp, 16.dp, 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ProjectColors.forEach { ProjectMarker(color = it, archived = true) }
            }
        }
    }
}
