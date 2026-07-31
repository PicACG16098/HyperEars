package dev.hyperears.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import dev.hyperears.ui.about.AboutScreen
import dev.hyperears.ui.dashboard.DashboardScreen
import dev.hyperears.ui.dashboard.DashboardUiState

private object AppDestination {
    const val Dashboard = "dashboard"
    const val About = "about"
}

/**
 * Application navigation root.
 *
 * Navigation is kept above individual screens so each destination owns only its content and
 * screen-level callbacks. The controller also preserves the dashboard while the About page is on
 * the back stack.
 */
@Composable
fun HyperEarsApp(
    uiState: DashboardUiState,
    onRefresh: () -> Unit,
) {
    val navController = rememberNavController()

    NavHost(
        navController = navController,
        startDestination = AppDestination.Dashboard,
    ) {
        composable(AppDestination.Dashboard) {
            DashboardScreen(
                uiState = uiState,
                onRefresh = onRefresh,
                onOpenAbout = {
                    navController.navigate(AppDestination.About) {
                        launchSingleTop = true
                    }
                },
            )
        }
        composable(AppDestination.About) {
            AboutScreen(onBack = { navController.navigateUp() })
        }
    }
}
