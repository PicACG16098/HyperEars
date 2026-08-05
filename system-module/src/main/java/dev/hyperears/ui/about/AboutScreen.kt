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
    val scope: SupportScope,
    val evidence: EvidenceLevel,
    val matchAndConfirmation: String,
    val privateTransport: String,
    val battery: String,
    val noiseControl: String,
)

private data class SupportBrand(
    val name: String,
    val entries: List<SupportEntry>,
)

private enum class EvidenceLevel(val label: String) {
    VERIFIED("实机验证"),
    PUBLIC_IMPLEMENTATION("公开实现"),
    REFERENCE_PROTOCOL("参考协议"),
    FAMILY_EXTRAPOLATION("家族外推"),
    STANDARD_FALLBACK("标准回退"),
}

private enum class SupportScope(val label: String) {
    MODEL("具体型号"),
    PRODUCT_LINE("产品线"),
    VENDOR_FAMILY("品牌家族"),
    STANDARD_FALLBACK("标准回退"),
}

private data class ProjectLink(
    val title: String,
    val detail: String,
    val url: String,
)

private val supportBrands = listOf(
    SupportBrand(
        name = "vivo / iQOO",
        entries = listOf(
            SupportEntry(
                name = "vivo TWS Air3 Pro",
                scope = SupportScope.MODEL,
                evidence = EvidenceLevel.VERIFIED,
                matchAndConfirmation = "规范化名称精确匹配；合法 GAIA 响应确认协议",
                privateTransport = "GAIA RFCOMM UUID 0837",
                battery = "私有组件（协议确认后）",
                noiseControl = "降噪、关闭、通透（协议确认后）",
            ),
            SupportEntry(
                name = "vivo TWS 3e",
                scope = SupportScope.MODEL,
                evidence = EvidenceLevel.PUBLIC_IMPLEMENTATION,
                matchAndConfirmation = "规范化名称精确匹配；合法 GAIA 响应确认协议",
                privateTransport = "GAIA RFCOMM UUID 0837；RFCOMM 通道 13 回退",
                battery = "私有组件（协议确认后）",
                noiseControl = "降噪、关闭、通透（协议确认后）",
            ),
            SupportEntry(
                name = "其他 vivo / iQOO TWS 家族型号",
                scope = SupportScope.VENDOR_FAMILY,
                evidence = EvidenceLevel.FAMILY_EXTRAPOLATION,
                matchAndConfirmation = "规范化家族名称选择候选；合法 GAIA 响应确认协议",
                privateTransport = "GAIA RFCOMM UUID 0837",
                battery = "私有组件（合法电量响应后）",
                noiseControl = "降噪、关闭、通透（合法握手或模式响应后）",
            ),
        ),
    ),
    SupportBrand(
        name = "OPPO",
        entries = listOf(
            SupportEntry(
                name = "OPPO Enco Air2 Pro",
                scope = SupportScope.MODEL,
                evidence = EvidenceLevel.REFERENCE_PROTOCOL,
                matchAndConfirmation = "规范化名称包含型号标记；合法 OPPO 响应确认协议；使用 Air2 Pro 编码映射",
                privateTransport = "RFCOMM UUID 079a",
                battery = "私有组件（协议确认后）",
                noiseControl = "降噪、关闭、通透（协议确认后）",
            ),
            SupportEntry(
                name = "OPPO Enco Free4 / Enco X3 / Enco Air5",
                scope = SupportScope.MODEL,
                evidence = EvidenceLevel.REFERENCE_PROTOCOL,
                matchAndConfirmation = "规范化名称包含对应型号标记；合法 OPPO 响应确认协议",
                privateTransport = "RFCOMM UUID 079a",
                battery = "私有组件（协议确认后）",
                noiseControl = "降噪、关闭、通透（协议确认后）",
            ),
            SupportEntry(
                name = "其他 OPPO / Enco 耳机",
                scope = SupportScope.VENDOR_FAMILY,
                evidence = EvidenceLevel.REFERENCE_PROTOCOL,
                matchAndConfirmation = "品牌名称与标准耳机身份匹配；合法 OPPO 响应确认协议",
                privateTransport = "RFCOMM UUID 079a",
                battery = "私有组件（合法电量响应后）",
                noiseControl = "降噪、关闭、通透（合法通知或模式响应后）",
            ),
        ),
    ),
    SupportBrand(
        name = "StarRing / 籁特易耳",
        entries = listOf(
            SupportEntry(
                name = "StarRing Ultra",
                scope = SupportScope.MODEL,
                evidence = EvidenceLevel.VERIFIED,
                matchAndConfirmation = "规范化名称精确匹配；传输连接成功",
                privateTransport = "GATT 7777/8888 特征优先；厂商 RFCOMM 回退",
                battery = "私有组件",
                noiseControl = "降噪、关闭、通透、抗风噪",
            ),
            SupportEntry(
                name = "其他 StarRing / 籁特易耳耳机",
                scope = SupportScope.STANDARD_FALLBACK,
                evidence = EvidenceLevel.STANDARD_FALLBACK,
                matchAndConfirmation = "品牌名称与标准耳机身份匹配",
                privateTransport = "Android 标准蓝牙能力",
                battery = "系统整机",
                noiseControl = "无",
            ),
        ),
    ),
    SupportBrand(
        name = "Bose",
        entries = listOf(
            SupportEntry(
                name = "Bose QuietComfort Headphones",
                scope = SupportScope.MODEL,
                evidence = EvidenceLevel.VERIFIED,
                matchAndConfirmation = "BMAP 产品身份 prince / 0x4075 匹配",
                privateTransport = "BMAP RFCOMM；产品身份响应确认就绪",
                battery = "私有整机",
                noiseControl = "降噪、通透、抗风噪",
            ),
            SupportEntry(
                name = "Bose QuietComfort 35 / 35 II",
                scope = SupportScope.MODEL,
                evidence = EvidenceLevel.PUBLIC_IMPLEMENTATION,
                matchAndConfirmation = "BMAP 产品 ID 0x400C / 0x4020 匹配",
                privateTransport = "BMAP RFCOMM；产品身份响应确认就绪",
                battery = "私有整机",
                noiseControl = "降噪、关闭、抗风噪",
            ),
            SupportEntry(
                name = "Bose Noise Cancelling Headphones 700",
                scope = SupportScope.MODEL,
                evidence = EvidenceLevel.PUBLIC_IMPLEMENTATION,
                matchAndConfirmation = "BMAP 产品身份 goodyear / 0x4024 匹配",
                privateTransport = "BMAP RFCOMM；产品身份响应确认就绪",
                battery = "私有整机",
                noiseControl = "降噪、关闭、通透",
            ),
            SupportEntry(
                name = "Bose QuietComfort 45 / QuietComfort Earbuds",
                scope = SupportScope.MODEL,
                evidence = EvidenceLevel.PUBLIC_IMPLEMENTATION,
                matchAndConfirmation = "BMAP 产品 ID 0x4039 / 0x402F 匹配",
                privateTransport = "BMAP RFCOMM；产品身份响应确认就绪",
                battery = "私有整机或组件（按设备形态）",
                noiseControl = "降噪、通透",
            ),
            SupportEntry(
                name = "QuietComfort Earbuds II / Ultra 系列与第二代",
                scope = SupportScope.MODEL,
                evidence = EvidenceLevel.REFERENCE_PROTOCOL,
                matchAndConfirmation = "已登记 BMAP 产品 ID 匹配",
                privateTransport = "BMAP RFCOMM；产品身份响应确认就绪",
                battery = "私有整机或组件（按设备形态）",
                noiseControl = "降噪、通透",
            ),
            SupportEntry(
                name = "其他 Bose BMAP 耳机",
                scope = SupportScope.VENDOR_FAMILY,
                evidence = EvidenceLevel.FAMILY_EXTRAPOLATION,
                matchAndConfirmation = "BMAP 产品身份与只读状态响应确认能力",
                privateTransport = "BMAP RFCOMM 候选逐一验证",
                battery = "按 BMAP 响应显示私有整机或组件",
                noiseControl = "AudioModes：降噪、通透；ANR：降噪、关闭、抗风噪；CNC：降噪、关闭、通透",
            ),
        ),
    ),
    SupportBrand(
        name = "Edifier / 漫步者",
        entries = listOf(
            SupportEntry(
                name = "Edifier W860NB PRO",
                scope = SupportScope.MODEL,
                evidence = EvidenceLevel.VERIFIED,
                matchAndConfirmation = "规范化名称精确匹配；合法 BES 响应确认协议",
                privateTransport = "Edifier BES RFCOMM；通道 1 回退",
                battery = "私有整机（协议确认后）",
                noiseControl = "降噪、关闭、通透、抗风噪（协议确认后）",
            ),
            SupportEntry(
                name = "Edifier 花再 Evo Pro",
                scope = SupportScope.MODEL,
                evidence = EvidenceLevel.VERIFIED,
                matchAndConfirmation = "规范化名称包含 Evo Pro；合法 BES 响应确认协议",
                privateTransport = "Edifier BES RFCOMM；通道 1 回退",
                battery = "私有聚合（左右同值，盒未知）",
                noiseControl = "降噪、关闭、通透、抗风噪",
            ),
            SupportEntry(
                name = "其他 Edifier W820 / W830 / W860 系列头戴式耳机",
                scope = SupportScope.PRODUCT_LINE,
                evidence = EvidenceLevel.FAMILY_EXTRAPOLATION,
                matchAndConfirmation = "系列名称与头戴式设备形态匹配；合法 BES 响应确认协议",
                privateTransport = "Edifier BES RFCOMM；通道 1 回退",
                battery = "头戴整机或 TWS 聚合（合法电量响应后）",
                noiseControl = "降噪、关闭、通透、抗风噪（合法模式响应后）",
            ),
            SupportEntry(
                name = "其他名称可识别的 Edifier 耳机",
                scope = SupportScope.VENDOR_FAMILY,
                evidence = EvidenceLevel.FAMILY_EXTRAPOLATION,
                matchAndConfirmation = "品牌名称与标准耳机身份匹配；合法 BES 响应确认协议",
                privateTransport = "Edifier BES RFCOMM；通道 1 回退",
                battery = "私有整机（合法电量响应后）",
                noiseControl = "降噪、关闭、通透、抗风噪（合法模式响应后）",
            ),
        ),
    ),
    SupportBrand(
        name = "ROSESELSA / 弱水时砂",
        entries = listOf(
            SupportEntry(
                name = "ROSESELSA EARFREE i5",
                scope = SupportScope.MODEL,
                evidence = EvidenceLevel.PUBLIC_IMPLEMENTATION,
                matchAndConfirmation = "规范化名称精确匹配；传输连接成功",
                privateTransport = "GATT 服务 011bf5da；7777/8888 特征",
                battery = "私有组件",
                noiseControl = "降噪、关闭、通透、抗风噪",
            ),
            SupportEntry(
                name = "ROSE BudsFeel MK2",
                scope = SupportScope.MODEL,
                evidence = EvidenceLevel.PUBLIC_IMPLEMENTATION,
                matchAndConfirmation = "规范化名称精确匹配；传输连接成功",
                privateTransport = "RFCOMM UUID 0cf12d31-…",
                battery = "私有组件",
                noiseControl = "降噪、关闭、通透、抗风噪",
            ),
            SupportEntry(
                name = "EARFREE / EARFEEL 与 BudsFeel 产品线",
                scope = SupportScope.PRODUCT_LINE,
                evidence = EvidenceLevel.FAMILY_EXTRAPOLATION,
                matchAndConfirmation = "产品线名称或对应服务选择候选；合法状态帧确认协议",
                privateTransport = "对应 GATT 或 RFCOMM 私有通道",
                battery = "系统整机；合法电量响应后切换为私有组件",
                noiseControl = "降噪、关闭、通透、抗风噪（合法状态响应后）",
            ),
            SupportEntry(
                name = "其他 ROSESELSA / ROSE 耳机",
                scope = SupportScope.STANDARD_FALLBACK,
                evidence = EvidenceLevel.STANDARD_FALLBACK,
                matchAndConfirmation = "品牌名称与标准耳机身份匹配",
                privateTransport = "Android 标准蓝牙能力",
                battery = "系统整机",
                noiseControl = "无",
            ),
        ),
    ),
    SupportBrand(
        name = "NiceHCK / YuanDao",
        entries = listOf(
            SupportEntry(
                name = "NiceHCK YuanDao OriG in",
                scope = SupportScope.MODEL,
                evidence = EvidenceLevel.PUBLIC_IMPLEMENTATION,
                matchAndConfirmation = "规范化名称精确匹配；合法状态帧确认协议",
                privateTransport = "RFCOMM UUID a100",
                battery = "私有组件（协议确认后）",
                noiseControl = "降噪、关闭、通透、抗风噪（协议确认后）",
            ),
            SupportEntry(
                name = "其他 NiceHCK / YuanDao 耳机",
                scope = SupportScope.STANDARD_FALLBACK,
                evidence = EvidenceLevel.STANDARD_FALLBACK,
                matchAndConfirmation = "品牌名称与标准耳机身份匹配",
                privateTransport = "Android 标准蓝牙能力",
                battery = "系统整机",
                noiseControl = "无",
            ),
        ),
    ),
    SupportBrand(
        name = "Sony",
        entries = listOf(
            SupportEntry(
                name = "WH-1000XM2/XM3/XM4、WF-1000XM3/XM4、WI-SP600N",
                scope = SupportScope.MODEL,
                evidence = EvidenceLevel.PUBLIC_IMPLEMENTATION,
                matchAndConfirmation = "规范化型号匹配；合法 v1/v2 初始化响应确认协议",
                privateTransport = "Sony RFCOMM v1/v2",
                battery = "按设备形态显示私有整机或组件",
                noiseControl = "降噪、关闭、通透、抗风噪",
            ),
            SupportEntry(
                name = "WH-1000XM5/XM6、CH720N、ULT WEAR、WF-1000XM5、SP800N、C700N/C710N、LinkBuds S",
                scope = SupportScope.MODEL,
                evidence = EvidenceLevel.PUBLIC_IMPLEMENTATION,
                matchAndConfirmation = "规范化型号匹配；合法 v1/v2 初始化响应确认协议",
                privateTransport = "Sony RFCOMM v1/v2",
                battery = "按设备形态显示私有整机或组件",
                noiseControl = "降噪、关闭、通透",
            ),
            SupportEntry(
                name = "Sony WF-C510",
                scope = SupportScope.MODEL,
                evidence = EvidenceLevel.PUBLIC_IMPLEMENTATION,
                matchAndConfirmation = "规范化型号匹配；合法 v1/v2 初始化响应确认协议",
                privateTransport = "Sony RFCOMM v1/v2",
                battery = "私有组件",
                noiseControl = "关闭、通透",
            ),
            SupportEntry(
                name = "Sony WF-C500 / LinkBuds / WI-C100",
                scope = SupportScope.MODEL,
                evidence = EvidenceLevel.PUBLIC_IMPLEMENTATION,
                matchAndConfirmation = "规范化型号匹配；合法 v1/v2 初始化响应确认协议",
                privateTransport = "Sony RFCOMM v1/v2",
                battery = "按设备形态显示私有整机或组件",
                noiseControl = "无",
            ),
            SupportEntry(
                name = "其他 Sony WH / WI / MDR、WF / LinkBuds 产品线",
                scope = SupportScope.PRODUCT_LINE,
                evidence = EvidenceLevel.FAMILY_EXTRAPOLATION,
                matchAndConfirmation = "产品线名称或 Sony 服务选择候选；合法初始化响应确认协议",
                privateTransport = "Sony RFCOMM v1/v2",
                battery = "按设备形态显示私有整机或组件（协议确认后）",
                noiseControl = "降噪、关闭、通透（降噪产品线且协议确认后）",
            ),
            SupportEntry(
                name = "其他 Sony 标准耳机",
                scope = SupportScope.STANDARD_FALLBACK,
                evidence = EvidenceLevel.STANDARD_FALLBACK,
                matchAndConfirmation = "品牌名称与标准耳机身份匹配",
                privateTransport = "Android 标准蓝牙能力",
                battery = "系统整机",
                noiseControl = "无",
            ),
        ),
    ),
    SupportBrand(
        name = "通用蓝牙耳机",
        entries = listOf(
            SupportEntry(
                name = "其他标准 A2DP / HFP 耳机",
                scope = SupportScope.STANDARD_FALLBACK,
                evidence = EvidenceLevel.STANDARD_FALLBACK,
                matchAndConfirmation = "Android 标准耳机身份；HyperOS 原生型号由系统处理",
                privateTransport = "Android 标准蓝牙能力",
                battery = "系统整机",
                noiseControl = "无",
            ),
        ),
    ),
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
        title = "兼容性文档",
        detail = "查看完整型号、证据、传输和能力矩阵",
        url = "https://github.com/silverpoetry/HyperEars/blob/main/docs/compatibility.md",
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

                    AboutSection(title = "兼容性说明") {
                        EvidenceLegend()
                    }

                    AboutSection(title = "设备支持") {
                        supportBrands.forEachIndexed { index, brand ->
                            BrandSupportTable(brand)
                            if (index != supportBrands.lastIndex) {
                                HorizontalDivider(
                                    color = MaterialTheme.colorScheme.outlineVariant,
                                )
                            }
                        }
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
                        text = "© 2026 HyperEars contributors\nHyperEars 与 Xiaomi、vivo、iQOO、OPPO、Bose、Edifier、ROSESELSA、NiceHCK、Sony 及相关品牌无关。产品名称仅用于兼容性说明。",
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
private fun BrandSupportTable(brand: SupportBrand) {
    Column(
        modifier = Modifier.padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = brand.name,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.primary,
        )
        brand.entries.forEachIndexed { index, entry ->
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = entry.name,
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium,
                    )
                    EvidenceBadge(entry.evidence)
                }
                SupportDetail(label = "适配层级", value = entry.scope.label)
                SupportDetail(label = "判型与确认", value = entry.matchAndConfirmation)
                SupportDetail(label = "私有传输", value = entry.privateTransport)
                SupportDetail(label = "电量", value = entry.battery)
                SupportDetail(label = "噪声模式", value = entry.noiseControl)
            }
            if (index != brand.entries.lastIndex) {
                HorizontalDivider(
                    modifier = Modifier.padding(vertical = 4.dp),
                    color = MaterialTheme.colorScheme.outlineVariant,
                )
            }
        }
    }
}

