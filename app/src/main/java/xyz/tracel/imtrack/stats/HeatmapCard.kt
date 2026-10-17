package xyz.tracel.imtrack.stats

import android.content.res.Configuration
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import xyz.tracel.imtrack.R
import xyz.tracel.imtrack.data.Project
import xyz.tracel.imtrack.projects.ProjectColors
import xyz.tracel.imtrack.ui.theme.ImtrackTheme
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.TextStyle

private val Cell = 9.dp
private val Gap = 2.dp
private val Radius = 2.dp
private val MonthRow = 14.dp
private val DayColumn = 8.dp
private val DayColumnGap = 4.dp

/** Level 1 to 4's opacity of the Heatmap colour. */
private val LevelAlpha = listOf(0.28f, 0.5f, 0.75f, 1f)

/** Rows 2, 4 and 6 (Tuesday, Thursday, Saturday) are labelled. */
private val LabelledRows = mapOf(1 to DayOfWeek.TUESDAY, 3 to DayOfWeek.THURSDAY, 5 to DayOfWeek.SATURDAY)

/**
 * The last six months, a square per day coloured by fixed minute levels: the primary colour for All
 * Projects, [project]'s colour when filtered. Not interactive. It fits a 360 dp wide phone.
 */
@Composable
fun HeatmapCard(heatmap: HeatmapDays, project: Project?, modifier: Modifier = Modifier) {
    val title = stringResource(R.string.heatmap_title)
    val color = project?.let { Color(it.color) } ?: MaterialTheme.colorScheme.primary
    val description = project?.let { stringResource(R.string.heatmap_description_filtered, it.name) }
        ?: stringResource(R.string.heatmap_description)
    StatsCard(modifier) {
        CardLabel(project?.let { stringResource(R.string.stats_label_filtered, title, it.name) } ?: title)
        HeatmapGrid(
            heatmap,
            color,
            Modifier
                .padding(top = 8.dp)
                .semantics { contentDescription = description },
        )
        Legend(color, Modifier.align(Alignment.End).padding(top = 8.dp))
    }
}

@Composable
private fun HeatmapGrid(heatmap: HeatmapDays, color: Color, modifier: Modifier = Modifier) {
    val layout = heatmap.layout
    val empty = MaterialTheme.colorScheme.surfaceContainerHigh
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant
    val density = LocalDensity.current
    val locale = LocalConfiguration.current.locales[0]
    val measurer = rememberTextMeasurer()
    // Sized in dp, not sp: the labels sit in fixed rows and columns of the grid.
    val labelStyle = MaterialTheme.typography.labelSmall.copy(
        fontSize = with(density) { 9.dp.toSp() },
        lineHeight = with(density) { 10.dp.toSp() },
        color = labelColor,
    )
    val dayLabels = remember(measurer, labelStyle, locale) {
        LabelledRows.mapValues { (_, day) -> measurer.measure(day.getDisplayName(TextStyle.NARROW, locale), labelStyle) }
    }
    val monthLabels = remember(measurer, labelStyle, locale, layout) {
        layout.monthLabels.map { it to measurer.measure(it.month.getDisplayName(TextStyle.SHORT_STANDALONE, locale), labelStyle) }
    }
    BoxWithConstraints(modifier.fillMaxWidth()) {
        // 9 dp squares fit a 360 dp phone exactly; narrower screens get smaller ones.
        val cellDp = minOf(Cell, (maxWidth - DayColumn - DayColumnGap - Gap * (HeatmapWeeks - 1)) / HeatmapWeeks)
        val width = DayColumn + DayColumnGap + cellDp * HeatmapWeeks + Gap * (HeatmapWeeks - 1)
        val height = MonthRow + cellDp * 7 + Gap * 6
        Canvas(Modifier.size(width, height)) {
            val cell = cellDp.toPx()
            val step = cell + Gap.toPx()
            val gridLeft = (DayColumn + DayColumnGap).toPx()
            val gridTop = MonthRow.toPx()
            dayLabels.forEach { (row, text) ->
                drawText(text, topLeft = Offset(0f, gridTop + row * step + (cell - text.size.height) / 2))
            }
            monthLabels.forEach { (label, text) ->
                // The last column's label would run off the edge; it ends there instead.
                val x = (gridLeft + label.column * step).coerceAtMost(size.width - text.size.width)
                drawText(text, topLeft = Offset(x, 0f))
            }
            for (column in 0 until HeatmapWeeks) {
                for (row in 0 until 7) {
                    val day = layout.dayAt(column, row) ?: continue
                    val level = heatmapLevel(heatmap.seconds[day] ?: 0L)
                    drawRoundRect(
                        color = if (level == 0) empty else color.copy(alpha = LevelAlpha[level - 1]),
                        topLeft = Offset(gridLeft + column * step, gridTop + row * step),
                        size = Size(cell, cell),
                        cornerRadius = CornerRadius(Radius.toPx()),
                    )
                }
            }
        }
    }
}

/** `<30 min ▪▪▪▪ 2 h+`. */
@Composable
private fun Legend(color: Color, modifier: Modifier = Modifier) {
    val style = MaterialTheme.typography.labelSmall
    val textColor = MaterialTheme.colorScheme.onSurfaceVariant
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
        Text(stringResource(R.string.heatmap_legend_less), style = style, color = textColor)
        LevelAlpha.forEach { alpha ->
            Box(Modifier.size(Cell).background(color.copy(alpha = alpha), RoundedCornerShape(Radius)))
        }
        Text(stringResource(R.string.heatmap_legend_more), style = style, color = textColor)
    }
}

/** Sample days for previews: a few months of mixed amounts up to Wed 7 Oct 2026. */
internal fun previewHeatmap(empty: Boolean = false): HeatmapDays {
    val today = LocalDate.of(2026, 10, 7)
    val amounts = listOf(0L, 900L, 2_400L, 0L, 4_500L, 9_000L, 1_200L, 0L, 3_000L)
    val seconds = (0L until 150L).associate { today.minusDays(it) to amounts[(it * 7 % amounts.size).toInt()] }
    return HeatmapDays(heatmapLayout(today), if (empty) emptyMap() else seconds.filterValues { it > 0L })
}

@Preview(name = "Heatmap", showBackground = true, widthDp = 360)
@Preview(name = "Heatmap, dark", showBackground = true, widthDp = 360, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun HeatmapPreview() {
    ImtrackTheme {
        Surface {
            HeatmapCard(previewHeatmap(), project = null, modifier = Modifier.fillMaxWidth())
        }
    }
}

@Preview(name = "Heatmap, a Project filtered", showBackground = true, widthDp = 360)
@Composable
private fun HeatmapFilteredPreview() {
    val guitar = Project("guitar", "Guitar", ProjectColors[2], false, createdAt = 0L, updatedAt = 0L, deletedAt = null)
    ImtrackTheme {
        Surface {
            HeatmapCard(previewHeatmap(), project = guitar)
        }
    }
}

@Preview(name = "Heatmap, empty", showBackground = true, widthDp = 360)
@Composable
private fun HeatmapEmptyPreview() {
    ImtrackTheme {
        Surface {
            HeatmapCard(previewHeatmap(empty = true), project = null)
        }
    }
}
