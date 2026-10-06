package xyz.tracel.imtrack.projects

import kotlin.random.Random

/** The 8 fixed Project colours, as ARGB. */
val ProjectColors: List<Int> = listOf(
    0xFF7C4DFF, 0xFFEF6C00, 0xFF00897B, 0xFFC2185B,
    0xFF1E88E5, 0xFF43A047, 0xFFF9A825, 0xFF6D4C41,
).map { it.toInt() }

/**
 * The colour a new Project starts with: random among those no active Project uses,
 * or random among all 8 if every one is taken.
 */
fun pickDefaultColor(activeColors: Collection<Int>, random: Random): Int {
    val unused = ProjectColors.filterNot { it in activeColors }
    return unused.ifEmpty { ProjectColors }.random(random)
}
