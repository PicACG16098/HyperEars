package dev.hyperears.integration

import dev.hyperears.protocol.starring.StarRingWireCodec
import java.util.UUID

/**
 * Shared StarRing family behavior.
 *
 * Unknown family members retain Android's standard headset behavior. Private transports are
 * opened only by a concrete model adapter whose command set has been verified.
 */
open class StarRingEarbudAdapter : StandardEarbudAdapter() {
    override val id: String = ID
    override val displayName: String = "StarRing headset"
    override val transports: List<EarbudTransportSpec> = listOf(
        RfcommEndpointSpec.Channel(number = 28),
        RfcommEndpointSpec.Channel(number = 28, secure = false),
        RfcommEndpointSpec.ServiceUuid(
            uuid = SPP_UUID.toString(),
            id = "spp-uuid",
        ),
        RfcommEndpointSpec.Channel(number = 5),
    )

    override fun matches(identity: EarbudIdentity): Boolean {
        val name = normalizeDeviceName(identity.deviceName.orEmpty())
        return name.startsWith("starring") || name.startsWith("lightyear")
    }

    companion object {
        const val ID = "starring-family"
        val SPP_UUID: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
    }
}

/** Concrete adapter for the captured StarRing Ultra protocol. */
class StarRingUltraAdapter : StarRingEarbudAdapter() {

    override val id: String = ID
    override val displayName: String = "StarRing Ultra"
    override val resolution: AdapterResolution = AdapterResolution.EXACT_MATCH
    override val miLinkCardPresentationId: MiLinkCardPresentationId = PRESENTATION_ID
    override val privateProtocolRequired: Boolean = true
    override val transports: List<EarbudTransportSpec> = listOf(
        GattTransportSpec(
            writeCharacteristicUuid = WRITE_CHARACTERISTIC_UUID,
            notifyCharacteristicUuid = NOTIFY_CHARACTERISTIC_UUID,
            writeInstanceId = 0xA102,
            notifyInstanceId = 0xA105,
            id = "starring-official-gatt",
        ),
    ) + super.transports
    override val noiseControlConfirmation: ControlConfirmationPolicy =
        ControlConfirmationPolicy.DEVICE_REPORT
    override val batterySource: BatterySource = BatterySource.PRIVATE_PROTOCOL
    override val capabilities: EarbudCapabilities = super.capabilities.copy(
        noiseControl = true,
        windNoiseControl = true,
    )
    override val supportedNoiseModes: Set<NoiseMode> = NoiseMode.entries.toSet()

    override fun matches(identity: EarbudIdentity): Boolean =
        normalizeDeviceName(identity.deviceName.orEmpty()) == "starringultra"

    override fun createProtocolSession(): ProtocolSession = StarRingUltraProtocolSession()

    companion object {
        const val ID = "starring-ultra"
        val PRESENTATION_ID = MiLinkCardPresentationId(ID)
        private const val WRITE_CHARACTERISTIC_UUID =
            "00007777-0000-1000-8000-00805F9B34FB"
        private const val NOTIFY_CHARACTERISTIC_UUID =
            "00008888-0000-1000-8000-00805F9B34FB"
    }
}

private class StarRingUltraProtocolSession : ProtocolSession {
    private val decoder = StarRingWireCodec.Decoder()

    override fun initialReadCommands(): List<ByteArray> = listOf(
        StarRingWireCodec.queryNoiseMode,
        StarRingWireCodec.queryBattery,
    )

    override fun encode(request: ControlRequest): List<ByteArray> = when (request) {
        ControlRequest.Refresh -> initialReadCommands()
        is ControlRequest.SetNoiseMode -> listOf(
            StarRingWireCodec.setNoiseMode(request.mode.toProtocolMode()),
        )
    }

    override fun readback(request: ControlRequest): List<ByteArray> = emptyList()

    override fun offer(bytes: ByteArray): List<ProtocolEvent> =
        decoder.offer(bytes).map { frame ->
            StarRingWireCodec.parseBatteryState(frame)?.let {
                return@map ProtocolEvent.BatteryChanged(
                    EarbudBattery(
                        left = BatteryReading(it.leftPercent, charging = false),
                        right = BatteryReading(it.rightPercent, charging = false),
                        case = BatteryReading(it.casePercent, charging = false),
                    ),
                )
            }
            StarRingWireCodec.parseNoiseState(frame)?.let {
                return@map ProtocolEvent.NoiseModeChanged(
                    mode = it.mode.toDomainMode(),
                )
            }
            ProtocolEvent.UnknownFrame(
                version = 0,
                vendor = frame.group,
                command = frame.command,
                payloadSize = frame.payload.size,
            )
        }

    override fun reset() {
        decoder.reset()
    }

    private fun NoiseMode.toProtocolMode(): StarRingWireCodec.NoiseMode = when (this) {
        NoiseMode.ANC -> StarRingWireCodec.NoiseMode.ANC
        NoiseMode.OFF -> StarRingWireCodec.NoiseMode.NORMAL
        NoiseMode.TRANSPARENCY -> StarRingWireCodec.NoiseMode.TRANSPARENCY
        NoiseMode.WIND -> StarRingWireCodec.NoiseMode.WIND
    }

    private fun StarRingWireCodec.NoiseMode.toDomainMode(): NoiseMode = when (this) {
        StarRingWireCodec.NoiseMode.ANC -> NoiseMode.ANC
        StarRingWireCodec.NoiseMode.NORMAL -> NoiseMode.OFF
        StarRingWireCodec.NoiseMode.TRANSPARENCY -> NoiseMode.TRANSPARENCY
        StarRingWireCodec.NoiseMode.WIND -> NoiseMode.WIND
    }
}
