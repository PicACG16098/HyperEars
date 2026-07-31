package dev.hyperears.ui.about

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalUriHandler
import dev.hyperears.BuildConfig
import dev.hyperears.R

private data class SupportEntry(
    val name: String,
    val detail: String,
)

private data class ProjectLink(
    val title: String,
    val detail: String,
    val url: String,
)

private val verifiedDevices = listOf(
    SupportEntry("vivo TWS Air3 Pro", "左右耳及充电盒电量、三态降噪、设备流转"),
    SupportEntry("StarRing Ultra", "左右耳电量、四态噪声控制、设备流转"),
    SupportEntry("Bose QuietComfort Headphones", "整机电量、BMAP 模式切换、设备流转"),
)

private val familyDevices = listOf(
    SupportEntry("vivo TWS 3e", "基于公开协议实现；尚未完成本地实机验证"),
    SupportEntry(
        "vivo / iQOO TWS 家族",
        "Air2/Air200、5e、3 Pro、3、2e、2、1、A1 Pro、A1、Air Pro、Air、Neo、X1；iQOO TWS Air Pro、Air、1",
    ),
    SupportEntry("OPPO Enco", "Air2 Pro、Free4、X3、Air5 及其他 Enco 家族设备"),
    SupportEntry("Bose BMAP", "具体产品确认后启用对应能力"),
)

private val projectLinks = listOf(
    ProjectLink(
        title = "源代码",
        detail = "github.com/silverpoetry/HyperEars",
        url = "https://github.com/silverpoetry/HyperEars",
    ),
    ProjectLink(
        title = "问题反馈",
        detail = "提交兼容性问题或功能建议",
        url = "https://github.com/silverpoetry/HyperEars/issues/new/choose",
    ),
    ProjectLink(
        title = "版本发布",
        detail = "查看正式版本和更新记录",
        url = "https://github.com/silverpoetry/HyperEars/releases",
    ),
    ProjectLink(
        title = "开源许可",
        detail = "GNU GPL-3.0-only",
        url = "https://github.com/silverpoetry/HyperEars/blob/main/LICENSE",
    ),
    ProjectLink(
        title = "第三方声明",
        detail = "协议研究来源与许可信息",
        url = "https://github.com/silverpoetry/HyperEars/blob/main/THIRD_PARTY_NOTICES.md",
    ),
    ProjectLink(
        title = "隐私说明",
        detail = "本地数据处理和权限边界",
        url = "https://github.com/silverpoetry/HyperEars/blob/main/PRIVACY.md",
    ),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutScreen(onBack: () -> Unit) {
    val uriHandler = LocalUriHandler.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("关于") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            painter = painterResource(R.drawable.ic_arrow_back),
                            contentDescription = "返回",
                        )
                    }
                },
            )
        },
        containerColor = MaterialTheme.colorScheme.surface,
    ) { scaffoldPadding ->
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .padding(scaffoldPadding),
        ) {
            val horizontalPadding = if (maxWidth >= 720.dp) 32.dp else 16.dp
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(
                        PaddingValues(
                            start = horizontalPadding,
                            end = horizontalPadding,
                            top = 12.dp,
                            bottom = 32.dp,
                        ),
                    ),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .widthIn(max = 800.dp),
                    verticalArrangement = Arrangement.spacedBy(20.dp),
                ) {
                    ProjectHeader()

                    AboutSection(title = "设备支持") {
                        SupportGroup(
                            title = "实机验证",
                            entries = verifiedDevices,
                        )
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        SupportGroup(
                            title = "协议与家族兼容",
                            entries = familyDevices,
                        )
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        SupportGroup(
                            title = "通用蓝牙耳机",
                            entries = listOf(
                                SupportEntry(
                                    "标准 A2DP / HFP 耳机",
                                    "设备流转、音量和 Android 系统整机电量；不提供私有控制",
                                ),
                            ),
                        )
                    }

                    AboutSection(title = "项目") {
                        projectLinks.forEachIndexed { index, link ->
                            ListItem(
                                headlineContent = { Text(link.title) },
                                supportingContent = { Text(link.detail) },
                                trailingContent = {
                                    Text(
                                        text = "↗",
                                        style = MaterialTheme.typography.titleMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                },
                                colors = ListItemDefaults.colors(
                                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                                ),
                                modifier = Modifier.clickable {
                                    runCatching { uriHandler.openUri(link.url) }
                                },
                            )
                            if (index != projectLinks.lastIndex) {
                                HorizontalDivider(
                                    modifier = Modifier.padding(horizontal = 16.dp),
                                    color = MaterialTheme.colorScheme.outlineVariant,
                                )
                            }
                        }
                    }

                    Text(
                        text = "© 2026 HyperEars contributors\nHyperEars 与 Xiaomi、vivo、iQOO、OPPO、Bose 及相关品牌无关。产品名称仅用于兼容性说明。",
                        modifier = Modifier.padding(horizontal = 4.dp),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun ProjectHeader() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = "HyperEars",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = "版本 ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = "面向 Xiaomi HyperOS 和 MiLink 的第三方蓝牙耳机系统集成模块。",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun AboutSection(
    title: String,
    content: @Composable () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            text = title,
            modifier = Modifier.padding(horizontal = 4.dp),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
        )
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surfaceContainerLow,
        ) {
            Column { content() }
        }
    }
}

@Composable
private fun SupportGroup(
    title: String,
    entries: List<SupportEntry>,
) {
    Column(
        modifier = Modifier.padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
        )
        entries.forEach { entry ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.Top,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = entry.name,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium,
                    )
                    Text(
                        text = entry.detail,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}
