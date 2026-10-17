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
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
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

/** The Heatmap: its grid and each day's total seconds, for [projectId] (null: All Projects). */
data class HeatmapDays(val layout: HeatmapLayout, val seconds: Map<LocalDate, Long>, val projectId: String? = null)

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
    val heatmap: HeatmapDays,
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

    private val today = todayFlow(clock).distinctUntilChanged()

    private val totals = combine(today, period) { today, period -> today to period }
        .flatMapLatest { (today, period) ->
            val soFar = period.soFar(today)
            combine(
                sessionRepository.observeTotalsBetween(soFar.current.start, soFar.current.endInclusive),
                sessionRepository.observeTotalsBetween(soFar.previous.start, soFar.previous.endInclusive),
            ) { current, previous -> PeriodTotals(today, period, current, previous) }
        }

    private val heatmap = combine(today, selectedId) { today, selectedId -> today to selectedId }
        .flatMapLatest { (today, selectedId) ->
            val layout = heatmapLayout(today)
            sessionRepository.observeDayTotalsBetween(layout.range.start, layout.range.endInclusive, selectedId)
                .map { HeatmapDays(layout, it, selectedId) }
        }

    val uiState: StateFlow<StatsUiState?> = combine(
        totals,
        projectRepository.observeAll(),
        selectedId,
        streakRepository.observe(),
        heatmap,
    ) { totals, all, selectedId, streak, heatmap ->
        // Just after the filter changes, the heatmap still has the old Project's days: wait for the new ones.
        if (heatmap.projectId != selectedId) return@combine null
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
            heatmap = heatmap,
        )
    }.filterNotNull().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

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
