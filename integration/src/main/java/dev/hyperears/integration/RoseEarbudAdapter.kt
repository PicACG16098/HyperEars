package dev.hyperears.integration

import dev.hyperears.protocol.rose.RoseBudsFeelMk2WireCodec
import dev.hyperears.protocol.rose.RoseEarfreeI5WireCodec

/** Standard Bluetooth fallback for ROSESELSA/ROSE headsets outside a known protocol family. */
open class RoseEarbudAdapter : StandardEarbudAdapter() {
    override val id: String = ID
    override val displayName: String = "ROSESELSA headset"

    override fun matches(identity: EarbudIdentity): Boolean {
        if (identity.nativeSystemEarbud) return false
        val name = normalizeDeviceName(identity.deviceName.orEmpty())
        return name.startsWith("roseselsa") ||
            name.startsWith("roseear") ||
            name.startsWith("rosebudsfeel") ||
            name.startsWith("budsfeel")
    }

    companion object {
        const val ID = "roseselsa-family"
    }
}

/**
 * EARFREE/EARFEEL product-line adapter.
 *
 * Public EARFREE i5 captures establish the service, characteristics and frame grammar. Unknown
 * models in the same named product line may reuse it, but must return a valid protocol frame
 * before the private channel becomes ready.
 */
open class RoseEarfreeProtocolFamilyAdapter : RoseEarbudAdapter() {
    override val id: String = ID
    override val displayName: String = "ROSE EARFREE protocol family"
    override val miLinkCardPresentationId: MiLinkCardPresentationId = PRESENTATION_ID
    override val privateProtocolRequired: Boolean = true
    override val transportReadiness: TransportReadiness =
        TransportReadiness.PROTOCOL_HANDSHAKE
    override val batterySource: BatterySource = BatterySource.PRIVATE_PROTOCOL
    override val capabilities: EarbudCapabilities = EarbudCapabilities(
        battery = true,
        noiseControl = true,
        windNoiseControl = true,
        audioHandoff = true,
    )
    override val supportedNoiseModes: Set<NoiseMode> = NoiseMode.entries.toSet()
    override val transports: List<EarbudTransportSpec> = listOf(
        GattTransportSpec(
            serviceUuid = SERVICE_UUID,
            writeCharacteristicUuid = WRITE_CHARACTERISTIC_UUID,
            notifyCharacteristicUuid = NOTIFY_CHARACTERISTIC_UUID,
            id = "rose-earfree-family-gatt",
        ),
    )

    override fun matches(identity: EarbudIdentity): Boolean {
        if (identity.nativeSystemEarbud) return false
        val name = normalizeDeviceName(identity.deviceName.orEmpty())
        val namedProductLine = name.startsWith("roseselsaearfree") ||
            name.startsWith("roseearfree") ||
            name.startsWith("roseearfeel")
        val advertisedService = identity.serviceUuids.any {
            it.equals(SERVICE_UUID, ignoreCase = true)
        }
        return namedProductLine || (super.matches(identity) && advertisedService)
    }

    override fun createProtocol(): EarbudProtocol = RoseEarfreeProtocol()

    companion object {
        const val ID = "rose-earfree-protocol-family"
        val PRESENTATION_ID = MiLinkCardPresentationId("rose-earfree-protocol")
        const val SERVICE_UUID = "011bf5da-0000-1000-8000-00805f9b34fb"
        const val WRITE_CHARACTERISTIC_UUID =
            "00007777-0000-1000-8000-00805f9b34fb"
        const val NOTIFY_CHARACTERISTIC_UUID =
            "00008888-0000-1000-8000-00805f9b34fb"
    }
}

object RoseEarfreeI5Adapter : RoseEarfreeProtocolFamilyAdapter() {
    const val ID = "roseselsa-earfree-i5"
    val PRESENTATION_ID = RoseEarfreeProtocolFamilyAdapter.PRESENTATION_ID

    override val id: String = ID
    override val displayName: String = "ROSESELSA EARFREE i5"
    override val miLinkCardPresentationId: MiLinkCardPresentationId = PRESENTATION_ID
    override val transportReadiness: TransportReadiness = TransportReadiness.CONNECTED

    override fun matches(identity: EarbudIdentity): Boolean {
        if (identity.nativeSystemEarbud) return false
        return normalizeDeviceName(identity.deviceName.orEmpty()) in MODEL_NAMES
    }

    private val MODEL_NAMES = setOf(
        "roseselsaearfreei5",
        "roseearfreei5",
        "roseearfeeli5",
    )
}

