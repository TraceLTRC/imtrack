package xyz.tracel.imtrack.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.dropUnlessResumed
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import xyz.tracel.imtrack.projects.ProjectsScreen
import xyz.tracel.imtrack.settings.SettingsScreen
import xyz.tracel.imtrack.stats.PastPeriodsScreen

/**
 * Top-level navigation. The tabs are one destination that owns the top bar and bottom bar,
 * so Projects and Settings cover it whole and nothing resizes during a transition.
 */
@Composable
fun ImtrackApp(openHomeRequests: Flow<Unit> = emptyFlow()) {
    val navController = rememberNavController()
    // Counts requests to show Home; saved, so a rotation doesn't look like a new one.
    var homeRequest by rememberSaveable { mutableIntStateOf(0) }
    LaunchedEffect(openHomeRequests) {
        openHomeRequests.collect {
            navController.popBackStack<TabsRoute>(inclusive = false)
            homeRequest++
        }
    }
    NavHost(
        navController = navController,
        startDestination = TabsRoute,
        enterTransition = pageEnter,
        exitTransition = pageExit,
        popEnterTransition = pagePopEnter,
        popExitTransition = pagePopExit,
        predictivePopEnterTransition = pagePredictivePopEnter,
        predictivePopExitTransition = pagePredictivePopExit,
        // Without this, the white window background shows at the edges while pages slide.
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        composable<TabsRoute> {
            TabsScreen(
                homeRequest = homeRequest,
                onOpenPastPeriods = { navController.navigate(PastPeriodsRoute) },
                onOpenProjects = { navController.navigate(ProjectsRoute) },
                onOpenSettings = { navController.navigate(SettingsRoute) },
            )
        }
        // dropUnlessResumed: a second tap during the exit transition must not pop the tabs too.
        composable<PastPeriodsRoute> { PastPeriodsScreen(onBack = dropUnlessResumed { navController.popBackStack() }) }
        composable<ProjectsRoute> { ProjectsScreen(onBack = dropUnlessResumed { navController.popBackStack() }) }
        composable<SettingsRoute> { SettingsScreen(onBack = dropUnlessResumed { navController.popBackStack() }) }
    }
}
