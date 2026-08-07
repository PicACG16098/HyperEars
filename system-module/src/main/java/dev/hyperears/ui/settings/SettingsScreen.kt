package dev.hyperears.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.hyperears.root.RootAction
import dev.hyperears.root.RootActionState
import dev.hyperears.settings.ModuleSettings
import dev.hyperears.ui.components.HyperEarsPage
import dev.hyperears.ui.components.rememberSwitchHaptics

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    settings: ModuleSettings,
    rootAvailable: Boolean?,
    rootActionState: RootActionState,
    onSettingsChanged: (ModuleSettings) -> Unit,
    onRunRootAction: (RootAction) -> Unit,
    onExportLogs: () -> Unit,
) {
    HyperEarsPage(title = "设置") { pagePadding, scrollBehavior ->
        var pendingRootAction by remember { mutableStateOf<RootAction?>(null) }
        val listState = rememberLazyListState()

        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .padding(pagePadding)
                .nestedScroll(scrollBehavior.nestedScrollConnection),
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = 12.dp,
                bottom = 0.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item(key = "preferences") {
                SettingsGroupCard {
                    TogglePreference(
                        title = "暂停模块",
                        detail = "停用第三方耳机集成。",
                        checked = settings.modulePaused,
                        onCheckedChange = {
                            onSettingsChanged(settings.copy(modulePaused = it))
                        },
                    )
                    PreferenceDivider()
                    TogglePreference(
                        title = "打开厂商设置",
                        detail = "需勾选对应厂商应用作用域。",
                        checked = settings.preferVendorControlApp,
                        onCheckedChange = {
                            onSettingsChanged(settings.copy(preferVendorControlApp = it))
                        },
                    )
                    PreferenceDivider()
                    TogglePreference(
                        title = "运行时退避",
                        detail = "厂商控制 App 运行时自动让出耳机私有控制通道，需勾选对应作用域。",
                        checked = settings.yieldToVendorControlApp,
                        onCheckedChange = {
                            onSettingsChanged(settings.copy(yieldToVendorControlApp = it))
                        },
                    )
                    PreferenceDivider()
                    TogglePreference(
                        title = "详细日志",
                        detail = "记录注入进程与应用操作；LSPosed 需关闭“禁用详细日志”并开启“输出日志到守护进程”。",
                        checked = settings.diagnosticLogging,
                        onCheckedChange = {
                            onSettingsChanged(settings.copy(diagnosticLogging = it))
                        },
                    )
                }
            }
            item(key = "quick-actions") {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (rootAvailable != true) {
                        Text(
                            text = if (rootAvailable == false) {
                                "需要 Root 权限"
                            } else {
                                "正在检查 Root 权限"
                            },
                            modifier = Modifier.padding(horizontal = 4.dp),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    SettingsGroupCard {
                        ActionPreference(
                            title = "导出日志",
                            detail = "导出 LSPosed 模块日志与应用操作日志。",
                            actionLabel = "导出",
                            available = rootAvailable == true,
                            running = false,
                            onClick = onExportLogs,
                        )
                        PreferenceDivider()
                        RootAction.entries.forEachIndexed { index, action ->
                            ActionPreference(
                                title = action.title,
                                detail = action.detail,
                                actionLabel = "执行",
                                available = rootAvailable == true,
                                running = rootActionState is RootActionState.Running &&
                                    rootActionState.action == action,
                                onClick = { pendingRootAction = action },
                            )
                            if (index != RootAction.entries.lastIndex) {
                                PreferenceDivider()
                            }
                        }
                    }
                }
            }
        }

        pendingRootAction?.let { action ->
            AlertDialog(
                onDismissRequest = { pendingRootAction = null },
                title = { Text(action.title) },
                text = { Text(action.detail) },
                confirmButton = {
                    TextButton(
                        onClick = {
                            pendingRootAction = null
                            onRunRootAction(action)
                        },
                    ) {
                        Text("执行")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { pendingRootAction = null }) {
                        Text("取消")
                    }
                },
            )
        }
    }
}

@Composable
private fun SettingsGroupCard(
    content: @Composable () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
    ) {
        Column { content() }
    }
}

@Composable
private fun PreferenceDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(horizontal = 16.dp),
        color = MaterialTheme.colorScheme.outlineVariant,
    )
}

@Composable
private fun TogglePreference(
    title: String,
    detail: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    val haptics = rememberSwitchHaptics()
    ListItem(
        headlineContent = {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
            )
        },
        supportingContent = {
            Text(
                text = detail,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
        trailingContent = {
            Switch(
                checked = checked,
                onCheckedChange = { updated ->
                    haptics.perform(updated)
                    onCheckedChange(updated)
                },
            )
        },
        colors = ListItemDefaults.colors(
            containerColor = Color.Transparent,
        ),
    )
}

@Composable
private fun ActionPreference(
    title: String,
    detail: String,
    actionLabel: String,
    available: Boolean,
    running: Boolean,
    onClick: () -> Unit,
) {
    val contentColor = if (available) {
        MaterialTheme.colorScheme.onSurface
    } else {
        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
    }
    ListItem(
        headlineContent = {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                color = contentColor,
            )
        },
        supportingContent = {
            Text(
                text = detail,
                style = MaterialTheme.typography.bodyMedium,
                color = contentColor,
            )
        },
        trailingContent = {
            Button(
                onClick = onClick,
                enabled = available && !running,
            ) {
                Text(if (running) "执行中" else actionLabel)
            }
        },
        colors = ListItemDefaults.colors(
            containerColor = Color.Transparent,
        ),
    )
}
