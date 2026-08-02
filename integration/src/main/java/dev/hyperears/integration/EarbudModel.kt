package dev.hyperears.integration

enum class NoiseMode {
    ANC,
    OFF,
    TRANSPARENCY,
    WIND,
}

data class BatteryReading(
    val percent: Int?,
    val charging: Boolean,
) {
    init {
        require(percent == null || percent in 0..100)
    }

    val available: Boolean get() = percent != null
}

data class EarbudBattery(
    val left: BatteryReading = BatteryReading(null, false),
    val right: BatteryReading = BatteryReading(null, false),
    val case: BatteryReading = BatteryReading(null, false),
    val overall: BatteryReading = BatteryReading(null, false),
) {
    companion object {
        /** Projects one authoritative aggregate value without inventing component telemetry. */
        fun fromAggregate(percent: Int?): EarbudBattery {
            val reading = BatteryReading(percent?.takeIf { it in 0..100 }, charging = false)
            return EarbudBattery(
                left = reading,
                right = reading,
                overall = reading,
            )
        }

        /**
         * Projects Android's single headset battery value onto MiLink's left/right schema.
         *
         * Standard Bluetooth exposes no trustworthy case level or per-bud split, so both buds
         * deliberately receive the same aggregate value and the case remains unavailable.
         */
        fun fromSystemAggregate(percent: Int?): EarbudBattery {
            return fromAggregate(percent)
        }
    }
}

enum class BatterySource {
    NONE,
    SYSTEM_AGGREGATE,
    PRIVATE_PROTOCOL,
}

/**
 * Defines where control-state truth comes from after a successful vendor write.
 *
 * The policy belongs to the model adapter; byte-level readback commands belong to the protocol.
 */
enum class ControlConfirmationPolicy {
    /** Publish only an authoritative device report. */
    DEVICE_REPORT,

    /** Publish the requested state after the complete write transaction succeeds. */
    PUBLISH_AFTER_WRITE,

    /** Publish after the write, then request an authoritative state refresh. */
    PUBLISH_AFTER_WRITE_THEN_REFRESH,
}

enum class SystemProfileState {
    DISCONNECTED,
    CONNECTED,
}

enum class PrivateTransportState {
    NOT_REQUIRED,
    IDLE,
    CONNECTING,
    CONNECTED,
    RECOVERING,
    DORMANT,
}

enum class ProtocolHandshakeState {
    NOT_REQUIRED,
    PENDING,
    CONFIRMED,
    REJECTED,
}

/** One authoritative lifecycle projection for a physical headset session. */
data class DeviceLifecycle(
    val systemProfile: SystemProfileState = SystemProfileState.DISCONNECTED,
    val privateTransport: PrivateTransportState = PrivateTransportState.NOT_REQUIRED,
    val protocolHandshake: ProtocolHandshakeState = ProtocolHandshakeState.NOT_REQUIRED,
) {
    val active: Boolean get() = systemProfile == SystemProfileState.CONNECTED
    val privateTransportRequired: Boolean
        get() = privateTransport != PrivateTransportState.NOT_REQUIRED
    val privateTransportConnected: Boolean
        get() = privateTransport == PrivateTransportState.CONNECTED
    val protocolConfirmed: Boolean
        get() = protocolHandshake == ProtocolHandshakeState.CONFIRMED
    val protocolReady: Boolean
        get() = protocolHandshake in setOf(
            ProtocolHandshakeState.NOT_REQUIRED,
            ProtocolHandshakeState.CONFIRMED,
        )
    val operational: Boolean
        get() = active && (
            !privateTransportRequired ||
                privateTransportConnected && protocolReady
            )
}

data class EarbudState(
    val adapter: AdapterSnapshot? = null,
    val deviceName: String? = null,
    val address: String? = null,
    val lifecycle: DeviceLifecycle = DeviceLifecycle(),
    val battery: EarbudBattery = EarbudBattery(),
    val noiseMode: NoiseMode? = null,
    val revision: Long = 0,
) {
    /** Compatibility views. Lifecycle truth is stored only in [lifecycle]. */
    val modelId: String? get() = adapter?.id
    val sessionActive: Boolean get() = lifecycle.active
    val privateProtocolRequired: Boolean get() = lifecycle.privateTransportRequired
    val connected: Boolean get() = lifecycle.operational
    val privateChannelConnected: Boolean get() = lifecycle.privateTransportConnected
    val handshakeAccepted: Boolean get() = lifecycle.protocolConfirmed
}

/** Evidence decoded from a vendor byte stream. It never represents system lifecycle state. */
sealed interface ProtocolEvent {
    data object HandshakeAccepted : ProtocolEvent
    data object HandshakeRejected : ProtocolEvent

    /** Authoritative vendor product identifier; mapping to an Adapter remains adapter-owned. */
    data class ProductIdentified(val productId: Int) : ProtocolEvent

    /** Private-protocol abilities established by successful read-only responses. */
    data class CapabilitiesIdentified(
        val battery: Boolean,
        val noiseModes: Set<NoiseMode> = emptySet(),
    ) : ProtocolEvent

    data class BatteryChanged(val battery: EarbudBattery) : ProtocolEvent
    data class NoiseModeChanged(val mode: NoiseMode) : ProtocolEvent

    data class UnknownFrame(
        val version: Int,
        val vendor: Int,
        val command: Int,
        val payloadSize: Int,
    ) : ProtocolEvent
}

sealed interface ControlRequest {
    data object Refresh : ControlRequest
    data class SetNoiseMode(val mode: NoiseMode) : ControlRequest
}

/** A model-declared private-protocol transport candidate. */
sealed interface EarbudTransportSpec {
    val id: String
}

sealed interface RfcommEndpointSpec : EarbudTransportSpec {

