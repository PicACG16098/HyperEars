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
        /**
         * Projects Android's single headset battery value onto MiLink's left/right schema.
         *
         * Standard Bluetooth exposes no trustworthy case level or per-bud split, so both buds
         * deliberately receive the same aggregate value and the case remains unavailable.
         */
        fun fromSystemAggregate(percent: Int?): EarbudBattery {
            val reading = BatteryReading(percent?.takeIf { it in 0..100 }, charging = false)
            return EarbudBattery(
                left = reading,
                right = reading,
                overall = reading,
            )
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

data class EarbudState(
    val modelId: String? = null,
    val deviceName: String? = null,
    val address: String? = null,
    val sessionActive: Boolean = false,
    val privateProtocolRequired: Boolean = false,
    val connected: Boolean = false,
    val privateChannelConnected: Boolean = false,
    val handshakeAccepted: Boolean = false,
    val battery: EarbudBattery = EarbudBattery(),
    val noiseMode: NoiseMode? = null,
    val revision: Long = 0,
)

sealed interface EarbudEvent {
    /**
     * A supported device has entered the system profile lifecycle.
     *
     * This does not imply that its private control channel is ready.
     */
    data class SessionStarted(
        val modelId: String,
        val deviceName: String,
        val address: String,
        val privateProtocolRequired: Boolean,
    ) : EarbudEvent

    /** An identity-only adapter is ready without opening a vendor channel. */
    data object AdapterReady : EarbudEvent

    /** The private vendor channel is ready for reads and writes. */
    data object ChannelConnected : EarbudEvent

    /**
     * The private vendor channel was lost while the system profile remains connected.
     *
     * The session may reconnect without being recreated.
     */
    data object ChannelDisconnected : EarbudEvent

    /** The system profile lifecycle ended and the device session was removed. */
    data object SessionEnded : EarbudEvent

    /** Refines a family match to a concrete model after an authoritative on-wire identity read. */
    data class ModelIdentified(val modelId: String) : EarbudEvent

    data class Handshake(val accepted: Boolean) : EarbudEvent
    data class BatteryChanged(val battery: EarbudBattery) : EarbudEvent
    data class NoiseModeChanged(
        val mode: NoiseMode,
        val acknowledged: Boolean,
    ) : EarbudEvent

    data class UnknownFrame(
        val version: Int,
        val vendor: Int,
        val command: Int,
        val payloadSize: Int,
    ) : EarbudEvent
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
 * BLE GATT transport whose characteristics carry the protocol's unmodified business frames.
 *
 * UUIDs are authoritative. Optional instance IDs pin a captured attribute table when a device
 * exposes duplicate characteristic UUIDs; runtimes still validate characteristic properties.
 */
data class GattTransportSpec(
    val writeCharacteristicUuid: String,
    val notifyCharacteristicUuid: String,
    val writeInstanceId: Int? = null,
    val notifyInstanceId: Int? = null,
    override val id: String,
) : EarbudTransportSpec

data class EarbudCapabilities(
    val battery: Boolean = false,
    val noiseControl: Boolean = false,
    val windNoiseControl: Boolean = false,
    val audioHandoff: Boolean = false,
    val spatialAudio: Boolean = false,
    val wearDetection: Boolean = false,
    val findDevice: Boolean = false,
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
interface EarbudProtocol {
    fun initialReadCommands(): List<ByteArray>
    fun encode(request: ControlRequest): List<ByteArray>

    /**
     * Optional commands unlocked by an authoritative protocol event.
     *
     * This keeps family-safe discovery separate from model-specific reads. The session serializes
     * returned commands on the existing transport; protocols never own sockets or coroutines.
     */
    fun followUpCommands(event: EarbudEvent): List<ByteArray> = emptyList()

    /** Optional authoritative readback sent after [encode] completes successfully. */
    fun readback(request: ControlRequest): List<ByteArray> = emptyList()

    fun offer(bytes: ByteArray): List<EarbudEvent>
    fun reset()
}

object EarbudStateReducer {
    fun reduce(previous: EarbudState, event: EarbudEvent): EarbudState {
        val nextRevision = previous.revision + 1
        val candidate = when (event) {
            is EarbudEvent.SessionStarted -> {
                val sameLogicalDevice =
                    previous.modelId == event.modelId &&
                        previous.address
                            ?.equals(event.address, ignoreCase = true) == true
                EarbudState(
                    modelId = event.modelId,
                    deviceName = event.deviceName,
                    address = event.address,
                    sessionActive = true,
                    privateProtocolRequired = event.privateProtocolRequired,
                    battery = if (sameLogicalDevice) {
                        previous.battery
                    } else {
                        EarbudBattery()
                    },
                    noiseMode = previous.noiseMode.takeIf { sameLogicalDevice },
                    revision = previous.revision,
                )
            }

            EarbudEvent.AdapterReady -> previous.copy(
                connected = true,
                privateChannelConnected = false,
                handshakeAccepted = false,
            )

            EarbudEvent.ChannelConnected -> previous.copy(
                connected = true,
                privateChannelConnected = true,
                handshakeAccepted = false,
            )

            EarbudEvent.ChannelDisconnected -> previous.copy(
                connected = false,
                privateChannelConnected = false,
                handshakeAccepted = false,
            )

            EarbudEvent.SessionEnded -> previous.copy(
                sessionActive = false,
                connected = false,
                privateChannelConnected = false,
                handshakeAccepted = false,
            )

            is EarbudEvent.ModelIdentified -> if (previous.sessionActive) {
                previous.copy(modelId = event.modelId)
            } else {
                previous
            }

            is EarbudEvent.Handshake -> previous.copy(
                handshakeAccepted = event.accepted,
            )

            is EarbudEvent.BatteryChanged -> previous.copy(
                battery = event.battery,
            )

            is EarbudEvent.NoiseModeChanged -> previous.copy(
                noiseMode = event.mode,
            )

            is EarbudEvent.UnknownFrame -> previous
        }
        return if (candidate == previous) {
            previous
        } else {
            candidate.copy(revision = nextRevision)
        }
    }
}
