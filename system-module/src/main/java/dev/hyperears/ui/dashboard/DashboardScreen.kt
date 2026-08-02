package dev.hyperears.ui.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.hyperears.R
import java.text.DateFormat
import java.util.Date

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    uiState: DashboardUiState,
    onRefresh: () -> Unit,
    onOpenAbout: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "HyperEars",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
                    )
                },
                actions = {
                    TextButton(onClick = onRefresh) {
                        Text("同步")
                    }
                    IconButton(onClick = onOpenAbout) {
                        Icon(
                            painter = painterResource(R.drawable.ic_info_outline),
                            contentDescription = "关于",
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
            val wideLayout = maxWidth >= 720.dp
            val horizontalPadding = if (wideLayout) 32.dp else 16.dp
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
                        .widthIn(max = 1120.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    RuntimeCard(uiState)
                    SectionHeader(
                        title = "设备会话",
                        count = uiState.sessions.size,
                    )
                    if (uiState.deviceCards.isEmpty()) {
                        EmptySessionsCard()
                    } else if (wideLayout) {
                        uiState.deviceCards.chunked(2).forEach { rowSessions ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(16.dp),
                                verticalAlignment = Alignment.Top,
                            ) {
                                rowSessions.forEach { session ->
                                    DeviceSessionCard(
                                        session = session,
                                        modifier = Modifier.weight(1f),
                                    )
                                }
                                if (rowSessions.size == 1) {
                                    Spacer(Modifier.weight(1f))
                                }
                            }
                        }
                    } else {
                        uiState.deviceCards.forEach { session ->
                            DeviceSessionCard(session)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RuntimeCard(uiState: DashboardUiState) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            RuntimeProcessRow(
                label = "蓝牙进程 Hook",
                status = if (uiState.runtimeResponsive) {
                    "已响应 · ${uiState.lastUpdatedAtMillis?.let(::formatTime) ?: "—"}"
                } else {
                    "未响应"
                },
                online = uiState.runtimeResponsive,
            )
            Spacer(Modifier.height(12.dp))
            RuntimeProcessRow(
                label = "MiLink 进程 Hook",
                status = if (uiState.miLinkProcesses.isEmpty()) {
                    "未响应"
                } else {
                    "${uiState.miLinkProcesses.size} 个进程响应"
                },
                online = uiState.miLinkProcesses.isNotEmpty(),
            )
            Spacer(Modifier.height(18.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Spacer(Modifier.height(16.dp))
            Row(modifier = Modifier.fillMaxWidth()) {
                SummaryMetric("状态接收", uiState.miLinkObservedCount, Modifier.weight(1f))
                SummaryMetric("身份查询", uiState.identityQueriedCount, Modifier.weight(1f))
                SummaryMetric("能力查询", uiState.capabilitiesQueriedCount, Modifier.weight(1f))
                SummaryMetric("活动会话", uiState.sessions.size, Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun RuntimeProcessRow(
    label: String,
    status: String,
    online: Boolean,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        StatusDot(
            color = if (online) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.error
            },
        )
        Text(
            text = label,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            text = status,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun SummaryMetric(
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
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun SectionHeader(
    title: String,
    count: Int,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            text = count.toString(),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun EmptySessionsCard() {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = "暂无活动设备会话",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = "受支持耳机连接后显示",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun DeviceSessionCard(
    session: DeviceSessionUiModel,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
    ) {
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
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = "Profile  ${session.profileName}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = "ID  ${session.profileId}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = "蓝牙  ${session.address}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                    )
                }
                Spacer(Modifier.size(12.dp))
                PhasePill(session.phase)
            }

            ProfileFacts(session)

            Text(
                text = "耳机链路",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            EarbudLinkStrip(session.headsetLifecycle)

            Text(
                text = "MiLink 处理",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            LifecycleStrip(session.miLinkLifecycle)

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

            MetricStrip(session.metrics)
        }
    }
}

@Composable
private fun ProfileFacts(session: DeviceSessionUiModel) {
    if (!session.profileResolved) {
        Text(
            text = session.profileSummary,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
        )
        return
    }
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Text(
            text = session.profileSummary,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = "控制  ${session.controlSummary}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun EarbudLinkStrip(
    stages: List<DeviceLinkStage>,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        stages.forEach { stage ->
            LinkState(stage.label, stage.complete, Modifier.weight(1f))
        }
    }
}

@Composable
private fun MetricStrip(metrics: List<DeviceMetric>) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        metrics.forEach { metric ->
            CompactMetric(metric.label, metric.value, Modifier.weight(1f))
        }
    }
}

@Composable
private fun LinkState(
    label: String,
    observed: Boolean,
    modifier: Modifier = Modifier,
) {
    val color = if (observed) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.outline
    }
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        color = if (observed) {
            color.copy(alpha = 0.12f)
        } else {
            MaterialTheme.colorScheme.surfaceContainer
        },
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            StatusDot(color)
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = color,
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun LifecycleStrip(stages: List<DeviceLifecycleStage>) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        stages.forEach { stage ->
            val color = when {
                stage.complete -> MaterialTheme.colorScheme.primary
                stage.active -> MaterialTheme.colorScheme.tertiary
                else -> MaterialTheme.colorScheme.outline
            }
            Surface(
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(14.dp),
                color = if (stage.complete || stage.active) {
                    color.copy(alpha = 0.12f)
                } else {
                    MaterialTheme.colorScheme.surfaceContainer
                },
            ) {
                Column(
                    modifier = Modifier.padding(vertical = 10.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(3.dp),
                ) {
                    StatusDot(color)
                    Text(
                        text = stage.label,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                    )
                    Text(
                        text = stage.value,
                        style = MaterialTheme.typography.labelMedium,
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
private fun CompactMetric(
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
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
        )
    }
}

@Composable
private fun PhasePill(phase: DevicePhase) {
    val color = when (phase) {
        DevicePhase.CONNECTING -> MaterialTheme.colorScheme.tertiary
        DevicePhase.PREPARING_PRIVATE_CHANNEL -> MaterialTheme.colorScheme.secondary
        DevicePhase.WAITING_FOR_MILINK -> MaterialTheme.colorScheme.tertiary
        DevicePhase.STATE_ACCEPTED,
        DevicePhase.IDENTITY_QUERIED,
        DevicePhase.CAPABILITIES_QUERIED,
        -> MaterialTheme.colorScheme.primary
    }
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = color.copy(alpha = 0.12f),
        contentColor = color,
    ) {
        Text(
            text = phase.label,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun StatusDot(color: Color) {
    Box(
        modifier = Modifier
            .size(8.dp)
            .background(color, CircleShape),
    )
}

private fun formatTime(timestamp: Long): String =
    DateFormat.getTimeInstance(DateFormat.MEDIUM).format(Date(timestamp))
