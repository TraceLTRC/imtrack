package xyz.tracel.imtrack.stats

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import xyz.tracel.imtrack.data.SessionRepository
import xyz.tracel.imtrack.sessions.todayFlow
import java.time.Clock
import javax.inject.Inject

/** The Streak for Home's tile and the Stats tab's Streak card, from one place. */
class StreakRepository @Inject constructor(
    private val sessionRepository: SessionRepository,
    private val clock: Clock,
) {
    /** Current and Longest, updated live as Sessions change and again at midnight. */
    fun observe(): Flow<Streak> = combine(
        todayFlow(clock).distinctUntilChanged(),
        sessionRepository.observeDaysWithTime(),
    ) { today, days -> streakOf(days, today) }.distinctUntilChanged()
}
