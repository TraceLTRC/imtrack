package xyz.tracel.imtrack.stats

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import xyz.tracel.imtrack.data.Project
import xyz.tracel.imtrack.data.ProjectRepository
import xyz.tracel.imtrack.data.SessionRepository
import xyz.tracel.imtrack.projects.sortForProjectsPage
import xyz.tracel.imtrack.sessions.todayFlow
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject

/** The previous Period's total at the same point, shown only when it's more than zero. */
data class Comparison(val previousSeconds: Long, val previousLabel: PeriodLabel)

/** A By Project row: a Project with time in the Period so far. */
data class ByProjectRow(val project: Project, val seconds: Long)

/**
 * The Stats tab. [selected] is the Project filter (null: All Projects); [streak] and [byProject] are
 * only there for All Projects. [projects] is the chip order: active A–Z, then archived A–Z.
 */
data class StatsUiState(
    val period: PeriodKind,
    val projects: List<Project>,
    val selected: Project?,
    val totalSeconds: Long,
    val comparison: Comparison?,
    val streak: Streak?,
    val byProject: List<ByProjectRow>,
)

/** One Period's totals so far and the previous one's at the same point, by Project ID. */
private data class PeriodTotals(
    val today: LocalDate,
    val period: PeriodKind,
    val current: Map<String, Long>,
    val previous: Map<String, Long>,
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class StatsViewModel @Inject constructor(
    projectRepository: ProjectRepository,
    private val sessionRepository: SessionRepository,
    streakRepository: StreakRepository,
    clock: Clock,
    // Keeps the Period and the filter through tab switches and rotation, not a cold start.
    private val savedStateHandle: SavedStateHandle,
) : ViewModel() {
    private val period = savedStateHandle.getStateFlow(KeyPeriod, PeriodKind.Week)
    private val selectedId = savedStateHandle.getStateFlow<String?>(KeyProject, null)

    private val totals = combine(todayFlow(clock).distinctUntilChanged(), period) { today, period -> today to period }
        .flatMapLatest { (today, period) ->
            val soFar = period.soFar(today)
            combine(
                sessionRepository.observeTotalsBetween(soFar.current.start, soFar.current.endInclusive),
                sessionRepository.observeTotalsBetween(soFar.previous.start, soFar.previous.endInclusive),
            ) { current, previous -> PeriodTotals(today, period, current, previous) }
        }

    val uiState: StateFlow<StatsUiState?> = combine(
        totals,
        projectRepository.observeAll(),
        selectedId,
        streakRepository.observe(),
    ) { totals, all, selectedId, streak ->
        // A Project that's gone (after an Import) falls back to All Projects, for good.
        val selected = all.find { it.id == selectedId }
        if (selectedId != null && selected == null) savedStateHandle[KeyProject] = null
        fun sum(byProject: Map<String, Long>) =
            if (selected == null) byProject.values.sum() else byProject[selected.id] ?: 0L
        val previous = sum(totals.previous)
        val previousRange = totals.period.rangeContaining(totals.today).previous(totals.period)
        StatsUiState(
            period = totals.period,
            projects = sortForProjectsPage(all).let { it.active + it.archived },
            selected = selected,
            totalSeconds = sum(totals.current),
            comparison = if (previous > 0L) {
                Comparison(previous, periodLabel(totals.period, previousRange, totals.today))
            } else {
                null
            },
            streak = streak.takeIf { selected == null },
            byProject = if (selected == null) byProject(totals.current, all) else emptyList(),
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun selectPeriod(period: PeriodKind) {
        savedStateHandle[KeyPeriod] = period
    }

    /** Null selects All Projects. */
    fun selectProject(project: Project?) {
        savedStateHandle[KeyProject] = project?.id
    }

    private companion object {
        const val KeyPeriod = "period"
        const val KeyProject = "projectId"
    }
}

/** Projects with time, most first (then A–Z); archived ones included. */
private fun byProject(seconds: Map<String, Long>, projects: List<Project>): List<ByProjectRow> =
    projects.mapNotNull { project -> seconds[project.id]?.takeIf { it > 0L }?.let { ByProjectRow(project, it) } }
        .sortedWith(compareByDescending<ByProjectRow> { it.seconds }.thenBy(String.CASE_INSENSITIVE_ORDER) { it.project.name })
