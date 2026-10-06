package xyz.tracel.imtrack.projects

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class ProjectColorsTest {
    @Test
    fun thereAreEightFixedColours() {
        assertEquals(
            listOf(
                0xFF7C4DFF, 0xFFEF6C00, 0xFF00897B, 0xFFC2185B,
                0xFF1E88E5, 0xFF43A047, 0xFFF9A825, 0xFF6D4C41,
            ).map { it.toInt() },
            ProjectColors,
        )
    }

    @Test
    fun neverPicksAColourAnActiveProjectUsesWhileUnusedOnesRemain() {
        val used = ProjectColors.take(5)
        repeat(200) { seed ->
            val picked = pickDefaultColor(activeColors = used, random = Random(seed))
            assertTrue(picked in ProjectColors.drop(5))
        }
    }

    @Test
    fun picksTheOnlyUnusedColour() {
        val unused = ProjectColors[3]
        val picked = pickDefaultColor(activeColors = ProjectColors - unused, random = Random(0))
        assertEquals(unused, picked)
    }

    @Test
    fun picksFromAllEightWhenEveryColourIsUsed() {
        val picks = (0 until 200).map { seed ->
            pickDefaultColor(activeColors = ProjectColors, random = Random(seed))
        }.toSet()
        assertEquals(ProjectColors.toSet(), picks)
    }

    @Test
    fun spreadsPicksAcrossTheUnusedColours() {
        val picks = (0 until 200).map { seed ->
            pickDefaultColor(activeColors = emptyList(), random = Random(seed))
        }.toSet()
        assertEquals(ProjectColors.toSet(), picks)
    }

    @Test
    fun ignoresColoursOutsideTheFixedSet() {
        val picked = pickDefaultColor(
            activeColors = ProjectColors.drop(1) + 0xFF000000.toInt(),
            random = Random(0),
        )
        assertEquals(ProjectColors[0], picked)
    }
}
