package dev.hyperears.ui.components

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * Owns one page's app bar and scroll state.
 *
 * Each page calls this independently, so switching pages never reuses another page's collapsed
 * offset or title-bar layout. The caller receives the matching insets and nested-scroll behavior.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HyperEarsPage(
    title: String,
    modifier: Modifier = Modifier,
    content: @Composable (PaddingValues, TopAppBarScrollBehavior) -> Unit,
) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    Scaffold(
        modifier = modifier,
        // The app shell already reserves the bottom navigation area. Re-consuming navigation-bar
        // insets here adds a visible empty band between page content and the bottom navigation.
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            LargeTopAppBar(
                title = { Text(title) },
                scrollBehavior = scrollBehavior,
            )
        },
        content = { contentPadding ->
            content(contentPadding, scrollBehavior)
        },
    )
}
