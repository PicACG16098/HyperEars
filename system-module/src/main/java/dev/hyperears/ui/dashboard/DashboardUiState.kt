package dev.hyperears.ui.dashboard

import dev.hyperears.bridge.BridgeReceipt
import dev.hyperears.bridge.BridgeStage
import dev.hyperears.integration.EarbudState
import dev.hyperears.integration.ControlOwnership
import dev.hyperears.integration.PrivateTransportState
import dev.hyperears.integration.ProtocolHandshakeState
import dev.hyperears.integration.SystemProfileState
import java.util.Locale

data class DeviceSessionSnapshot(
    val state: EarbudState,
    val sessionToken: String,
    val bridgeReceipts: Set<BridgeReceipt> = emptySet(),
) {
    val bridgeObserved: Boolean
        get() = observed(BridgeStage.STATE_ACCEPTED)

    val identityQueried: Boolean
        get() = observed(BridgeStage.IDENTITY_QUERIED)

    val capabilitiesQueried: Boolean
        get() = observed(BridgeStage.CAPABILITIES_QUERIED)

    val runtimeNotified: Boolean
        get() = observed(BridgeStage.RUNTIME_NOTIFIED)

    val phase: DevicePhase
        get() = when {
            state.lifecycle.systemProfile == SystemProfileState.DISCONNECTED ->
                DevicePhase.SYSTEM_DISCONNECTED
            state.lifecycle.controlOwnership == ControlOwnership.EXTERNAL_APP ->
                DevicePhase.EXTERNAL_CONTROL_APP
            state.lifecycle.privateTransport == PrivateTransportState.CONNECTING ->
                DevicePhase.TRANSPORT_CONNECTING
            state.lifecycle.privateTransport == PrivateTransportState.RECOVERING ->
                DevicePhase.TRANSPORT_RECOVERING
            state.lifecycle.privateTransport == PrivateTransportState.DORMANT ->
                DevicePhase.TRANSPORT_DORMANT
            state.lifecycle.protocolHandshake == ProtocolHandshakeState.PENDING ->
                DevicePhase.PROTOCOL_CONFIRMING
            state.lifecycle.protocolHandshake == ProtocolHandshakeState.REJECTED ->
                DevicePhase.PROTOCOL_REJECTED
            !bridgeObserved -> DevicePhase.WAITING_FOR_MILINK
            capabilitiesQueried -> DevicePhase.CAPABILITIES_QUERIED
            identityQueried -> DevicePhase.IDENTITY_QUERIED
            else -> DevicePhase.STATE_ACCEPTED
        }

    val miLinkLifecycle: List<DeviceLifecycleStage>
        get() = listOf(
            DeviceLifecycleStage(
                label = "状态接收",
                value = if (bridgeObserved) "已接收" else "未观测",
                complete = bridgeObserved,
                active = state.connected && !bridgeObserved,
            ),
            DeviceLifecycleStage(
                label = "身份查询",
                value = if (identityQueried) "已调用" else "未观测",
                complete = identityQueried,
                active = bridgeObserved && !identityQueried,
            ),
            DeviceLifecycleStage(
                label = "卡片能力",
                value = if (capabilitiesQueried) "已调用" else "未观测",
                complete = capabilitiesQueried,
                active = identityQueried && !capabilitiesQueried,
            ),
            DeviceLifecycleStage(
                label = "状态通知",
                value = if (runtimeNotified) "已触发" else "未观测",
                complete = runtimeNotified,
                active = false,
            ),
        )

    private fun observed(stage: BridgeStage): Boolean =
        bridgeReceipts.any {
            it.sessionToken == sessionToken &&
                it.stage == stage &&
                (stage != BridgeStage.STATE_ACCEPTED || it.revision == state.revision)
        }
}

data class DeviceSessionCollection(
    val sessions: Map<String, DeviceSessionSnapshot> = emptyMap(),
    val pendingBridgeReceipts: Map<String, Set<BridgeReceipt>> = emptyMap(),
)

