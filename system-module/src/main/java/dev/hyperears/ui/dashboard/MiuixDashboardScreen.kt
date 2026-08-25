package dev.hyperears.ui.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.hyperears.integration.NoiseMode
import dev.hyperears.ui.components.MiuixHyperEarsPage
import java.text.DateFormat
import java.util.Date
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.HorizontalDivider
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.preference.RadioButtonPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.window.WindowDialog

@Composable
fun MiuixDashboardScreen(
    uiState: DashboardUiState,
    onRefresh: () -> Unit,
    onSetNoiseMode: (address: String, sessionToken: String, mode: NoiseMode) -> Unit,
) {
    MiuixHyperEarsPage(title = "HyperEars") { pagePadding, scrollBehavior ->
        LazyColumn(
            state = rememberLazyListState(),
            modifier = Modifier
                .fillMaxSize()
                .padding(pagePadding)
                .nestedScroll(scrollBehavior.nestedScrollConnection),
            contentPadding = PaddingValues(
                start = 12.dp,
                end = 12.dp,
                top = 12.dp,
                bottom = 0.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item(key = "runtime") { MiuixRuntimeCard(uiState, onRefresh) }
            item(key = "session-header") {
                MiuixSectionHeader("设备会话", uiState.sessions.size)
            }
            if (uiState.deviceCards.isEmpty()) {
                item(key = "empty-sessions") { MiuixEmptySessionsCard() }
            } else {
                items(
                    items = uiState.deviceCards,
                    key = { session -> "${session.address}:${session.adapterId}" },
                ) { session ->
                    MiuixDeviceSessionCard(session) { mode ->
                        onSetNoiseMode(session.address, session.sessionToken, mode)
                    }
                }
            }
        }
    }
}

@Composable
private fun MiuixRuntimeCard(
    uiState: DashboardUiState,
    onRefresh: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "运行状态",
                    modifier = Modifier.weight(1f),
                    style = MiuixTheme.textStyles.headline1,
                    fontWeight = FontWeight.SemiBold,
                )
                TextButton(text = "同步", onClick = onRefresh)
            }
            MiuixRuntimeProcessRow(
                label = "蓝牙进程 Hook",
                status = if (uiState.runtimeResponsive) {
                    "已响应 · ${uiState.lastUpdatedAtMillis?.let(::formatMiuixTime) ?: "—"}"
                } else {
                    "未响应"
                },
                online = uiState.runtimeResponsive,
            )
            MiuixRuntimeProcessRow(
                label = "MiLink 进程 Hook",
                status = if (uiState.miLinkProcesses.isEmpty()) {
                    "未响应"
                } else {
                    "${uiState.miLinkProcesses.size} 个进程响应"
                },
                online = uiState.miLinkProcesses.isNotEmpty(),
            )
            HorizontalDivider()
            Row(modifier = Modifier.fillMaxWidth()) {
                MiuixSummaryMetric("状态接收", uiState.miLinkObservedCount, Modifier.weight(1f))
                MiuixSummaryMetric("身份查询", uiState.identityQueriedCount, Modifier.weight(1f))
                MiuixSummaryMetric("能力查询", uiState.capabilitiesQueriedCount, Modifier.weight(1f))
                MiuixSummaryMetric("活动会话", uiState.sessions.size, Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun MiuixRuntimeProcessRow(
    label: String,
    status: String,
    online: Boolean,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        MiuixStatusDot(
            if (online) MiuixTheme.colorScheme.primary else MiuixTheme.colorScheme.error,
        )
        Text(
            text = label,
            modifier = Modifier.weight(1f),
            style = MiuixTheme.textStyles.body1,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            text = status,
            style = MiuixTheme.textStyles.body2,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
        )
    }
}

@Composable
private fun MiuixSummaryMetric(
    label: String,
    value: Int,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(
            text = value.toString(),
            style = MiuixTheme.textStyles.title2,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            text = label,
            style = MiuixTheme.textStyles.footnote2,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
        )
    }
}

