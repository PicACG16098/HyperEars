package dev.hyperears.ui.navigation

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import dev.hyperears.R
import dev.hyperears.root.RootAction
import dev.hyperears.root.RootActionState
import dev.hyperears.settings.ModuleSettings
import dev.hyperears.ui.about.AboutScreen
import dev.hyperears.ui.dashboard.DashboardScreen
import dev.hyperears.ui.dashboard.DashboardUiState
import dev.hyperears.ui.settings.SettingsScreen
import kotlinx.coroutines.launch

private data class AppPage(
    val id: String,
    val label: String,
    val iconRes: Int,
)

private val appPages = listOf(
    AppPage("dashboard", "主页", R.drawable.ic_dashboard),
    AppPage("settings", "设置", R.drawable.ic_settings),
    AppPage("about", "关于", R.drawable.ic_info_outline),
)

private const val TOP_LEVEL_PAGE_PRELOAD_COUNT = 1

/**
 * The app shell owns only bottom navigation. Each page owns its app bar, insets and nested-scroll
 * state so pager transitions cannot transfer a collapsed title-bar offset between pages.
 */
@Composable
fun HyperEarsApp(
    uiState: DashboardUiState,
    onRefresh: () -> Unit,
    settings: ModuleSettings,
    rootAvailable: Boolean?,
    rootActionState: RootActionState,
    onSettingsChanged: (ModuleSettings) -> Unit,
    onRunRootAction: (RootAction) -> Unit,
    onExportLogs: () -> Unit,
) {
    val pagerState = rememberPagerState(pageCount = { appPages.size })
    val coroutineScope = rememberCoroutineScope()
    val selectedPage = pagerState.settledPage
    Scaffold(
        // Child pages own the status-bar inset through their own top app bars. The shell only
        // contributes the bottom navigation inset, so the top inset is not applied twice.
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = {
            NavigationBar {
                appPages.forEachIndexed { index, page ->
                    NavigationBarItem(
                        selected = selectedPage == index,
                        onClick = {
                            if (index != pagerState.settledPage) {
                                coroutineScope.launch { pagerState.animateScrollToPage(index) }
                            }
                        },
                        icon = {
                            Icon(
                                painter = painterResource(page.iconRes),
                                contentDescription = page.label,
                            )
                        },
                        label = { Text(page.label) },
                    )
                }
            }
        },
    ) { scaffoldPadding ->
        HorizontalPager(
            state = pagerState,
            key = { appPages[it].id },
            // Keep the adjacent destination composed while the pager is settled. The page itself
            // remains lazy, so this avoids first-entry shell work without inflating startup cost.
            beyondViewportPageCount = TOP_LEVEL_PAGE_PRELOAD_COUNT,
            modifier = Modifier
                .fillMaxSize()
                .padding(scaffoldPadding),
        ) { page ->
            when (page) {
                0 -> DashboardScreen(
                    uiState = uiState,
                    onRefresh = onRefresh,
                )

                1 -> SettingsScreen(
                    settings = settings,
                    rootAvailable = rootAvailable,
                    rootActionState = rootActionState,
                    onSettingsChanged = onSettingsChanged,
                    onRunRootAction = onRunRootAction,
                    onExportLogs = onExportLogs,
                )

                2 -> AboutScreen()
            }
        }
    }
}
