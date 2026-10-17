package xyz.tracel.imtrack.stopwatch

/** The running card's clock: `12:34` under an hour, `1:02:03` from an hour on. */
fun formatElapsed(seconds: Long): String {
    val hours = seconds / 3_600
    val minutes = seconds % 3_600 / 60
    val rest = seconds % 60
    val ss = rest.toString().padStart(2, '0')
    return if (hours > 0) "$hours:${minutes.toString().padStart(2, '0')}:$ss" else "$minutes:$ss"
}
