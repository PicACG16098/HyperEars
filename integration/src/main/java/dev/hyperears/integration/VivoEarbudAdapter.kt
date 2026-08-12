package dev.hyperears.integration

import dev.hyperears.protocol.vivo.VivoTwsProtocol

/**
 * Shared vivo/iQOO TWS family adapter.
 *
 * Public captures and the official vivo app agree on the GAIA vendor, battery command, three-state
 * noise command and mode values. The family owns the candidate protocol, but exposes private
 * capabilities only after a valid GAIA response. Concrete models override only their verified
 * GAIA version and trailing noise parameters.
 */
open class VivoEarbudAdapter : StandardEarbudAdapter() {
    override val id: String = ID
    override val displayName: String = "vivo / iQOO TWS"
    override val resolution: AdapterResolution = AdapterResolution.FAMILY_MATCH
    override val controlApps: List<ControlAppSpec> = listOf(ControlAppCatalog.vivoEarphones)
    override val transports: List<EarbudTransportSpec> = listOf(
        RfcommEndpointSpec.ServiceUuid(
            uuid = VIVO_GAIA_UUID,
            id = "vivo-gaia-0837",
        ),
    )
    protected open val protocolConfig: VivoTwsProtocol.WireConfig =
        VivoTwsProtocol.WireConfig.FAMILY_DEFAULT_V4

    override fun matches(identity: EarbudIdentity): Boolean =
        VivoRetailModelCatalog.isFamilyName(identity.deviceName)

    override val privateProtocolRequired: Boolean = true
    override val transportReadiness: TransportReadiness =
        TransportReadiness.PROTOCOL_HANDSHAKE
    override val supportedNoiseModes: Set<NoiseMode> = emptySet()

    override fun createProtocolSession(): ProtocolSession =
        VivoProtocolSession(config = protocolConfig)

    companion object {
        const val ID = "vivo-tws-family"
        const val VIVO_GAIA_UUID = "00000837-d102-11e1-9b23-00025b00a5a5"

        internal val THREE_STATE_NOISE_MODES = setOf(
            NoiseMode.ANC,
            NoiseMode.OFF,
            NoiseMode.TRANSPARENCY,
        )
    }
}

/** Exact vivo models whose documented wire configuration may be probed safely. */
abstract class VivoModelEarbudAdapter : VivoEarbudAdapter() {
    final override val resolution: AdapterResolution = AdapterResolution.EXACT_MATCH
}

/**
 * Concrete adapter for the currently verified vivo TWS Air3 Pro.
 */
class VivoTwsAir3ProAdapter : VivoModelEarbudAdapter() {

    override val id: String = ID
    override val displayName: String = "vivo TWS Air3 Pro"
    override val protocolConfig: VivoTwsProtocol.WireConfig =
        VivoTwsProtocol.WireConfig.AIR3_PRO_CAPTURED

    override fun matches(identity: EarbudIdentity): Boolean =
        normalizeDeviceName(identity.deviceName.orEmpty()) == "vivotwsair3pro"

    companion object {
        const val ID = "vivo-tws-air3-pro"
    }
}

/**
 * Concrete adapter for vivo TWS 3e.
 *
 * The v3 write shape and RFCOMM channel 13 are documented by ScrewVivoTWS. The service UUID is
 * still attempted first so normal SDP remains the preferred transport path.
 */
class VivoTws3eAdapter : VivoModelEarbudAdapter() {

    override val id: String = ID
    override val displayName: String = "vivo TWS 3e"
    override val protocolConfig: VivoTwsProtocol.WireConfig =
        VivoTwsProtocol.WireConfig.TWS_3E_V3
    override val transports: List<EarbudTransportSpec> =
        super.transports + RfcommEndpointSpec.Channel(number = 13)

    override fun matches(identity: EarbudIdentity): Boolean =
        normalizeDeviceName(identity.deviceName.orEmpty()) == "vivotws3e"

    companion object {
        const val ID = "vivo-tws-3e"
    }
}

private class VivoProtocolSession(
    private val config: VivoTwsProtocol.WireConfig,
) : ProtocolSession {
    private val decoder = VivoTwsProtocol.Decoder()
    private var handshakePublished = false

    override fun initialReadCommands(): List<ByteArray> = listOf(
        VivoTwsProtocol.handshake(),
        VivoTwsProtocol.queryNoiseMode(config),
        VivoTwsProtocol.queryBattery(),
    )

    override fun encode(request: ControlRequest): List<ByteArray> = when {
        request === StandardControlRequest.Refresh -> initialReadCommands()
        request is StandardControlRequest.SetNoiseMode -> listOf(
            VivoTwsProtocol.setNoiseMode(
                mode = request.mode.toProtocolMode(),
                configuration = config,
            ),
        )

        else -> emptyList()
    }

    override fun offer(bytes: ByteArray): List<ProtocolEvent> =
        decoder.offer(bytes).flatMap { frame ->
            VivoTwsProtocol.parseHandshakeState(frame)?.let {
                return@flatMap if (it.accepted) {
                    handshakePublished = true
                    listOf(ProtocolEvent.HandshakeAccepted)
                } else {
                    listOf(ProtocolEvent.HandshakeRejected)
                }
            }
            VivoTwsProtocol.parseBatteryState(frame)?.let {
                return@flatMap buildList {
                    add(ProtocolEvent.CapabilitiesIdentified(battery = true))
                    add(
                        ProtocolEvent.FeatureStateChanged(
                            BatteryFeatureState(EarbudBattery(
                                left = BatteryReading(it.leftPercent, it.leftCharging),
                                right = BatteryReading(it.rightPercent, it.rightCharging),
                                case = BatteryReading(it.casePercent, it.caseCharging),
                            )),
                        ),
                    )
                    publishHandshakeIfNeeded()
                }
            }
            VivoTwsProtocol.parseNoiseState(frame)?.let {
                return@flatMap buildList {
                    add(
                        ProtocolEvent.CapabilitiesIdentified(
                            battery = false,
                            noiseModes = VivoEarbudAdapter.THREE_STATE_NOISE_MODES,
                        ),
                    )
                    add(
                        ProtocolEvent.FeatureStateChanged(
                            NoiseModeFeatureState(it.mode.toDomainMode()),
                        ),
                    )
                    publishHandshakeIfNeeded()
                }
            }
            listOf(
                ProtocolEvent.UnknownFrame(
                    version = frame.version,
                    vendor = frame.vendor,
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

    private fun NoiseMode.toProtocolMode(): VivoTwsProtocol.NoiseMode = when (this) {
        NoiseMode.ANC -> VivoTwsProtocol.NoiseMode.ANC
        NoiseMode.OFF -> VivoTwsProtocol.NoiseMode.OFF
        NoiseMode.TRANSPARENCY -> VivoTwsProtocol.NoiseMode.TRANSPARENCY
        NoiseMode.WIND -> error("The selected vivo protocol configuration has no wind-noise mode")
    }

    private fun VivoTwsProtocol.NoiseMode.toDomainMode(): NoiseMode = when (this) {
        VivoTwsProtocol.NoiseMode.ANC -> NoiseMode.ANC
        VivoTwsProtocol.NoiseMode.OFF -> NoiseMode.OFF
        VivoTwsProtocol.NoiseMode.TRANSPARENCY -> NoiseMode.TRANSPARENCY
    }
}