    data class ServiceUuid(
        val uuid: String,
        override val id: String,
    ) : RfcommEndpointSpec

    data class Channel(
        val number: Int,
        val secure: Boolean = true,
        override val id: String = "rfcomm-$number${if (secure) "" else "-insecure"}",
    ) : RfcommEndpointSpec
}

/**
 * Bluetooth Classic L2CAP endpoint identified by a fixed protocol/service multiplexer.
 *
 * Apple Accessory Protocol uses the BR/EDR socket type with PSM `0x1001`; keeping that detail in
 * the transport declaration prevents Apple-specific reflection from leaking into the protocol or
 * device adapter layers.
 */
data class L2capEndpointSpec(
    val psm: Int,
    val serviceUuid: String,
    val authenticated: Boolean = true,
    val encrypted: Boolean = true,
    override val id: String,
) : EarbudTransportSpec {
    init {
        require(psm in 1..0xFFFF)
        require(serviceUuid.isNotBlank())
    }
}

/**
 * BLE GATT transport whose characteristics carry the protocol's unmodified business frames.
 *
 * UUIDs are authoritative. Optional instance IDs pin a captured attribute table when a device
 * exposes duplicate characteristic UUIDs; runtimes still validate characteristic properties.
 */
data class GattTransportSpec(
    /** Optional service boundary used to disambiguate otherwise common characteristic UUIDs. */
    val serviceUuid: String? = null,
    val writeCharacteristicUuid: String,
    val notifyCharacteristicUuid: String,
    val writeInstanceId: Int? = null,
    val notifyInstanceId: Int? = null,
    override val id: String,
) : EarbudTransportSpec {
    init {
        require(serviceUuid == null || serviceUuid.isNotBlank())
    }
}

data class EarbudCapabilities(
    val battery: Boolean = false,
    val noiseControl: Boolean = false,
    val windNoiseControl: Boolean = false,
    val audioHandoff: Boolean = false,
    val spatialAudio: Boolean = false,
    val wearDetection: Boolean = false,
    val findDevice: Boolean = false,
)

enum class AdapterResolution {
    STANDARD,
    EXACT_MATCH,
    FAMILY_MATCH,
    PROTOCOL_CONFIRMED,
}

enum class TransportKind {
    RFCOMM,
    GATT,
    L2CAP,
}

/** Immutable device-facing projection of the one active adapter instance. */
data class AdapterSnapshot(
    val id: String,
    val displayName: String,
    val resolution: AdapterResolution,
    val privateProtocolRequired: Boolean,
    val batterySource: BatterySource,
    val formFactor: HeadsetFormFactor,
    val capabilities: EarbudCapabilities,
    val supportedNoiseModes: Set<NoiseMode>,
    val presentationId: MiLinkCardPresentationId?,
    val transportKinds: Set<TransportKind>,
    val ancSwitchCooldownMs: Long,
)

data class AdapterRuntimeState(
    val battery: EarbudBattery = EarbudBattery(),
    val noiseMode: NoiseMode? = null,
)

enum class AdapterActivation {
    KEEP_CHANNEL_READY,
    RESTART_ON_CURRENT_CHANNEL,
    RECONNECT,
}

sealed interface HandshakeResult {
    data object AwaitingEvidence : HandshakeResult
    data object Ready : HandshakeResult
    data object Rejected : HandshakeResult
    data class Replace(
        val adapter: EarbudAdapter,
        val activation: AdapterActivation,
    ) : HandshakeResult
}

data class AdapterIoResult(
    val commands: List<ByteArray> = emptyList(),
    val handshake: HandshakeResult? = null,
    val stateChanged: Boolean = false,
    val unknownFrames: List<ProtocolEvent.UnknownFrame> = emptyList(),
)

data class AdapterControlResult(
    val accepted: Boolean,
    val commands: List<ByteArray> = emptyList(),
    val readback: List<ByteArray> = emptyList(),
    val stateChanged: Boolean = false,
)

/**
 * Physical presentation declared by an adapter.
 *
 * This is deliberately platform-neutral. The MiLink bridge maps it onto one known stock carrier
 * ID per form factor; concrete model identity never leaks into Xiaomi's device-ID registry.
 */
enum class HeadsetFormFactor {
    TWS,
    HEADPHONES,
}

/** Opaque link from a concrete device adapter to its platform-specific MiLink presentation. */
@JvmInline
value class MiLinkCardPresentationId(
    val value: String,
) {
    init {
        require(value.isNotBlank())
    }
}

/**
 * One private-protocol codec instance owned by one physical device session.
 *
 * Model selection and capabilities belong to [EarbudAdapter]; this interface only translates
 * between the common domain model and a vendor byte stream.
 */
interface ProtocolSession {
    fun initialReadCommands(): List<ByteArray>
    fun encode(request: ControlRequest): List<ByteArray>

    /**
     * Protocol-generated writes produced while decoding incoming bytes.
     *
     * ACK-driven protocols use this to advance their request queue without owning the transport.
     * The runtime drains this exactly once after each [offer] call. Most protocols are passive and
     * retain the empty default.
     */
    fun drainImmediateCommands(): List<ByteArray> = emptyList()

    /**
     * Optional commands unlocked by an authoritative protocol event.
     *
     * This keeps family-safe discovery separate from model-specific reads. The session serializes
     * returned commands on the existing transport; protocols never own sockets or coroutines.
     */
    fun followUpCommands(event: ProtocolEvent): List<ByteArray> = emptyList()

    /** Optional authoritative readback sent after [encode] completes successfully. */
    fun readback(request: ControlRequest): List<ByteArray> = emptyList()

    fun offer(bytes: ByteArray): List<ProtocolEvent>
    fun reset()
}
