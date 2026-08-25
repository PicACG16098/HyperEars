package dev.hyperears.ui.about

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.hyperears.BuildConfig
import dev.hyperears.ui.components.MiuixHyperEarsPage
import dev.hyperears.update.ReleaseInfo
import dev.hyperears.update.UpdateCheckResult
import dev.hyperears.update.UpdateCheckUiState
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.HorizontalDivider
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** Miuix renderer for the same project and compatibility data used by [AboutScreen]. */
@Composable
fun MiuixAboutScreen(
    updateCheckState: UpdateCheckUiState,
    onCheckUpdates: () -> Unit,
    onOpenRelease: (ReleaseInfo) -> Unit,
) {
    MiuixHyperEarsPage(title = "关于") { pagePadding, scrollBehavior ->
        val uriHandler = LocalUriHandler.current
        LazyColumn(
            state = rememberLazyListState(),
            modifier = Modifier
                .fillMaxSize()
                .padding(pagePadding)
                .nestedScroll(scrollBehavior.nestedScrollConnection),
            contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item(key = "introduction") {
                Column(
                    modifier = Modifier.padding(horizontal = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(
                        text = "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE}) · GPL-3.0-only",
                        style = MiuixTheme.textStyles.body2,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    )
                    Text(
                        text = "为第三方蓝牙耳机补充 HyperOS 与 MiLink 系统集成。",
                        style = MiuixTheme.textStyles.body2,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    )
                    Text(
                        text = "所有条目均支持设备流转和系统音量。下方列出电量与噪声控制；私有能力在协议确认后开放。",
                        style = MiuixTheme.textStyles.body2,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    )
                }
            }
            item(key = "update") {
                MiuixUpdateCard(
                    state = updateCheckState,
                    onCheck = onCheckUpdates,
                    onOpenRelease = onOpenRelease,
                )
            }
            items(items = supportBrands, key = SupportBrand::name) { brand ->
                MiuixBrandSupportCard(brand)
            }
            item(key = "project-links") {
                Card(modifier = Modifier.fillMaxWidth()) {
                    projectLinks.forEachIndexed { index, link ->
                        BasicComponent(
                            title = link.title,
                            summary = link.detail,
                            onClick = { runCatching { uriHandler.openUri(link.url) } },
                        )
                        if (index != projectLinks.lastIndex) MiuixAboutDivider()
                    }
                }
            }
            item(key = "copyright") {
                Text(
                    text = "© 2026 HyperEars contributors\n产品名称与商标归各自权利人所有。",
                    modifier = Modifier.padding(horizontal = 12.dp),
                    style = MiuixTheme.textStyles.footnote1,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                )
            }
        }
    }
}

@Composable
private fun MiuixUpdateCard(
    state: UpdateCheckUiState,
    onCheck: () -> Unit,
    onOpenRelease: (ReleaseInfo) -> Unit,
) {
    val available = state.result as? UpdateCheckResult.Available
    val detail = when (val result = state.result) {
        is UpdateCheckResult.Available -> "发现新版本 ${result.release.version}"
        UpdateCheckResult.UpToDate -> "当前已是最新版本"
        is UpdateCheckResult.Failed -> result.message
        null -> "从 GitHub Releases 获取最新版本"
    }
    Card(modifier = Modifier.fillMaxWidth()) {
        BasicComponent(
            title = "检查更新",
            summary = detail,
            endActions = {
                Button(
                    onClick = {
                        if (available == null) onCheck() else onOpenRelease(available.release)
                    },
                    enabled = !state.checking,
                ) {
                    Text(
                        when {
                            state.checking -> "检查中"
                            available != null -> "查看"
                            else -> "检查"
                        },
                    )
                }
            },
        )
    }
}

@Composable
private fun MiuixBrandSupportCard(brand: SupportBrand) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = brand.name,
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 8.dp),
            style = MiuixTheme.textStyles.headline2,
            fontWeight = FontWeight.SemiBold,
            color = MiuixTheme.colorScheme.primary,
        )
        brand.entries.forEachIndexed { index, entry ->
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.Top,
                ) {
                    Text(
                        text = entry.name,
                        modifier = Modifier.weight(1f),
                        style = MiuixTheme.textStyles.body1,
                        fontWeight = FontWeight.Medium,
                    )
                    Text(
                        text = entry.evidence.label,
                        style = MiuixTheme.textStyles.footnote1,
                        color = miuixEvidenceColor(entry.evidence),
                    )
                }
                Text(
                    text = "电量：${entry.battery.label} · 噪声：${entry.noiseControl}",
                    style = MiuixTheme.textStyles.body2,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                )
            }
            if (index != brand.entries.lastIndex) MiuixAboutDivider()
        }
    }
}

@Composable
private fun miuixEvidenceColor(evidence: EvidenceLevel) = when (evidence) {
    EvidenceLevel.VERIFIED -> MiuixTheme.colorScheme.primary
    EvidenceLevel.PUBLIC_IMPLEMENTATION -> MiuixTheme.colorScheme.secondary
    EvidenceLevel.REFERENCE_PROTOCOL,
    EvidenceLevel.FAMILY_PROBE,
    -> MiuixTheme.colorScheme.primaryVariant
    EvidenceLevel.STANDARD_FALLBACK -> MiuixTheme.colorScheme.onSurfaceVariantSummary
}

@Composable
private fun MiuixAboutDivider() {
    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
}