data class DashboardUiState(
    val sessions: List<DeviceSessionSnapshot> = emptyList(),
    val runtimeResponsive: Boolean = false,
    val miLinkProcesses: Set<String> = emptySet(),
    val lastUpdatedAtMillis: Long? = null,
) {
    val deviceCards: List<DeviceSessionUiModel> by lazy(LazyThreadSafetyMode.NONE) {
        sessions.map(DeviceSessionUiProjector::project)
    }

    val connectedCount: Int
        get() = sessions.count { it.state.connected }

    val handshakeCount: Int
        get() = sessions.count { it.state.handshakeAccepted }

    val miLinkObservedCount: Int
        get() = sessions.count { it.bridgeObserved }

    val identityQueriedCount: Int
        get() = sessions.count { it.identityQueried }

    val capabilitiesQueriedCount: Int
        get() = sessions.count { it.capabilitiesQueried }
}

enum class DevicePhase(val label: String) {
    SYSTEM_DISCONNECTED("系统音频未连接"),
    EXTERNAL_CONTROL_APP("专有控制 App 运行中"),
    TRANSPORT_CONNECTING("私有传输连接中"),
    TRANSPORT_RECOVERING("私有传输恢复中"),
    TRANSPORT_DORMANT("私有传输已休眠"),
    PROTOCOL_CONFIRMING("协议确认中"),
    PROTOCOL_REJECTED("协议确认失败"),
    WAITING_FOR_MILINK("等待 MiLink 接收"),
    STATE_ACCEPTED("MiLink 已接收状态"),
    IDENTITY_QUERIED("MiLink 已查询身份"),
    CAPABILITIES_QUERIED("MiLink 已查询能力"),
}

data class DeviceLifecycleStage(
    val label: String,
    val value: String,
    val complete: Boolean,
    val active: Boolean,
)

object DeviceSessionReducer {
    fun reduce(
        previous: DeviceSessionCollection,
        state: EarbudState,
        sessionToken: String,
    ): DeviceSessionCollection {
        val address = state.address?.takeIf(String::isNotBlank) ?: return previous
        val key = normalizeAddress(address)
        if (!state.sessionActive) {
            return previous.copy(
                sessions = previous.sessions - key,
                pendingBridgeReceipts = previous.pendingBridgeReceipts - key,
            )
        }

        val receipts = buildSet {
            addAll(previous.sessions[key]?.bridgeReceipts.orEmpty())
            addAll(previous.pendingBridgeReceipts[key].orEmpty())
        }.filterTo(mutableSetOf()) {
            it.sessionToken == sessionToken &&
                (
                    it.stage != BridgeStage.STATE_ACCEPTED ||
                        it.revision == state.revision
                )
        }
        return previous.copy(
            sessions = previous.sessions + (
                key to DeviceSessionSnapshot(
                    state = state,
                    sessionToken = sessionToken,
                    bridgeReceipts = receipts,
                )
            ),
            pendingBridgeReceipts = previous.pendingBridgeReceipts - key,
        )
    }

    fun acceptBridgeReceipt(
        previous: DeviceSessionCollection,
        receipt: BridgeReceipt,
    ): DeviceSessionCollection {
        val key = normalizeAddress(receipt.address)
        val session = previous.sessions[key]
        if (session == null) {
            return previous.copy(
                pendingBridgeReceipts = previous.pendingBridgeReceipts + (
                    key to (previous.pendingBridgeReceipts[key].orEmpty() + receipt)
                ),
            )
        }
        if (receipt.sessionToken != session.sessionToken) return previous
        if (receipt.stage == BridgeStage.STATE_ACCEPTED &&
            receipt.revision < session.state.revision
        ) {
            return previous
        }
        if (receipt.stage == BridgeStage.STATE_ACCEPTED &&
            receipt.revision > session.state.revision
        ) {
            return previous.copy(
                pendingBridgeReceipts = previous.pendingBridgeReceipts + (
                    key to (previous.pendingBridgeReceipts[key].orEmpty() + receipt)
                ),
            )
        }
        return previous.copy(
            sessions = previous.sessions + (
                key to session.copy(
                    bridgeReceipts = session.bridgeReceipts + receipt,
                )
            ),
        )
    }

    private fun normalizeAddress(address: String): String =
        address.uppercase(Locale.ROOT)
}
