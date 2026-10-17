package xyz.tracel.imtrack.sessions

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import java.time.Clock
import java.time.LocalDate

/** Today's local date by [clock], emitted again just after each midnight. */
fun todayFlow(clock: Clock): Flow<LocalDate> = flow {
    while (true) {
        val today = LocalDate.now(clock)
        emit(today)
        val nextMidnight = today.plusDays(1).atStartOfDay(clock.zone).toInstant().toEpochMilli()
        delay((nextMidnight - clock.millis()).coerceAtLeast(1L))
    }
}
