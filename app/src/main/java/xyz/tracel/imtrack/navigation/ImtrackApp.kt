package xyz.tracel.imtrack.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.dropUnlessResumed
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import xyz.tracel.imtrack.projects.ProjectsScreen
import xyz.tracel.imtrack.settings.SettingsScreen

/**
 * Top-level navigation. The tabs are one destination that owns the top bar and bottom bar,
 * so Projects and Settings cover it whole and nothing resizes during a transition.
 */
@Composable
fun ImtrackApp() {
    val navController = rememberNavController()
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
                onOpenProjects = { navController.navigate(ProjectsRoute) },
                onOpenSettings = { navController.navigate(SettingsRoute) },
            )
        }
        // dropUnlessResumed: a second tap during the exit transition must not pop the tabs too.
        composable<ProjectsRoute> { ProjectsScreen(onBack = dropUnlessResumed { navController.popBackStack() }) }
        composable<SettingsRoute> { SettingsScreen(onBack = dropUnlessResumed { navController.popBackStack() }) }
    }
}
