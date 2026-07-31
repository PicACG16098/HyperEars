package dev.hyperears.integration

import dev.hyperears.protocol.vivo.VivoTwsProtocol

/**
 * Shared vivo/iQOO TWS family adapter.
 *
 * Public captures and the official vivo app agree on the GAIA vendor, battery command, three-state
 * noise command and mode values. The family therefore owns those shared capabilities. Concrete
 * models override only their verified GAIA version and trailing noise parameters.
 */
open class VivoEarbudAdapter : StandardEarbudAdapter() {
    override val id: String = ID
    override val displayName: String = "vivo / iQOO TWS"
    override val endpoints: List<RfcommEndpointSpec> = listOf(
        RfcommEndpointSpec.ServiceUuid(
            uuid = VIVO_GAIA_UUID,
            id = "vivo-gaia-0837",
        ),
    )
    protected open val protocolProfile: VivoTwsProtocol.Profile =
        VivoTwsProtocol.Profile.FAMILY_DEFAULT_V4

    override fun matches(identity: EarbudIdentity): Boolean =
        VivoRetailModelCatalog.isFamilyName(identity.deviceName)

    override val privateProtocolRequired: Boolean = true
    override val batterySource: BatterySource = BatterySource.PRIVATE_PROTOCOL
    override val supportedNoiseModes: Set<NoiseMode> = THREE_STATE_NOISE_MODES
    override val capabilities: EarbudCapabilities =
        super.capabilities.copy(noiseControl = true)

    override fun createProtocol(): EarbudProtocol =
        VivoEarbudProtocol(profile = protocolProfile)

    companion object {
        const val ID = "vivo-tws-family"
        const val VIVO_GAIA_UUID = "00000837-d102-11e1-9b23-00025b00a5a5"

        private val THREE_STATE_NOISE_MODES = setOf(
            NoiseMode.ANC,
            NoiseMode.OFF,
            NoiseMode.TRANSPARENCY,
        )
    }
}

/**
 * Concrete adapter for the currently verified vivo TWS Air3 Pro.
 */
object VivoTwsAir3ProAdapter : VivoEarbudAdapter() {
    const val ID = "vivo-tws-air3-pro"

    override val id: String = ID
    override val displayName: String = "vivo TWS Air3 Pro"
    override val protocolProfile: VivoTwsProtocol.Profile =
        VivoTwsProtocol.Profile.AIR3_PRO_CAPTURED

    override fun matches(identity: EarbudIdentity): Boolean =
        normalizeDeviceName(identity.deviceName.orEmpty()) == "vivotwsair3pro"
}

/**
 * Concrete adapter for vivo TWS 3e.
 *
 * The v3 write shape and RFCOMM channel 13 are documented by ScrewVivoTWS. The service UUID is
 * still attempted first so normal SDP remains the preferred transport path.
 */
object VivoTws3eAdapter : VivoEarbudAdapter() {
    const val ID = "vivo-tws-3e"

    override val id: String = ID
    override val displayName: String = "vivo TWS 3e"
    override val protocolProfile: VivoTwsProtocol.Profile =
        VivoTwsProtocol.Profile.TWS_3E_V3
    override val endpoints: List<RfcommEndpointSpec> =
        super.endpoints + RfcommEndpointSpec.Channel(number = 13)

    override fun matches(identity: EarbudIdentity): Boolean =
        normalizeDeviceName(identity.deviceName.orEmpty()) == "vivotws3e"
}

private class VivoEarbudProtocol(
    private val profile: VivoTwsProtocol.Profile,
) : EarbudProtocol {
    private val decoder = VivoTwsProtocol.Decoder()

    override fun initialReadCommands(): List<ByteArray> = listOf(
        VivoTwsProtocol.handshake(),
        VivoTwsProtocol.queryNoiseMode(profile),
        VivoTwsProtocol.queryBattery(),
    )

    override fun encode(request: ControlRequest): List<ByteArray> = when (request) {
        ControlRequest.Refresh -> initialReadCommands()
        is ControlRequest.SetNoiseMode -> listOf(
            VivoTwsProtocol.setNoiseMode(
                mode = request.mode.toProtocolMode(),
                profile = profile,
            ),
        )
    }

    override fun offer(bytes: ByteArray): List<EarbudEvent> =
        decoder.offer(bytes).map { frame ->
            VivoTwsProtocol.parseHandshakeState(frame)?.let {
                return@map EarbudEvent.Handshake(it.accepted)
            }
            VivoTwsProtocol.parseBatteryState(frame)?.let {
                return@map EarbudEvent.BatteryChanged(
                    EarbudBattery(
                        left = BatteryReading(it.leftPercent, it.leftCharging),
                        right = BatteryReading(it.rightPercent, it.rightCharging),
                        case = BatteryReading(it.casePercent, it.caseCharging),
                    ),
                )
            }
            VivoTwsProtocol.parseNoiseState(frame)?.let {
                return@map EarbudEvent.NoiseModeChanged(
                    mode = it.mode.toDomainMode(),
                    acknowledged = it.acknowledged,
                )
            }
            EarbudEvent.UnknownFrame(
                version = frame.version,
                vendor = frame.vendor,
                command = frame.command,
                payloadSize = frame.payload.size,
            )
        }

    override fun reset() {
        decoder.reset()
    }

    private fun NoiseMode.toProtocolMode(): VivoTwsProtocol.NoiseMode = when (this) {
        NoiseMode.ANC -> VivoTwsProtocol.NoiseMode.ANC
        NoiseMode.OFF -> VivoTwsProtocol.NoiseMode.OFF
        NoiseMode.TRANSPARENCY -> VivoTwsProtocol.NoiseMode.TRANSPARENCY
        NoiseMode.WIND -> error("The selected vivo protocol profile has no wind-noise mode")
    }

    private fun VivoTwsProtocol.NoiseMode.toDomainMode(): NoiseMode = when (this) {
        VivoTwsProtocol.NoiseMode.ANC -> NoiseMode.ANC
        VivoTwsProtocol.NoiseMode.OFF -> NoiseMode.OFF
        VivoTwsProtocol.NoiseMode.TRANSPARENCY -> NoiseMode.TRANSPARENCY
    }
}
