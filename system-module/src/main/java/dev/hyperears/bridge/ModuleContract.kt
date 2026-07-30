package dev.hyperears.bridge

import android.content.Intent
import dev.hyperears.integration.BatteryReading
import dev.hyperears.integration.ControlRequest
import dev.hyperears.integration.EarbudBattery
import dev.hyperears.integration.EarbudState
import dev.hyperears.integration.NoiseMode

object ModuleContract {
    const val ACTION_REQUEST_STATE = "dev.hyperears.action.REQUEST_STATE"
    const val ACTION_REQUEST_BRIDGE_STATUS = "dev.hyperears.action.REQUEST_BRIDGE_STATUS"
    const val ACTION_CONTROL = "dev.hyperears.action.CONTROL"
    const val ACTION_STATE_CHANGED = "dev.hyperears.action.STATE_CHANGED"
    const val ACTION_BRIDGE_STATE_OBSERVED =
        "dev.hyperears.action.BRIDGE_STATE_OBSERVED"
    const val ACTION_BRIDGE_RUNTIME_OBSERVED =
        "dev.hyperears.action.BRIDGE_RUNTIME_OBSERVED"

    const val MODULE_PACKAGE = "dev.hyperears"
    const val BLUETOOTH_PACKAGE = "com.android.bluetooth"
    const val MILINK_PACKAGE = "com.milink.service"

    private const val EXTRA_REPLY_PACKAGE = "reply_package"
    private const val EXTRA_SESSION_TOKEN = "session_token"
    private const val EXTRA_CONTROL = "control"
    private const val EXTRA_NOISE_MODE = "noise_mode"
    private const val EXTRA_MODEL_ID = "model_id"
    private const val EXTRA_DEVICE_NAME = "device_name"
    private const val EXTRA_ADDRESS = "address"
    private const val EXTRA_SESSION_ACTIVE = "session_active"
    private const val EXTRA_PRIVATE_PROTOCOL_REQUIRED = "private_protocol_required"
    private const val EXTRA_CONNECTED = "connected"
    private const val EXTRA_PRIVATE_CHANNEL_CONNECTED = "private_channel_connected"
    private const val EXTRA_HANDSHAKE = "handshake"
    private const val EXTRA_REVISION = "revision"
    private const val EXTRA_CONSUMER_PROCESS = "consumer_process"
    private const val EXTRA_BRIDGE_STAGE = "bridge_stage"
    private const val EXTRA_LEFT = "left_battery"
    private const val EXTRA_LEFT_CHARGING = "left_charging"
    private const val EXTRA_RIGHT = "right_battery"
    private const val EXTRA_RIGHT_CHARGING = "right_charging"
    private const val EXTRA_CASE = "case_battery"
    private const val EXTRA_CASE_CHARGING = "case_charging"

    private const val CONTROL_REFRESH = "refresh"
    private const val CONTROL_SET_NOISE = "set_noise"

    val stateConsumerPackages = setOf(
        MODULE_PACKAGE,
        MILINK_PACKAGE,
    )

    fun requestState(replyPackage: String): Intent =
        Intent(ACTION_REQUEST_STATE)
            .setPackage(BLUETOOTH_PACKAGE)
            .putExtra(EXTRA_REPLY_PACKAGE, replyPackage)

    fun requestBridgeStatus(replyPackage: String): Intent =
        Intent(ACTION_REQUEST_BRIDGE_STATUS)
            .setPackage(MILINK_PACKAGE)
            .putExtra(EXTRA_REPLY_PACKAGE, replyPackage)

    fun control(
        request: ControlRequest,
        address: String,
        sessionToken: String,
    ): Intent = Intent(ACTION_CONTROL)
        .setPackage(BLUETOOTH_PACKAGE)
        .putExtra(EXTRA_ADDRESS, address)
        .putExtra(EXTRA_SESSION_TOKEN, sessionToken)
        .apply {
            when (request) {
                ControlRequest.Refresh -> putExtra(EXTRA_CONTROL, CONTROL_REFRESH)
                is ControlRequest.SetNoiseMode -> {
                    putExtra(EXTRA_CONTROL, CONTROL_SET_NOISE)
                    putExtra(EXTRA_NOISE_MODE, request.mode.name)
                }
            }
        }

    fun stateChanged(
        state: EarbudState,
        sessionToken: String,
        targetPackage: String,
    ): Intent = Intent(ACTION_STATE_CHANGED)
        .setPackage(targetPackage)
        .putState(state)
        .putExtra(EXTRA_SESSION_TOKEN, sessionToken)

    fun bridgeStateObserved(
        state: EarbudState,
        sessionToken: String,
        consumerProcess: String,
        targetPackage: String,
        stage: BridgeStage = BridgeStage.STATE_ACCEPTED,
    ): Intent = Intent(ACTION_BRIDGE_STATE_OBSERVED)
        .setPackage(targetPackage)
        .putExtra(EXTRA_ADDRESS, state.address)
        .putExtra(EXTRA_SESSION_TOKEN, sessionToken)
        .putExtra(EXTRA_REVISION, state.revision)
        .putExtra(EXTRA_CONSUMER_PROCESS, consumerProcess)
        .putExtra(EXTRA_BRIDGE_STAGE, stage.name)

    fun bridgeRuntimeObserved(
        consumerProcess: String,
        targetPackage: String,
    ): Intent = Intent(ACTION_BRIDGE_RUNTIME_OBSERVED)
        .setPackage(targetPackage)
        .putExtra(EXTRA_CONSUMER_PROCESS, consumerProcess)

    fun Intent.readReplyPackage(): String? =
        getStringExtra(EXTRA_REPLY_PACKAGE)?.takeIf { it in stateConsumerPackages }

