package dev.hyperears.integration

enum class NoiseMode {
    ANC,
    OFF,
    TRANSPARENCY,
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
)

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

sealed interface RfcommEndpointSpec {
    val id: String

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

data class EarbudCapabilities(
    val battery: Boolean = false,
    val noiseControl: Boolean = false,
    val audioHandoff: Boolean = false,
    val spatialAudio: Boolean = false,
    val wearDetection: Boolean = false,
    val findDevice: Boolean = false,
)

data class MiLinkIdentity(val deviceId: String)

/**
 * One private-protocol codec instance owned by one physical device session.
 *
 * Model selection and capabilities belong to [EarbudAdapter]; this interface only translates
 * between the common domain model and a vendor byte stream.
 */
interface EarbudProtocol {
    fun initialReadCommands(): List<ByteArray>
    fun encode(request: ControlRequest): List<ByteArray>
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
