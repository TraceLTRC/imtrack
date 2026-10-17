package xyz.tracel.imtrack.sessions

/** The longest Session you can log: a whole day. */
const val MaxMinutes = 1_440

/** The preset chips of the Minutes field. */
val MinutePresets = listOf(15, 30, 45, 60, 90)

/** Whether [minutes] can be logged: a whole number from 1 to [MaxMinutes]. */
fun isValidMinutes(minutes: Int): Boolean = minutes in 1..MaxMinutes

/** The Minutes field's text as valid minutes, or null if it isn't. */
fun parseMinutes(text: String): Int? = text.toIntOrNull()?.takeIf(::isValidMinutes)

/** What the Minutes field keeps of typed or pasted text: digits only, at most 4 of them. */
fun minutesInput(text: String): String = text.filter { it in '0'..'9' }.take(4)
