package xyz.tracel.imtrack.navigation

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import kotlinx.serialization.Serializable
import xyz.tracel.imtrack.R

@Serializable data object TabsRoute
@Serializable data object HomeRoute
@Serializable data object HistoryRoute
@Serializable data object StatsRoute
@Serializable data object ProjectsRoute
@Serializable data object SettingsRoute

/** The bottom-bar tabs. Each has its own top app bar title. */
enum class Tab(
    val route: Any,
    @param:StringRes val label: Int,
    @param:StringRes val title: Int,
    @param:DrawableRes val icon: Int,
) {
    Home(HomeRoute, R.string.tab_home, R.string.app_name, R.drawable.ic_home),
    History(HistoryRoute, R.string.tab_history, R.string.tab_history, R.drawable.ic_history),
    Stats(StatsRoute, R.string.tab_stats, R.string.tab_stats, R.drawable.ic_bar_chart),
}