@Composable
private fun MiuixSectionHeader(title: String, count: Int) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            style = MiuixTheme.textStyles.title3,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            text = count.toString(),
            style = MiuixTheme.textStyles.footnote1,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
        )
    }
}

@Composable
private fun MiuixEmptySessionsCard() {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = "暂无活动设备会话",
                style = MiuixTheme.textStyles.headline1,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = "受支持耳机连接后显示",
                style = MiuixTheme.textStyles.body2,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            )
        }
    }
}

@Composable
private fun MiuixDeviceSessionCard(
    session: DeviceSessionUiModel,
    onSetNoiseMode: (NoiseMode) -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = session.deviceName,
                        style = MiuixTheme.textStyles.title3,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    MiuixSecondaryText("Adapter  ${session.adapterName}")
                    MiuixSecondaryText("ID  ${session.adapterId}")
                    MiuixSecondaryText("蓝牙  ${session.address}")
                }
                Spacer(Modifier.size(12.dp))
                MiuixPhasePill(session.phase)
            }

            MiuixAdapterFacts(session)
            MiuixSectionLabel("会话状态")
            MiuixSessionStatusList(session.headsetLifecycle)
            MiuixSectionLabel("MiLink 处理")
            MiuixLifecycleStrip(session.miLinkLifecycle)
            HorizontalDivider()
            MiuixMetricStrip(session.metrics, session.noiseControl, onSetNoiseMode)
        }
    }
}

@Composable
private fun MiuixSecondaryText(text: String) {
    Text(
        text = text,
        style = MiuixTheme.textStyles.footnote1,
        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

@Composable
private fun MiuixAdapterFacts(session: DeviceSessionUiModel) {
    val color = if (session.adapterResolved) {
        MiuixTheme.colorScheme.onSurfaceVariantSummary
    } else {
        MiuixTheme.colorScheme.error
    }
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Text(session.adapterSummary, style = MiuixTheme.textStyles.footnote1, color = color)
        if (session.adapterResolved) {
            Text(
                text = "控制  ${session.controlSummary}",
                style = MiuixTheme.textStyles.footnote1,
                color = color,
            )
        }
    }
}

@Composable
private fun MiuixSectionLabel(text: String) {
    Text(
        text = text,
        style = MiuixTheme.textStyles.footnote1,
        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
    )
}

@Composable
private fun MiuixSessionStatusList(stages: List<DeviceLinkStage>) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        stages.forEachIndexed { index, stage ->
            MiuixSessionStatusRow(stage)
            if (index != stages.lastIndex) {
                HorizontalDivider(modifier = Modifier.padding(start = 20.dp))
            }
        }
    }
}

