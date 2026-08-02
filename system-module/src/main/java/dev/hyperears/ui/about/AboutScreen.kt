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
    val evidence: EvidenceLevel,
    val matchBasis: String,
    val capabilities: String,
    val note: String? = null,
)

private data class SupportBrand(
    val name: String,
    val entries: List<SupportEntry>,
)

private enum class EvidenceLevel(val label: String) {
    VERIFIED("实机验证"),
    PUBLIC_PROFILE("公开实现"),
    REFERENCE_PROFILE("参考协议"),
    FAMILY_EXTRAPOLATION("家族外推"),
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
            SupportEntry("vivo TWS Air3 Pro", EvidenceLevel.VERIFIED, "精确名称；GAIA RFCOMM 0837", "左/右/盒电量；ANC/OFF/通透；流转"),
            SupportEntry("vivo TWS 3e", EvidenceLevel.PUBLIC_PROFILE, "精确名称；GAIA RFCOMM 0837，channel 13 回退", "左/右/盒电量；ANC/OFF/通透；流转"),
            SupportEntry(
                "vivo / iQOO TWS 家族目录",
                EvidenceLevel.FAMILY_EXTRAPOLATION,
                "精确目录名称；GAIA RFCOMM 0837",
                "合法响应中的左/右/盒电量；ANC/OFF/通透；流转",
                "Air2/Air200、5e、3 Pro、3、2e、2、1、A1 Pro/A1、Air Pro/Air、Neo、X1；iQOO TWS Air Pro/Air/1。",
            ),
        ),
    ),
    SupportBrand(
        name = "OPPO",
        entries = listOf(
            SupportEntry("Enco Air2 Pro", EvidenceLevel.REFERENCE_PROFILE, "精确名称；OPPO RFCOMM 079a", "左/右/盒电量；反向 ANC/OFF 编码、通透；流转"),
            SupportEntry("Enco Free4 / X3 / Air5", EvidenceLevel.REFERENCE_PROFILE, "精确名称进入独立 Profile；RFCOMM 079a", "左/右/盒电量；当前沿用家族 ANC/OFF/通透；流转", "不宣称尚未实现的自适应降噪或空间音频。"),
            SupportEntry("其他 OPPO / Enco", EvidenceLevel.REFERENCE_PROFILE, "保守家族名称；RFCOMM 079a", "左/右/盒电量；ANC/OFF/通透；流转"),
        ),
    ),
    SupportBrand(
        name = "StarRing / 籁特易耳",
        entries = listOf(
            SupportEntry("StarRing Ultra", EvidenceLevel.VERIFIED, "精确名称；官方 GATT 7777/8888，RFCOMM 候选回退", "左/右电量；降噪/正常/通透/抗风噪；流转", "充电盒仅在协议实际上报时显示。"),
            SupportEntry("其他 StarRing / 籁特易耳", EvidenceLevel.STANDARD_FALLBACK, "保守家族名称和耳机类别", "Android 整机电量、音量和流转；无私有控制"),
        ),
    ),
    SupportBrand(
        name = "Bose",
        entries = listOf(
            SupportEntry("QuietComfort Headphones (0x4075)", EvidenceLevel.VERIFIED, "BMAP 在线产品身份 prince / 0x4075", "整机电量；安静/感知/发现的抗风噪预设；流转"),
            SupportEntry("QC35/35 II、NC700、QC45、QuietComfort Earbuds", EvidenceLevel.PUBLIC_PROFILE, "BMAP 产品 ID；AudioModes / ANR / CNC Profile", "整机或组件电量；按型号开放安静/感知、关闭或抗风噪；流转"),
            SupportEntry("QuietComfort Ultra Headphones/Earbuds 与二代", EvidenceLevel.REFERENCE_PROFILE, "BMAP 产品 ID；同代 AudioModes Profile", "整机或组件电量；安静/感知；附加 ANC 预设归一为降噪；流转"),
            SupportEntry("其他已登记或未知 BMAP", EvidenceLevel.FAMILY_EXTRAPOLATION, "BMAP STATUS 产品身份；GET-only AudioModes/ANR/CNC 探测", "BMAP 电量；仅在合法只读响应后开放对应控制；流转"),
        ),
    ),
    SupportBrand(
        name = "Edifier / 漫步者",
        entries = listOf(
            SupportEntry("W860NB PRO", EvidenceLevel.VERIFIED, "精确名称和头戴形态；Edifier BES RFCOMM", "整机电量；深度降噪/OFF/环境声/抗风噪；流转"),
            SupportEntry("其他 W820/W830/W860 头戴", EvidenceLevel.FAMILY_EXTRAPOLATION, "品牌/系列名称和头戴类别；BES RFCOMM", "合法响应中的整机电量；不开放未验证控制；流转"),
        ),
    ),
    SupportBrand(
        name = "ROSESELSA / 弱水时砂",
        entries = listOf(
            SupportEntry("EARFREE i5", EvidenceLevel.PUBLIC_PROFILE, "精确名称；GATT 011bf5da，7777/8888 特征", "左/右/盒电量；ANC/OFF/通透/抗风噪；流转"),
            SupportEntry("BudsFeel MK2", EvidenceLevel.PUBLIC_PROFILE, "精确名称；RFCOMM 0cf12d31", "左/右/盒电量；ANC/OFF/通透/抗风噪；流转"),
            SupportEntry("其他 EARFREE/EARFEEL 与 BudsFeel", EvidenceLevel.FAMILY_EXTRAPOLATION, "产品线名称 + 对应服务 + 合法状态帧", "握手后左/右/盒电量和四态控制；流转"),
            SupportEntry("其他 ROSESELSA / ROSE", EvidenceLevel.STANDARD_FALLBACK, "保守品牌名称和耳机类别", "Android 整机电量、音量和流转；无私有控制"),
        ),
    ),
    SupportBrand(
        name = "NiceHCK / YuanDao",
        entries = listOf(
            SupportEntry("YuanDao OriG in", EvidenceLevel.PUBLIC_PROFILE, "精确规范化名称；a100 RFCOMM + 合法状态帧", "握手后左/右/盒电量；ANC/OFF/通透/抗风噪；流转"),
            SupportEntry("其他 NiceHCK / YuanDao", EvidenceLevel.STANDARD_FALLBACK, "保守家族名称和耳机类别", "Android 整机电量、音量和流转；无私有控制"),
        ),
    ),
    SupportBrand(
        name = "Apple",
        entries = listOf(
            SupportEntry("AirPods Pro / Max", EvidenceLevel.PUBLIC_PROFILE, "AAP SDP UUID；Pro/Max 名称只细分形态和能力；L2CAP PSM 1001", "动态一至三个组件电量；ANC/OFF/通透；流转", "Adaptive 状态归一为降噪。"),
            SupportEntry("其他带 AAP 服务的 AirPods", EvidenceLevel.FAMILY_EXTRAPOLATION, "AAP SDP UUID，不单独依赖设备名称", "动态组件电量和流转；未确认型号不开放噪声控制"),
        ),
    ),
    SupportBrand(
        name = "Sony",
        entries = listOf(
            SupportEntry("WH / WF / WI / LinkBuds 登记型号", EvidenceLevel.PUBLIC_PROFILE, "精确零售型号 + Sony RFCOMM v1/v2 合法初始化", "按 Profile 提供整机或左/右/盒电量和对应环境声控制", "WH-1000XM2–XM6、CH720N、ULT WEAR、WF-1000XM3–XM5、C500/C510/C700N/C710N、SP800N、LinkBuds/S、WI-SP600N、WI-C100。"),
            SupportEntry("其他 WH/WI/MDR、WF/LinkBuds", EvidenceLevel.FAMILY_EXTRAPOLATION, "产品线名称或 Sony 服务 + 合法初始化", "形态对应电量；名称明确指示降噪时开放三态，否则仅电量"),
            SupportEntry("其他 Sony 耳机", EvidenceLevel.STANDARD_FALLBACK, "保守品牌名称和耳机类别", "Android 整机电量、音量和流转；无私有控制"),
        ),
    ),
    SupportBrand(
        name = "通用蓝牙耳机",
        entries = listOf(
            SupportEntry("标准 A2DP / HFP 耳机", EvidenceLevel.STANDARD_FALLBACK, "Android 蓝牙设备类别或保守耳机名称；排除原生 Xiaomi/REDMI", "系统整机电量复制为左右耳；系统音量和流转；无私有控制"),
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
                        text = "© 2026 HyperEars contributors\nHyperEars 与 Xiaomi、vivo、iQOO、OPPO、Bose、Edifier、ROSESELSA、NiceHCK、Apple、Sony 及相关品牌无关。产品名称仅用于兼容性说明。",
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
                SupportDetail(label = "判型/传输", value = entry.matchBasis)
                SupportDetail(label = "能力", value = entry.capabilities)
                entry.note?.let { note ->
                    Text(
                        text = note,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
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
            text = "证据等级描述验证范围，不代表同品牌所有固件均兼容。设备流转仍由 HyperOS 和 MiLink 的系统链路负责。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun EvidenceBadge(level: EvidenceLevel) {
    val color = when (level) {
        EvidenceLevel.VERIFIED -> MaterialTheme.colorScheme.primary
        EvidenceLevel.PUBLIC_PROFILE -> MaterialTheme.colorScheme.secondary
        EvidenceLevel.REFERENCE_PROFILE -> MaterialTheme.colorScheme.tertiary
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
        EvidenceLevel.VERIFIED -> "已在 HyperEars 实机完成连接、读取、控制和卡片回读。"
        EvidenceLevel.PUBLIC_PROFILE -> "依据可检查的公开实现建立具体型号画像，尚待更多实机覆盖。"
        EvidenceLevel.REFERENCE_PROFILE -> "依据同家族参考协议盲适配，具体固件可能存在差异。"
        EvidenceLevel.FAMILY_EXTRAPOLATION -> "名称只选择候选协议；能力还需服务、身份或合法响应确认。"
        EvidenceLevel.STANDARD_FALLBACK -> "仅使用 Android 与 MiLink 的标准耳机能力，不发送厂商控制命令。"
    }
