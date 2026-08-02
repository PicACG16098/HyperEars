package dev.hyperears.ui.dashboard

import dev.hyperears.integration.BatteryReading
import dev.hyperears.integration.BatterySource
import dev.hyperears.integration.EarbudAdapter
import dev.hyperears.integration.EarbudAdapterRegistry
import dev.hyperears.integration.GattTransportSpec
import dev.hyperears.integration.HeadsetFormFactor
import dev.hyperears.integration.L2capEndpointSpec
import dev.hyperears.integration.NoiseMode
import dev.hyperears.integration.RfcommEndpointSpec
import dev.hyperears.integration.TransportReadiness

/** Complete, adapter-agnostic data required to render one dashboard card. */
data class DeviceSessionUiModel(
    val deviceName: String,
    val address: String,
    val profileName: String,
    val profileId: String,
    val profileSummary: String,
    val controlSummary: String,
    val profileResolved: Boolean,
    val phase: DevicePhase,
    val headsetLifecycle: List<DeviceLinkStage>,
    val miLinkLifecycle: List<DeviceLifecycleStage>,
    val metrics: List<DeviceMetric>,
)

data class DeviceLinkStage(
    val label: String,
    val complete: Boolean,
)

data class DeviceMetric(
    val label: String,
    val value: String,
)

/**
 * The only UI-layer boundary allowed to inspect an [EarbudAdapter].
 *
 * Compose receives a stable, generic presentation model. Concrete profiles, transport classes,
 * battery topology and readiness rules never leak into the view hierarchy.
 */
object DeviceSessionUiProjector {
    fun project(session: DeviceSessionSnapshot): DeviceSessionUiModel {
        val state = session.state
        val adapter = EarbudAdapterRegistry.byId(state.modelId)
        return DeviceSessionUiModel(
            deviceName = state.deviceName ?: "未命名耳机",
            address = state.address ?: "—",
            profileName = adapter?.displayName ?: "未解析",
            profileId = state.modelId ?: "—",
            profileSummary = adapter?.profileSummary() ?: "当前 Profile ID 未在本版本注册",
            controlSummary = adapter?.controlSummary() ?: "能力未知",
            profileResolved = adapter != null,
            phase = session.phase,
            headsetLifecycle = headsetLifecycle(session, adapter),
            miLinkLifecycle = session.miLinkLifecycle,
            metrics = metrics(session, adapter),
        )
    }

    private fun headsetLifecycle(
        session: DeviceSessionSnapshot,
        adapter: EarbudAdapter?,
    ): List<DeviceLinkStage> = buildList {
        val state = session.state
        add(DeviceLinkStage("A2DP 会话", state.sessionActive))
        if (state.privateProtocolRequired) {
            add(DeviceLinkStage("私有通道", state.privateChannelConnected))
            val requiresHandshake =
                adapter?.transportReadiness == TransportReadiness.PROTOCOL_HANDSHAKE
            add(
                DeviceLinkStage(
                    label = if (requiresHandshake) "协议确认" else "连接即就绪",
                    complete = if (requiresHandshake) {
                        state.handshakeAccepted
                    } else {
                        state.privateChannelConnected
                    },
                ),
            )
        } else {
            add(DeviceLinkStage("身份桥", state.connected))
            add(DeviceLinkStage("无私有通道", true))
        }
    }

    private fun metrics(
        session: DeviceSessionSnapshot,
        adapter: EarbudAdapter?,
    ): List<DeviceMetric> = buildList {
        val battery = session.state.battery
        if (adapter?.formFactor == HeadsetFormFactor.HEADPHONES || battery.overall.available) {
            val aggregate = battery.overall.takeIf(BatteryReading::available)
                ?: battery.left.takeIf(BatteryReading::available)
                ?: battery.right.takeIf(BatteryReading::available)
                ?: battery.case.takeIf(BatteryReading::available)
                ?: battery.overall
            add(DeviceMetric("整机", aggregate.displayValue()))
        } else {
            add(DeviceMetric("左耳", battery.left.displayValue()))
            add(DeviceMetric("右耳", battery.right.displayValue()))
            add(DeviceMetric("充电盒", battery.case.displayValue()))
        }
        add(
            DeviceMetric(
                label = "模式",
                value = if (adapter?.capabilities?.noiseControl == false) {
                    "不支持"
                } else {
                    session.state.noiseMode.displayName()
                },
            ),
        )
    }

    private fun EarbudAdapter.profileSummary(): String =
        "形态  ${formFactor.displayName()}  ·  " +
            "电量  ${batterySource.displayName()}  ·  " +
            "传输  ${transportSummary()}"

    private fun EarbudAdapter.transportSummary(): String {
        if (!privateProtocolRequired) return "标准 A2DP/HFP"
        return transports
            .map { transport ->
                when (transport) {
                    is RfcommEndpointSpec -> "RFCOMM"
                    is GattTransportSpec -> "GATT"
                    is L2capEndpointSpec -> "L2CAP"
                }
            }
            .distinct()
            .joinToString(" / ")
            .ifEmpty { "未声明" }
    }

    private fun EarbudAdapter.controlSummary(): String {
        val modeLabels = supportedNoiseModes.map { mode -> mode.displayName() }
        return when {
            modeLabels.isNotEmpty() -> modeLabels.joinToString(" / ")
            capabilities.audioHandoff -> "MiLink 流转与系统音量；无私有模式"
            else -> "无"
        }
    }
}

private fun BatteryReading.displayValue(): String =
    percent?.let { value -> if (charging) "$value%+" else "$value%" } ?: "—"

private fun NoiseMode?.displayName(): String = when (this) {
    NoiseMode.ANC -> "降噪"
    NoiseMode.OFF -> "关闭"
    NoiseMode.TRANSPARENCY -> "通透"
    NoiseMode.WIND -> "抗风噪"
    null -> "—"
}

private fun HeadsetFormFactor.displayName(): String = when (this) {
    HeadsetFormFactor.TWS -> "TWS"
    HeadsetFormFactor.HEADPHONES -> "头戴"
}

private fun BatterySource.displayName(): String = when (this) {
    BatterySource.NONE -> "不提供"
    BatterySource.SYSTEM_AGGREGATE -> "Android 整机"
    BatterySource.PRIVATE_PROTOCOL -> "私有协议"
}
