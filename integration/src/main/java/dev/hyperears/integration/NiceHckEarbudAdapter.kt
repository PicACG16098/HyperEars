package dev.hyperears.integration

import dev.hyperears.protocol.nicehck.NiceHckWireCodec

/** Standard-behavior fallback for recognizable NiceHCK/YuanDao headsets. */
open class NiceHckEarbudAdapter : StandardEarbudAdapter() {
    override val id: String = ID
    override val displayName: String = "NiceHCK headset"
    override val resolution: AdapterResolution = AdapterResolution.FAMILY_MATCH
    override val controlApps: List<ControlAppSpec> = listOf(ControlAppCatalog.yuanDao)

    override fun matches(identity: EarbudIdentity): Boolean {
        if (identity.nativeSystemEarbud) return false
        val name = normalizeDeviceName(identity.deviceName.orEmpty())
        return name.contains("nicehck") || name.contains("yuandao")
    }

    companion object {
        const val ID = "nicehck-family"
    }
}

class NiceHckYuanDaoOrigAdapter : NiceHckEarbudAdapter() {

    override val id: String = ID
    override val displayName: String = "NiceHCK YuanDao OriG in"
    override val resolution: AdapterResolution = AdapterResolution.EXACT_MATCH
    override val miLinkCardPresentationId: MiLinkCardPresentationId?
        get() = PRESENTATION_ID.takeIf { effectiveCapabilities().noiseControl }
    override val privateProtocolRequired: Boolean = true
    override val transportReadiness: TransportReadiness = TransportReadiness.PROTOCOL_HANDSHAKE
    override val transports: List<EarbudTransportSpec> = listOf(
        RfcommEndpointSpec.ServiceUuid(
            uuid = SPP_UUID,
            id = "nicehck-orig-rfcomm",
        ),
    )

    override fun matches(identity: EarbudIdentity): Boolean {
        if (identity.nativeSystemEarbud) return false
        val name = normalizeDeviceName(identity.deviceName.orEmpty())
        return name in ORIG_MODEL_NAMES
    }

    override fun createProtocolSession(): ProtocolSession = NiceHckOrigProtocolSession()

    companion object {
        const val ID = "nicehck-yuandao-orig-in"
        val PRESENTATION_ID = MiLinkCardPresentationId(ID)
        internal val SUPPORTED_NOISE_MODES = NoiseMode.entries.toSet()
        private const val SPP_UUID = "0000a100-1000-8000-4e48-434b4354524c"
        private val ORIG_MODEL_NAMES = setOf(
            "origin",
            "yuandaoorigin",
            "nicehckorigin",
            "nicehckyuandaoorigin",
        )
    }
}

private class NiceHckOrigProtocolSession : ProtocolSession {
    private val decoder = NiceHckWireCodec.Decoder()
    private var handshakeAccepted = false

    override fun initialReadCommands(): List<ByteArray> = listOf(
        NiceHckWireCodec.queryBattery,
        NiceHckWireCodec.queryNoiseMode,
    )

    override fun encode(request: ControlRequest): List<ByteArray> = when {
        request === StandardControlRequest.Refresh -> initialReadCommands()
        request is StandardControlRequest.SetNoiseMode -> listOf(
            NiceHckWireCodec.setNoiseMode(request.mode.toWireMode()),
        )

        else -> emptyList()
    }

    override fun readback(request: ControlRequest): List<ByteArray> = when {
        request === StandardControlRequest.Refresh -> emptyList()
        request is StandardControlRequest.SetNoiseMode -> listOf(NiceHckWireCodec.queryNoiseMode)
        else -> emptyList()
    }

    override fun offer(bytes: ByteArray): List<ProtocolEvent> = buildList {
        decoder.offer(bytes).forEach { frame ->
            val battery = NiceHckWireCodec.parseBattery(frame)
            val noiseMode = NiceHckWireCodec.parseNoiseMode(frame)
            if (!handshakeAccepted && (battery != null || noiseMode != null)) {
                handshakeAccepted = true
                add(ProtocolEvent.HandshakeAccepted)
            }
            battery?.let {
                add(ProtocolEvent.CapabilitiesIdentified(battery = true))
                add(
                    ProtocolEvent.FeatureStateChanged(
                        BatteryFeatureState(EarbudBattery(
                            left = BatteryReading(it.leftPercent, false),
                            right = BatteryReading(it.rightPercent, false),
                            case = BatteryReading(it.casePercent, false),
                        )),
                    ),
                )
            }
            noiseMode?.let { mode ->
                add(
                    ProtocolEvent.CapabilitiesIdentified(
                        battery = false,
                        noiseModes = NiceHckYuanDaoOrigAdapter.SUPPORTED_NOISE_MODES,
                    ),
                )
                add(
                    ProtocolEvent.FeatureStateChanged(
                        NoiseModeFeatureState(mode.toDomainMode()),
                    ),
                )
            }
        }
    }

    override fun reset() {
        decoder.reset()
        handshakeAccepted = false
    }

    private fun NoiseMode.toWireMode(): NiceHckWireCodec.NoiseMode = when (this) {
        NoiseMode.ANC -> NiceHckWireCodec.NoiseMode.ANC
        NoiseMode.OFF -> NiceHckWireCodec.NoiseMode.OFF
        NoiseMode.TRANSPARENCY -> NiceHckWireCodec.NoiseMode.TRANSPARENCY
        NoiseMode.WIND -> NiceHckWireCodec.NoiseMode.WIND
    }

    private fun NiceHckWireCodec.NoiseMode.toDomainMode(): NoiseMode = when (this) {
        NiceHckWireCodec.NoiseMode.OFF -> NoiseMode.OFF
        NiceHckWireCodec.NoiseMode.TRANSPARENCY -> NoiseMode.TRANSPARENCY
        NiceHckWireCodec.NoiseMode.ANC,
        NiceHckWireCodec.NoiseMode.DEEP_ANC,
        NiceHckWireCodec.NoiseMode.EXPERIMENTAL_ANC,
        -> NoiseMode.ANC

        NiceHckWireCodec.NoiseMode.WIND -> NoiseMode.WIND
    }
}