/** BudsFeel product-line adapter using the public MK2 RFCOMM service and frame grammar. */
open class RoseBudsFeelProtocolFamilyAdapter : RoseEarbudAdapter() {
    override val id: String = ID
    override val displayName: String = "ROSE BudsFeel protocol family"
    override val miLinkCardPresentationId: MiLinkCardPresentationId = PRESENTATION_ID
    override val privateProtocolRequired: Boolean = true
    override val transportReadiness: TransportReadiness =
        TransportReadiness.PROTOCOL_HANDSHAKE
    override val batterySource: BatterySource = BatterySource.PRIVATE_PROTOCOL
    override val capabilities: EarbudCapabilities = EarbudCapabilities(
        battery = true,
        noiseControl = true,
        windNoiseControl = true,
        audioHandoff = true,
    )
    override val supportedNoiseModes: Set<NoiseMode> = NoiseMode.entries.toSet()
    override val transports: List<EarbudTransportSpec> = listOf(
        RfcommEndpointSpec.ServiceUuid(
            uuid = DATA_CHANNEL_UUID,
            id = "rose-budsfeel-family-rfcomm",
        ),
    )

    override fun matches(identity: EarbudIdentity): Boolean {
        if (identity.nativeSystemEarbud) return false
        val name = normalizeDeviceName(identity.deviceName.orEmpty())
        val namedProductLine = name.startsWith("rosebudsfeel") || name.startsWith("budsfeel")
        val advertisedService = identity.serviceUuids.any {
            it.equals(DATA_CHANNEL_UUID, ignoreCase = true)
        }
        return namedProductLine || (super.matches(identity) && advertisedService)
    }

    override fun createProtocol(): EarbudProtocol = RoseBudsFeelProtocol()

    companion object {
        const val ID = "rose-budsfeel-protocol-family"
        val PRESENTATION_ID = MiLinkCardPresentationId("rose-budsfeel-protocol")
        const val DATA_CHANNEL_UUID = "0cf12d31-fac3-4553-bd80-d6832e7b3931"
    }
}

object RoseBudsFeelMk2Adapter : RoseBudsFeelProtocolFamilyAdapter() {
    const val ID = "rose-budsfeel-mk2"
    val PRESENTATION_ID = RoseBudsFeelProtocolFamilyAdapter.PRESENTATION_ID

    override val id: String = ID
    override val displayName: String = "ROSE BudsFeel MK2"
    override val miLinkCardPresentationId: MiLinkCardPresentationId = PRESENTATION_ID
    override val transportReadiness: TransportReadiness = TransportReadiness.CONNECTED

    override fun matches(identity: EarbudIdentity): Boolean {
        if (identity.nativeSystemEarbud) return false
        return normalizeDeviceName(identity.deviceName.orEmpty()) in MODEL_NAMES
    }

    private val MODEL_NAMES = setOf("rosebudsfeelmk2", "budsfeelmk2")
}

private class RoseEarfreeProtocol : EarbudProtocol {
    private val decoder = RoseEarfreeI5WireCodec.Decoder()
    private var handshakePublished = false

    override fun initialReadCommands(): List<ByteArray> = listOf(
        RoseEarfreeI5WireCodec.queryBattery,
        RoseEarfreeI5WireCodec.queryNoiseMode,
    )

    override fun encode(request: ControlRequest): List<ByteArray> = when (request) {
        ControlRequest.Refresh -> initialReadCommands()
        is ControlRequest.SetNoiseMode -> listOf(
            RoseEarfreeI5WireCodec.setNoiseMode(request.mode.toWireMode()),
        )
    }

    override fun offer(bytes: ByteArray): List<EarbudEvent> = buildList {
        var acceptedFrame = false
        decoder.offer(bytes).forEach { frame ->
            RoseEarfreeI5WireCodec.parseBattery(frame)?.let { battery ->
                acceptedFrame = true
                add(
                    EarbudEvent.BatteryChanged(
                        EarbudBattery(
                            left = BatteryReading(battery.leftPercent, battery.leftCharging),
                            right = BatteryReading(battery.rightPercent, battery.rightCharging),
                            case = BatteryReading(battery.casePercent, false),
                        ),
                    ),
                )
            }
            RoseEarfreeI5WireCodec.parseNoiseMode(frame)?.let { mode ->
                acceptedFrame = true
                add(EarbudEvent.NoiseModeChanged(mode.toDomainMode(), acknowledged = true))
            }
        }
        if (acceptedFrame && !handshakePublished) {
            add(0, EarbudEvent.Handshake(accepted = true))
            handshakePublished = true
        }
    }

