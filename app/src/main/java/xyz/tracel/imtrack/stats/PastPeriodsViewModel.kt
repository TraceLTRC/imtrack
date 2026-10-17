package xyz.tracel.imtrack.stats

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.stateIn
import xyz.tracel.imtrack.data.ProjectRepository
import xyz.tracel.imtrack.data.SessionRepository
import xyz.tracel.imtrack.sessions.todayFlow
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject

/** Past Periods: every finished Period of [period], and the one row whose By Project rows are open. */
data class PastPeriodsUiState(val period: PeriodKind, val rows: List<PastPeriod>, val expanded: LocalDate?)

@HiltViewModel
class PastPeriodsViewModel @Inject constructor(
    projectRepository: ProjectRepository,
    sessionRepository: SessionRepository,
    clock: Clock,
    private val savedStateHandle: SavedStateHandle,
) : ViewModel() {
    private val period = savedStateHandle.getStateFlow(KeyPeriod, PeriodKind.Week)

    /** The open row, by its first day. */
    private val expanded = savedStateHandle.getStateFlow<LocalDate?>(KeyExpanded, null)

    // Built without the open row, so a tap doesn't add up every day again.
    private val rows = combine(
        todayFlow(clock).distinctUntilChanged(),
        period,
        sessionRepository.observeDayProjectTotals(),
        projectRepository.observeAll(),
    ) { today, period, days, projects -> period to pastPeriods(period, today, days, projects) }

    val uiState: StateFlow<PastPeriodsUiState?> = combine(rows, expanded) { (period, rows), expanded ->
        PastPeriodsUiState(period, rows, expanded)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** Switching the Period closes the open row. */
    fun selectPeriod(period: PeriodKind) {
        savedStateHandle[KeyPeriod] = period
        savedStateHandle[KeyExpanded] = null
    }

    /** Opens [row]'s By Project rows, closing any other; tapping the open one closes it. */
    fun toggle(row: PastPeriod) {
        savedStateHandle[KeyExpanded] = row.range.start.takeIf { it != expanded.value }
    }

    private companion object {
        const val KeyPeriod = "period"
        const val KeyExpanded = "expanded"
    }
}
