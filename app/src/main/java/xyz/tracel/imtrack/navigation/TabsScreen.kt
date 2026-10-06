package xyz.tracel.imtrack.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import xyz.tracel.imtrack.R
import xyz.tracel.imtrack.home.HomeScreen

/**
 * Home, History and Stats with the top app bar (title and ⋮ menu) and the bottom bar.
 * The tab back stack is saved while Projects or Settings is open, so back returns to the same tab.
 */
@Composable
fun TabsScreen(onOpenProjects: () -> Unit, onOpenSettings: () -> Unit) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentTab = Tab.entries.firstOrNull { tab ->
        backStackEntry?.destination?.hasRoute(tab.route::class) == true
    } ?: Tab.Home

    Scaffold(
        topBar = {
            TabTopBar(
                title = stringResource(currentTab.title),
                onOpenProjects = onOpenProjects,
                onOpenSettings = onOpenSettings,
            )
        },
        bottomBar = {
            NavigationBar {
                Tab.entries.forEach { tab ->
                    NavigationBarItem(
                        selected = tab == currentTab,
                        onClick = {
                            navController.navigate(tab.route) {
                                popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = { Icon(painterResource(tab.icon), contentDescription = null) },
                        label = { Text(stringResource(tab.label)) },
                    )
                }
            }
        },
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = HomeRoute,
            enterTransition = tabEnter,
            exitTransition = tabExit,
            predictivePopEnterTransition = tabPredictivePopEnter,
            predictivePopExitTransition = tabPredictivePopExit,
            modifier = Modifier
                .padding(innerPadding)
                .consumeWindowInsets(innerPadding),
        ) {
            composable<HomeRoute> { HomeScreen() }
            composable<HistoryRoute> { ComingSoon() }
            composable<StatsRoute> { ComingSoon() }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TabTopBar(title: String, onOpenProjects: () -> Unit, onOpenSettings: () -> Unit) {
    var menuOpen by remember { mutableStateOf(false) }
    TopAppBar(
        title = { Text(title) },
        actions = {
            Box {
                IconButton(onClick = { menuOpen = true }) {
                    Icon(painterResource(R.drawable.ic_more_vert), stringResource(R.string.more_options))
                }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    // Wired by the Undo last Session slice.
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.menu_undo_last_session)) },
                        onClick = {},
                        enabled = false,
                    )
                    HorizontalDivider()
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.menu_projects)) },
                        onClick = {
                            menuOpen = false
                            onOpenProjects()
                        },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.menu_settings)) },
                        onClick = {
                            menuOpen = false
                            onOpenSettings()
                        },
                    )
                }
            }
        },
    )
}

/** Stand-in for History and Stats until their slices land. */
@Composable
private fun ComingSoon() {
    Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
        Text(
            stringResource(R.string.coming_soon),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