    fun Intent.readSessionToken(): String? = getStringExtra(EXTRA_SESSION_TOKEN)

    fun Intent.readBridgeReceipt(): BridgeReceipt? {
        if (action != ACTION_BRIDGE_STATE_OBSERVED) return null
        val address = getStringExtra(EXTRA_ADDRESS)?.takeIf(String::isNotBlank) ?: return null
        val sessionToken = getStringExtra(EXTRA_SESSION_TOKEN)
            ?.takeIf(String::isNotBlank)
            ?: return null
        val consumerProcess = getStringExtra(EXTRA_CONSUMER_PROCESS)
            ?.takeIf(String::isNotBlank)
            ?: return null
        val stage = getStringExtra(EXTRA_BRIDGE_STAGE)
            ?.let { runCatching { BridgeStage.valueOf(it) }.getOrNull() }
            ?: return null
        if (!hasExtra(EXTRA_REVISION)) return null
        return BridgeReceipt(
            address = address,
            sessionToken = sessionToken,
            revision = getLongExtra(EXTRA_REVISION, -1),
            consumerProcess = consumerProcess,
            stage = stage,
        )
    }

    fun Intent.readBridgeRuntimeReceipt(): BridgeRuntimeReceipt? {
        if (action != ACTION_BRIDGE_RUNTIME_OBSERVED) return null
        val consumerProcess = getStringExtra(EXTRA_CONSUMER_PROCESS)
            ?.takeIf(String::isNotBlank)
            ?: return null
        return BridgeRuntimeReceipt(consumerProcess)
    }

    fun Intent.readAddress(): String? = getStringExtra(EXTRA_ADDRESS)

    fun Intent.readControl(): ControlRequest? = when (getStringExtra(EXTRA_CONTROL)) {
        CONTROL_REFRESH -> ControlRequest.Refresh
        CONTROL_SET_NOISE -> getStringExtra(EXTRA_NOISE_MODE)
            ?.let { runCatching { NoiseMode.valueOf(it) }.getOrNull() }
            ?.let { ControlRequest.SetNoiseMode(it) }
        else -> null
    }

    fun Intent.putState(state: EarbudState): Intent = apply {
        putExtra(EXTRA_MODEL_ID, state.modelId)
        putExtra(EXTRA_DEVICE_NAME, state.deviceName)
        putExtra(EXTRA_ADDRESS, state.address)
        putExtra(EXTRA_SESSION_ACTIVE, state.sessionActive)
        putExtra(EXTRA_PRIVATE_PROTOCOL_REQUIRED, state.privateProtocolRequired)
        putExtra(EXTRA_CONNECTED, state.connected)
        putExtra(EXTRA_PRIVATE_CHANNEL_CONNECTED, state.privateChannelConnected)
        putExtra(EXTRA_HANDSHAKE, state.handshakeAccepted)
        putExtra(EXTRA_REVISION, state.revision)
        putExtra(EXTRA_NOISE_MODE, state.noiseMode?.name)
        putExtra(EXTRA_LEFT, state.battery.left.percent ?: -1)
        putExtra(EXTRA_LEFT_CHARGING, state.battery.left.charging)
        putExtra(EXTRA_RIGHT, state.battery.right.percent ?: -1)
        putExtra(EXTRA_RIGHT_CHARGING, state.battery.right.charging)
        putExtra(EXTRA_CASE, state.battery.case.percent ?: -1)
        putExtra(EXTRA_CASE_CHARGING, state.battery.case.charging)
    }

    fun Intent.readState(): EarbudState? {
        if (action != ACTION_STATE_CHANGED || !hasExtra(EXTRA_REVISION)) return null
        return EarbudState(
            modelId = getStringExtra(EXTRA_MODEL_ID),
            deviceName = getStringExtra(EXTRA_DEVICE_NAME),
            address = getStringExtra(EXTRA_ADDRESS),
            sessionActive = getBooleanExtra(EXTRA_SESSION_ACTIVE, false),
            privateProtocolRequired =
                getBooleanExtra(EXTRA_PRIVATE_PROTOCOL_REQUIRED, false),
            connected = getBooleanExtra(EXTRA_CONNECTED, false),
            privateChannelConnected =
                getBooleanExtra(EXTRA_PRIVATE_CHANNEL_CONNECTED, false),
            handshakeAccepted = getBooleanExtra(EXTRA_HANDSHAKE, false),
            battery = EarbudBattery(
                left = batteryReading(EXTRA_LEFT, EXTRA_LEFT_CHARGING),
                right = batteryReading(EXTRA_RIGHT, EXTRA_RIGHT_CHARGING),
                case = batteryReading(EXTRA_CASE, EXTRA_CASE_CHARGING),
            ),
            noiseMode = getStringExtra(EXTRA_NOISE_MODE)
                ?.let { runCatching { NoiseMode.valueOf(it) }.getOrNull() },
            revision = getLongExtra(EXTRA_REVISION, 0),
        )
    }

    private fun Intent.batteryReading(levelKey: String, chargingKey: String): BatteryReading {
        val value = getIntExtra(levelKey, -1)
        return BatteryReading(
            percent = value.takeIf { it in 0..100 },
            charging = value in 0..100 && getBooleanExtra(chargingKey, false),
        )
    }
}

data class BridgeReceipt(
    val address: String,
    val sessionToken: String,
    val revision: Long,
    val consumerProcess: String,
    val stage: BridgeStage,
)

enum class BridgeStage {
    STATE_ACCEPTED,
    IDENTITY_QUERIED,
    CAPABILITIES_QUERIED,
    RUNTIME_NOTIFIED,
}

data class BridgeRuntimeReceipt(
    val consumerProcess: String,
)
