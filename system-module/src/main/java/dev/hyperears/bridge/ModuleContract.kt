package dev.hyperears.bridge

import android.content.Intent
import dev.hyperears.integration.AdapterResolution
import dev.hyperears.integration.AdapterSnapshot
import dev.hyperears.integration.BatteryReading
import dev.hyperears.integration.BatterySource
import dev.hyperears.integration.ControlRequest
import dev.hyperears.integration.EarbudCapabilities
import dev.hyperears.integration.EarbudBattery
import dev.hyperears.integration.EarbudState
import dev.hyperears.integration.DeviceLifecycle
import dev.hyperears.integration.HeadsetFormFactor
import dev.hyperears.integration.MiLinkCardPresentationId
import dev.hyperears.integration.NoiseMode
import dev.hyperears.integration.TransportKind
import dev.hyperears.integration.PrivateTransportState
import dev.hyperears.integration.ProtocolHandshakeState
import dev.hyperears.integration.SystemProfileState

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
    private const val EXTRA_ADAPTER_DISPLAY_NAME = "adapter_display_name"
    private const val EXTRA_ADAPTER_RESOLUTION = "adapter_resolution"
    private const val EXTRA_ADAPTER_BATTERY_SOURCE = "adapter_battery_source"
    private const val EXTRA_ADAPTER_FORM_FACTOR = "adapter_form_factor"
    private const val EXTRA_ADAPTER_PRESENTATION = "adapter_presentation"
    private const val EXTRA_ADAPTER_NOISE_MODES = "adapter_noise_modes"
    private const val EXTRA_ADAPTER_TRANSPORT_KINDS = "adapter_transport_kinds"
    private const val EXTRA_ADAPTER_ANC_COOLDOWN = "adapter_anc_cooldown"
    private const val EXTRA_CAP_BATTERY = "cap_battery"
    private const val EXTRA_CAP_NOISE = "cap_noise"
    private const val EXTRA_CAP_WIND = "cap_wind"
    private const val EXTRA_CAP_HANDOFF = "cap_handoff"
    private const val EXTRA_CAP_SPATIAL = "cap_spatial"
    private const val EXTRA_CAP_WEAR = "cap_wear"
    private const val EXTRA_CAP_FIND = "cap_find"
    private const val EXTRA_DEVICE_NAME = "device_name"
    private const val EXTRA_ADDRESS = "address"
    private const val EXTRA_SESSION_ACTIVE = "session_active"
    private const val EXTRA_PRIVATE_PROTOCOL_REQUIRED = "private_protocol_required"
    private const val EXTRA_CONNECTED = "connected"
    private const val EXTRA_PRIVATE_CHANNEL_CONNECTED = "private_channel_connected"
    private const val EXTRA_HANDSHAKE = "handshake"
    private const val EXTRA_SYSTEM_PROFILE_STATE = "system_profile_state"
    private const val EXTRA_PRIVATE_TRANSPORT_STATE = "private_transport_state"
    private const val EXTRA_PROTOCOL_HANDSHAKE_STATE = "protocol_handshake_state"
    private const val EXTRA_REVISION = "revision"
    private const val EXTRA_CONSUMER_PROCESS = "consumer_process"
    private const val EXTRA_BRIDGE_STAGE = "bridge_stage"
    private const val EXTRA_LEFT = "left_battery"
    private const val EXTRA_LEFT_CHARGING = "left_charging"
    private const val EXTRA_RIGHT = "right_battery"
    private const val EXTRA_RIGHT_CHARGING = "right_charging"
    private const val EXTRA_CASE = "case_battery"
    private const val EXTRA_CASE_CHARGING = "case_charging"
    private const val EXTRA_OVERALL = "overall_battery"
    private const val EXTRA_OVERALL_CHARGING = "overall_charging"

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
        state.adapter?.let { adapter ->
            putExtra(EXTRA_ADAPTER_DISPLAY_NAME, adapter.displayName)
            putExtra(EXTRA_ADAPTER_RESOLUTION, adapter.resolution.name)
            putExtra(EXTRA_ADAPTER_BATTERY_SOURCE, adapter.batterySource.name)
            putExtra(EXTRA_ADAPTER_FORM_FACTOR, adapter.formFactor.name)
            putExtra(EXTRA_ADAPTER_PRESENTATION, adapter.presentationId?.value)
            putExtra(EXTRA_ADAPTER_NOISE_MODES, adapter.supportedNoiseModes.map(NoiseMode::name).toTypedArray())
            putExtra(EXTRA_ADAPTER_TRANSPORT_KINDS, adapter.transportKinds.map(TransportKind::name).toTypedArray())
            putExtra(EXTRA_ADAPTER_ANC_COOLDOWN, adapter.ancSwitchCooldownMs)
            putExtra(EXTRA_CAP_BATTERY, adapter.capabilities.battery)
            putExtra(EXTRA_CAP_NOISE, adapter.capabilities.noiseControl)
            putExtra(EXTRA_CAP_WIND, adapter.capabilities.windNoiseControl)
            putExtra(EXTRA_CAP_HANDOFF, adapter.capabilities.audioHandoff)
            putExtra(EXTRA_CAP_SPATIAL, adapter.capabilities.spatialAudio)
            putExtra(EXTRA_CAP_WEAR, adapter.capabilities.wearDetection)
            putExtra(EXTRA_CAP_FIND, adapter.capabilities.findDevice)
        }
        putExtra(EXTRA_DEVICE_NAME, state.deviceName)
        putExtra(EXTRA_ADDRESS, state.address)
        putExtra(EXTRA_SESSION_ACTIVE, state.sessionActive)
        putExtra(EXTRA_PRIVATE_PROTOCOL_REQUIRED, state.privateProtocolRequired)
        putExtra(EXTRA_CONNECTED, state.connected)
        putExtra(EXTRA_PRIVATE_CHANNEL_CONNECTED, state.privateChannelConnected)
        putExtra(EXTRA_HANDSHAKE, state.handshakeAccepted)
        putExtra(EXTRA_SYSTEM_PROFILE_STATE, state.lifecycle.systemProfile.name)
        putExtra(EXTRA_PRIVATE_TRANSPORT_STATE, state.lifecycle.privateTransport.name)
        putExtra(EXTRA_PROTOCOL_HANDSHAKE_STATE, state.lifecycle.protocolHandshake.name)
        putExtra(EXTRA_REVISION, state.revision)
        putExtra(EXTRA_NOISE_MODE, state.noiseMode?.name)
        putExtra(EXTRA_LEFT, state.battery.left.percent ?: -1)
        putExtra(EXTRA_LEFT_CHARGING, state.battery.left.charging)
        putExtra(EXTRA_RIGHT, state.battery.right.percent ?: -1)
        putExtra(EXTRA_RIGHT_CHARGING, state.battery.right.charging)
        putExtra(EXTRA_CASE, state.battery.case.percent ?: -1)
        putExtra(EXTRA_CASE_CHARGING, state.battery.case.charging)
        putExtra(EXTRA_OVERALL, state.battery.overall.percent ?: -1)
        putExtra(EXTRA_OVERALL_CHARGING, state.battery.overall.charging)
    }

    fun Intent.readState(): EarbudState? {
        if (action != ACTION_STATE_CHANGED || !hasExtra(EXTRA_REVISION)) return null
        return EarbudState(
            adapter = readAdapterSnapshot(),
            deviceName = getStringExtra(EXTRA_DEVICE_NAME),
            address = getStringExtra(EXTRA_ADDRESS),
            lifecycle = readLifecycle(),
            battery = EarbudBattery(
                left = batteryReading(EXTRA_LEFT, EXTRA_LEFT_CHARGING),
                right = batteryReading(EXTRA_RIGHT, EXTRA_RIGHT_CHARGING),
                case = batteryReading(EXTRA_CASE, EXTRA_CASE_CHARGING),
                overall = batteryReading(EXTRA_OVERALL, EXTRA_OVERALL_CHARGING),
            ),
            noiseMode = getStringExtra(EXTRA_NOISE_MODE)
                ?.let { runCatching { NoiseMode.valueOf(it) }.getOrNull() },
            revision = getLongExtra(EXTRA_REVISION, 0),
        )
    }

    private fun Intent.readLifecycle(): DeviceLifecycle {
        val system = getStringExtra(EXTRA_SYSTEM_PROFILE_STATE)
            ?.let { runCatching { SystemProfileState.valueOf(it) }.getOrNull() }
        val transport = getStringExtra(EXTRA_PRIVATE_TRANSPORT_STATE)
            ?.let { runCatching { PrivateTransportState.valueOf(it) }.getOrNull() }
        val handshake = getStringExtra(EXTRA_PROTOCOL_HANDSHAKE_STATE)
            ?.let { runCatching { ProtocolHandshakeState.valueOf(it) }.getOrNull() }
        if (system != null && transport != null && handshake != null) {
            return DeviceLifecycle(system, transport, handshake)
        }

        // Backward-compatible decode for a state broadcast from an older module process.
        val active = getBooleanExtra(EXTRA_SESSION_ACTIVE, false)
        val privateRequired = getBooleanExtra(EXTRA_PRIVATE_PROTOCOL_REQUIRED, false)
        val channelConnected = getBooleanExtra(EXTRA_PRIVATE_CHANNEL_CONNECTED, false)
        val accepted = getBooleanExtra(EXTRA_HANDSHAKE, false)
        return DeviceLifecycle(
            systemProfile = if (active) {
                SystemProfileState.CONNECTED
            } else {
                SystemProfileState.DISCONNECTED
            },
            privateTransport = when {
                !privateRequired -> PrivateTransportState.NOT_REQUIRED
                channelConnected -> PrivateTransportState.CONNECTED
                else -> PrivateTransportState.IDLE
            },
            protocolHandshake = when {
                !privateRequired -> ProtocolHandshakeState.NOT_REQUIRED
                accepted -> ProtocolHandshakeState.CONFIRMED
                else -> ProtocolHandshakeState.PENDING
            },
        )
    }

    private fun Intent.readAdapterSnapshot(): AdapterSnapshot? {
        val id = getStringExtra(EXTRA_MODEL_ID)?.takeIf(String::isNotBlank) ?: return null
        val displayName = getStringExtra(EXTRA_ADAPTER_DISPLAY_NAME) ?: id
        val resolution = getStringExtra(EXTRA_ADAPTER_RESOLUTION)
            ?.let { runCatching { AdapterResolution.valueOf(it) }.getOrNull() }
            ?: AdapterResolution.FAMILY_MATCH
        val batterySource = getStringExtra(EXTRA_ADAPTER_BATTERY_SOURCE)
            ?.let { runCatching { BatterySource.valueOf(it) }.getOrNull() }
            ?: BatterySource.NONE
        val formFactor = getStringExtra(EXTRA_ADAPTER_FORM_FACTOR)
            ?.let { runCatching { HeadsetFormFactor.valueOf(it) }.getOrNull() }
            ?: HeadsetFormFactor.TWS
        val modes = getStringArrayExtra(EXTRA_ADAPTER_NOISE_MODES)
            .orEmpty()
            .mapNotNullTo(linkedSetOf()) { runCatching { NoiseMode.valueOf(it) }.getOrNull() }
        val transportKinds = getStringArrayExtra(EXTRA_ADAPTER_TRANSPORT_KINDS)
            .orEmpty()
            .mapNotNullTo(linkedSetOf()) { runCatching { TransportKind.valueOf(it) }.getOrNull() }
        return AdapterSnapshot(
            id = id,
            displayName = displayName,
            resolution = resolution,
            privateProtocolRequired = getBooleanExtra(EXTRA_PRIVATE_PROTOCOL_REQUIRED, false),
            batterySource = batterySource,
            formFactor = formFactor,
            capabilities = EarbudCapabilities(
                battery = getBooleanExtra(EXTRA_CAP_BATTERY, false),
                noiseControl = getBooleanExtra(EXTRA_CAP_NOISE, false),
                windNoiseControl = getBooleanExtra(EXTRA_CAP_WIND, false),
                audioHandoff = getBooleanExtra(EXTRA_CAP_HANDOFF, false),
                spatialAudio = getBooleanExtra(EXTRA_CAP_SPATIAL, false),
                wearDetection = getBooleanExtra(EXTRA_CAP_WEAR, false),
                findDevice = getBooleanExtra(EXTRA_CAP_FIND, false),
            ),
            supportedNoiseModes = modes,
            presentationId = getStringExtra(EXTRA_ADAPTER_PRESENTATION)
                ?.takeIf(String::isNotBlank)
                ?.let(::MiLinkCardPresentationId),
            transportKinds = transportKinds,
            ancSwitchCooldownMs = getLongExtra(EXTRA_ADAPTER_ANC_COOLDOWN, 0L),
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
