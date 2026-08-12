package dev.hyperears.integration

import dev.hyperears.protocol.starring.StarRingWireCodec

/**
 * Shared StarRing family behavior.
 *
 * Unknown family members retain Android's standard headset behavior. Private transports are
 * opened only by a concrete model adapter whose command set has been verified.
 */
open class StarRingEarbudAdapter : StandardEarbudAdapter() {
    override val id: String = ID
    override val displayName: String = "StarRing headset"
    override val resolution: AdapterResolution = AdapterResolution.FAMILY_MATCH
    override val controlApps: List<ControlAppSpec> = listOf(ControlAppCatalog.lightYear)

    override fun matches(identity: EarbudIdentity): Boolean {
        val name = normalizeDeviceName(identity.deviceName.orEmpty())
        return name.startsWith("starring") || name.startsWith("lightyear")
    }

    companion object {
        const val ID = "starring-family"
    }
}

/** Concrete adapter for the captured StarRing Ultra protocol. */
class StarRingUltraAdapter : StarRingEarbudAdapter() {

    override val id: String = ID
    override val displayName: String = "StarRing Ultra"
    override val resolution: AdapterResolution = AdapterResolution.EXACT_MATCH
    override val miLinkCardPresentationId: MiLinkCardPresentationId?
        get() = PRESENTATION_ID.takeIf { effectiveCapabilities().noiseControl }
    override val privateProtocolRequired: Boolean = true
    override val transportReadiness: TransportReadiness =
        TransportReadiness.PROTOCOL_HANDSHAKE
    override val transports: List<EarbudTransportSpec> = listOf(
        GattTransportSpec(
            writeCharacteristicUuid = WRITE_CHARACTERISTIC_UUID,
            notifyCharacteristicUuid = NOTIFY_CHARACTERISTIC_UUID,
            writeInstanceId = 0xA102,
            notifyInstanceId = 0xA105,
            id = "starring-official-gatt",
        ),
        RfcommEndpointSpec.Channel(number = 28),
        RfcommEndpointSpec.Channel(number = 28, secure = false),
        RfcommEndpointSpec.ServiceUuid(
            uuid = STANDARD_SPP_UUID,
            id = "starring-spp-uuid",
        ),
        RfcommEndpointSpec.Channel(number = 5),
    )

    override fun matches(identity: EarbudIdentity): Boolean =
        normalizeDeviceName(identity.deviceName.orEmpty()) == "starringultra"

    override fun createProtocolSession(): ProtocolSession = StarRingUltraProtocolSession()

    companion object {
        const val ID = "starring-ultra"
        val PRESENTATION_ID = MiLinkCardPresentationId(ID)
        internal val SUPPORTED_NOISE_MODES = NoiseMode.entries.toSet()
        private const val STANDARD_SPP_UUID = "00001101-0000-1000-8000-00805F9B34FB"
        private const val WRITE_CHARACTERISTIC_UUID =
            "00007777-0000-1000-8000-00805F9B34FB"
        private const val NOTIFY_CHARACTERISTIC_UUID =
            "00008888-0000-1000-8000-00805F9B34FB"
    }
}

private class StarRingUltraProtocolSession : ProtocolSession {
    private val decoder = StarRingWireCodec.Decoder()
    private var handshakePublished = false

    override fun initialReadCommands(): List<ByteArray> = listOf(
        StarRingWireCodec.queryNoiseMode,
        StarRingWireCodec.queryBattery,
    )

    override fun encode(request: ControlRequest): List<ByteArray> = when {
        request === StandardControlRequest.Refresh -> initialReadCommands()
        request is StandardControlRequest.SetNoiseMode -> listOf(
            StarRingWireCodec.setNoiseMode(request.mode.toProtocolMode()),
        )

        else -> emptyList()
    }

    override fun readback(request: ControlRequest): List<ByteArray> = emptyList()

    override fun offer(bytes: ByteArray): List<ProtocolEvent> =
        decoder.offer(bytes).flatMap { frame ->
            StarRingWireCodec.parseBatteryState(frame)?.let {
                return@flatMap buildList<ProtocolEvent> {
                    add(ProtocolEvent.CapabilitiesIdentified(battery = true))
                    add(
                        ProtocolEvent.FeatureStateChanged(
                            BatteryFeatureState(EarbudBattery(
                                left = BatteryReading(it.leftPercent, charging = false),
                                right = BatteryReading(it.rightPercent, charging = false),
                                case = BatteryReading(it.casePercent, charging = false),
                            )),
                        ),
                    )
                    publishHandshakeIfNeeded()
                }
            }
            StarRingWireCodec.parseNoiseState(frame)?.let {
                return@flatMap buildList<ProtocolEvent> {
                    add(
                        ProtocolEvent.CapabilitiesIdentified(
                            battery = false,
                            noiseModes = StarRingUltraAdapter.SUPPORTED_NOISE_MODES,
                        ),
                    )
                    add(ProtocolEvent.FeatureStateChanged(NoiseModeFeatureState(it.mode.toDomainMode())))
                    publishHandshakeIfNeeded()
                }
            }
            listOf<ProtocolEvent>(
                ProtocolEvent.UnknownFrame(
                    version = 0,
                    vendor = frame.group,
                    command = frame.command,
                    payloadSize = frame.payload.size,
                ),
            )
        }

    override fun reset() {
        decoder.reset()
        handshakePublished = false
    }

    private fun MutableList<ProtocolEvent>.publishHandshakeIfNeeded() {
        if (handshakePublished) return
        handshakePublished = true
        add(ProtocolEvent.HandshakeAccepted)
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