@Composable
private fun MiuixSessionStatusRow(stage: DeviceLinkStage) {
    val color = when (stage.status) {
        DeviceLinkStatus.READY -> MiuixTheme.colorScheme.primary
        DeviceLinkStatus.ACTIVE -> MiuixTheme.colorScheme.secondary
        DeviceLinkStatus.INACTIVE -> MiuixTheme.colorScheme.outline
        DeviceLinkStatus.ERROR -> MiuixTheme.colorScheme.error
    }
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        MiuixStatusDot(color)
        Text(
            text = stage.label,
            modifier = Modifier.weight(1f),
            style = MiuixTheme.textStyles.body2,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
        )
        Text(
            text = stage.value,
            style = MiuixTheme.textStyles.body2,
            color = color,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun MiuixLifecycleStrip(stages: List<DeviceLifecycleStage>) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        stages.forEach { stage ->
            val color = when {
                stage.complete -> MiuixTheme.colorScheme.primary
                stage.active -> MiuixTheme.colorScheme.secondary
                else -> MiuixTheme.colorScheme.outline
            }
            Card(
                modifier = Modifier.weight(1f),
                cornerRadius = 14.dp,
            ) {
                Column(
                    modifier = Modifier.padding(vertical = 10.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(3.dp),
                ) {
                    MiuixStatusDot(color)
                    Text(
                        text = stage.label,
                        style = MiuixTheme.textStyles.footnote2,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        maxLines = 1,
                    )
                    Text(
                        text = stage.value,
                        style = MiuixTheme.textStyles.footnote2,
                        color = color,
                        fontWeight = if (stage.complete || stage.active) {
                            FontWeight.SemiBold
                        } else {
                            FontWeight.Normal
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun MiuixMetricStrip(
    metrics: List<DeviceMetric>,
    noiseControl: NoiseControlUiModel?,
    onSetNoiseMode: (NoiseMode) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        metrics.forEach { metric ->
            if (metric.kind == DeviceMetricKind.NOISE_MODE && noiseControl != null) {
                MiuixNoiseModeMetric(
                    metric = metric,
                    control = noiseControl,
                    onSetNoiseMode = onSetNoiseMode,
                    modifier = Modifier.weight(1f),
                )
            } else {
                MiuixCompactMetric(metric.label, metric.value, Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun MiuixNoiseModeMetric(
    metric: DeviceMetric,
    control: NoiseControlUiModel,
    onSetNoiseMode: (NoiseMode) -> Unit,
    modifier: Modifier = Modifier,
) {
    var showDialog by rememberSaveable { mutableStateOf(false) }
    val selectable = control.enabled && control.supportedModes.size > 1
    Column(
        modifier = modifier.clickable(enabled = selectable) { showDialog = true },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(
            text = metric.value,
            style = MiuixTheme.textStyles.headline2,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
        )
        Text(
            text = metric.label,
            style = MiuixTheme.textStyles.footnote2,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            maxLines = 1,
        )
    }
    WindowDialog(
        show = showDialog && selectable,
        title = "降噪模式",
        onDismissRequest = { showDialog = false },
    ) {
        Column {
            control.supportedModes.forEach { mode ->
                RadioButtonPreference(
                    title = mode.displayName(),
                    selected = mode == control.selectedMode,
                    onClick = {
                        showDialog = false
                        if (mode != control.selectedMode) onSetNoiseMode(mode)
                    },
                )
            }
        }
    }
}

@Composable
private fun MiuixCompactMetric(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(
            text = value,
            style = MiuixTheme.textStyles.headline2,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
        )
        Text(
            text = label,
            style = MiuixTheme.textStyles.footnote2,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            maxLines = 1,
        )
    }
}

@Composable
private fun MiuixPhasePill(phase: DevicePhase) {
    val color = when (phase) {
        DevicePhase.SYSTEM_DISCONNECTED,
        DevicePhase.PROTOCOL_REJECTED,
        DevicePhase.TRANSPORT_DORMANT,
        -> MiuixTheme.colorScheme.error

        DevicePhase.TRANSPORT_CONNECTING,
        DevicePhase.TRANSPORT_RECOVERING,
        DevicePhase.PROTOCOL_CONFIRMING,
        DevicePhase.WAITING_FOR_MILINK,
        DevicePhase.EXTERNAL_CONTROL_APP,
        -> MiuixTheme.colorScheme.secondary

        DevicePhase.STATE_ACCEPTED,
        DevicePhase.IDENTITY_QUERIED,
        DevicePhase.CAPABILITIES_QUERIED,
        -> MiuixTheme.colorScheme.primary
    }
    Card(
        cornerRadius = 12.dp,
        colors = top.yukonga.miuix.kmp.basic.CardDefaults.defaultColors(
            color = color.copy(alpha = 0.12f),
            contentColor = color,
        ),
    ) {
        Text(
            text = phase.label,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            style = MiuixTheme.textStyles.footnote1,
            fontWeight = FontWeight.SemiBold,
            color = color,
        )
    }
}

@Composable
private fun MiuixStatusDot(color: Color) {
    Box(
        modifier = Modifier
            .size(8.dp)
            .background(color, CircleShape),
    )
}

private fun formatMiuixTime(timestamp: Long): String =
    DateFormat.getTimeInstance(DateFormat.MEDIUM).format(Date(timestamp))