@Composable
private fun EvidenceLegend() {
    Column(
        modifier = Modifier.padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = "所有条目均提供设备流转和系统音量。下表分别列出额外的电量与噪声控制能力；需要协议确认的能力只在合法响应后开放。",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        EvidenceLevel.entries.forEach { level ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.Top,
            ) {
                EvidenceBadge(level)
                Text(
                    text = level.description,
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Text(
            text = "证据等级描述适配依据和验证范围。设备流转由 HyperOS 与 MiLink 系统链路负责。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun EvidenceBadge(level: EvidenceLevel) {
    val color = when (level) {
        EvidenceLevel.VERIFIED -> MaterialTheme.colorScheme.primary
        EvidenceLevel.PUBLIC_IMPLEMENTATION -> MaterialTheme.colorScheme.secondary
        EvidenceLevel.REFERENCE_PROTOCOL -> MaterialTheme.colorScheme.tertiary
        EvidenceLevel.FAMILY_EXTRAPOLATION -> MaterialTheme.colorScheme.tertiary
        EvidenceLevel.STANDARD_FALLBACK -> MaterialTheme.colorScheme.outline
    }
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = color.copy(alpha = 0.12f),
        contentColor = color,
    ) {
        Text(
            text = level.label,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun SupportDetail(label: String, value: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
        )
        Text(
            text = value,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private val EvidenceLevel.description: String
    get() = when (this) {
        EvidenceLevel.VERIFIED -> "已在真实设备上验证判型、私有连接、状态读取、控制写入和卡片回读。"
        EvidenceLevel.PUBLIC_IMPLEMENTATION -> "可检查的公开实现提供具体型号的传输、帧格式和字段语义。"
        EvidenceLevel.REFERENCE_PROTOCOL -> "同品牌或同协议家族的公开资料提供传输与命令语义。"
        EvidenceLevel.FAMILY_EXTRAPOLATION -> "名称或服务选择候选适配，合法身份或状态响应完成协议确认。"
        EvidenceLevel.STANDARD_FALLBACK -> "提供设备流转、系统音量和 Android 整机电量。"
    }