    override fun reset() {
        decoder.reset()
        handshakePublished = false
    }

    private fun NoiseMode.toWireMode(): RoseEarfreeI5WireCodec.NoiseMode = when (this) {
        NoiseMode.ANC -> RoseEarfreeI5WireCodec.NoiseMode.ANC
        NoiseMode.OFF -> RoseEarfreeI5WireCodec.NoiseMode.OFF
        NoiseMode.TRANSPARENCY -> RoseEarfreeI5WireCodec.NoiseMode.TRANSPARENCY
        NoiseMode.WIND -> RoseEarfreeI5WireCodec.NoiseMode.WIND
    }

    private fun RoseEarfreeI5WireCodec.NoiseMode.toDomainMode(): NoiseMode = when (this) {
        RoseEarfreeI5WireCodec.NoiseMode.ANC -> NoiseMode.ANC
        RoseEarfreeI5WireCodec.NoiseMode.OFF -> NoiseMode.OFF
        RoseEarfreeI5WireCodec.NoiseMode.TRANSPARENCY -> NoiseMode.TRANSPARENCY
        RoseEarfreeI5WireCodec.NoiseMode.WIND -> NoiseMode.WIND
    }
}

private class RoseBudsFeelProtocol : EarbudProtocol {
    private val decoder = RoseBudsFeelMk2WireCodec.Decoder()
    private var sequence = 0
    private var handshakePublished = false

    override fun initialReadCommands(): List<ByteArray> = listOf(queryStatus())

    override fun encode(request: ControlRequest): List<ByteArray> = when (request) {
        ControlRequest.Refresh -> listOf(queryStatus())
        is ControlRequest.SetNoiseMode -> listOf(
            RoseBudsFeelMk2WireCodec.setNoiseMode(nextSequence(), request.mode.toWireMode()),
        )
    }

    override fun readback(request: ControlRequest): List<ByteArray> = when (request) {
        ControlRequest.Refresh -> emptyList()
        is ControlRequest.SetNoiseMode -> listOf(queryStatus())
    }

    override fun offer(bytes: ByteArray): List<EarbudEvent> {
        val states = decoder.offer(bytes)
        if (states.isEmpty()) return emptyList()
        return buildList {
            if (!handshakePublished) {
                add(EarbudEvent.Handshake(accepted = true))
                handshakePublished = true
            }
            states.forEach { state ->
                add(
                    when (state) {
                        is RoseBudsFeelMk2WireCodec.State.Battery ->
                            EarbudEvent.BatteryChanged(
                                EarbudBattery(
                                    left = BatteryReading(state.leftPercent, false),
                                    right = BatteryReading(state.rightPercent, false),
                                    case = BatteryReading(state.casePercent, false),
                                ),
                            )

                        is RoseBudsFeelMk2WireCodec.State.Noise ->
                            EarbudEvent.NoiseModeChanged(
                                state.mode.toDomainMode(),
                                acknowledged = true,
                            )
                    },
                )
            }
        }
    }

    override fun reset() {
        decoder.reset()
        sequence = 0
        handshakePublished = false
    }

    private fun queryStatus(): ByteArray =
        RoseBudsFeelMk2WireCodec.queryStatus(nextSequence())

    private fun nextSequence(): Int = sequence.also { sequence = (sequence + 1) and 0xFF }

    private fun NoiseMode.toWireMode(): RoseBudsFeelMk2WireCodec.NoiseMode = when (this) {
        NoiseMode.ANC -> RoseBudsFeelMk2WireCodec.NoiseMode.ANC
        NoiseMode.OFF -> RoseBudsFeelMk2WireCodec.NoiseMode.OFF
        NoiseMode.TRANSPARENCY -> RoseBudsFeelMk2WireCodec.NoiseMode.TRANSPARENCY
        NoiseMode.WIND -> RoseBudsFeelMk2WireCodec.NoiseMode.WIND
    }

    private fun RoseBudsFeelMk2WireCodec.NoiseMode.toDomainMode(): NoiseMode = when (this) {
        RoseBudsFeelMk2WireCodec.NoiseMode.ANC -> NoiseMode.ANC
        RoseBudsFeelMk2WireCodec.NoiseMode.OFF -> NoiseMode.OFF
        RoseBudsFeelMk2WireCodec.NoiseMode.TRANSPARENCY -> NoiseMode.TRANSPARENCY
        RoseBudsFeelMk2WireCodec.NoiseMode.WIND -> NoiseMode.WIND
    }
}
