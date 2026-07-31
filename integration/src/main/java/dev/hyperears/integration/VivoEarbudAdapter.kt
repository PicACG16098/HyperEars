package dev.hyperears.integration

import dev.hyperears.protocol.vivo.VivoTwsProtocol

/**
 * Shared vivo TWS family adapter.
 *
 * It contributes only family traits that are common and safe to inherit. Unknown vivo models are
 * integrated through the standard identity, system battery and audio-handoff behavior without
 * opening the unverified vivo private channel.
 */
open class VivoEarbudAdapter : StandardEarbudAdapter() {
    override val id: String = ID
    override val displayName: String = "vivo TWS"
    override val endpoints: List<RfcommEndpointSpec> = listOf(
        RfcommEndpointSpec.ServiceUuid(
            uuid = VIVO_GAIA_UUID,
            id = "vivo-gaia-0837",
        ),
    )

    override fun matches(identity: EarbudIdentity): Boolean =
        normalizeDeviceName(identity.deviceName.orEmpty()).startsWith("vivotws")

    companion object {
        const val ID = "vivo-tws-family"
        const val VIVO_GAIA_UUID = "00000837-d102-11e1-9b23-00025b00a5a5"
    }
}

/**
 * Concrete adapter for the currently verified vivo TWS Air3 Pro.
 */
object VivoTwsAir3ProAdapter : VivoEarbudAdapter() {
    const val ID = "vivo-tws-air3-pro"

    override val id: String = ID
    override val displayName: String = "vivo TWS Air3 Pro"
    override val privateProtocolRequired: Boolean = true
    override val batterySource: BatterySource = BatterySource.PRIVATE_PROTOCOL
    override val capabilities: EarbudCapabilities = super.capabilities.copy(
        noiseControl = true,
    )
    override val supportedNoiseModes: Set<NoiseMode> = setOf(
        NoiseMode.ANC,
        NoiseMode.OFF,
        NoiseMode.TRANSPARENCY,
    )

    override fun matches(identity: EarbudIdentity): Boolean =
        normalizeDeviceName(identity.deviceName.orEmpty()) == "vivotwsair3pro"

    override fun createProtocol(): EarbudProtocol = VivoTwsAir3ProEarbudProtocol()
}

private class VivoTwsAir3ProEarbudProtocol : EarbudProtocol {
    private val decoder = VivoTwsProtocol.Decoder()

    override fun initialReadCommands(): List<ByteArray> = listOf(
        VivoTwsProtocol.handshake(),
        VivoTwsProtocol.queryNoiseMode(VivoTwsProtocol.Variant.AIR3_PRO_CAPTURED),
        VivoTwsProtocol.queryBattery(),
    )

    override fun encode(request: ControlRequest): List<ByteArray> = when (request) {
        ControlRequest.Refresh -> initialReadCommands()
        is ControlRequest.SetNoiseMode -> listOf(
            VivoTwsProtocol.setNoiseMode(
                mode = request.mode.toProtocolMode(),
                variant = VivoTwsProtocol.Variant.AIR3_PRO_CAPTURED,
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
        NoiseMode.WIND -> error("vivo TWS Air3 Pro does not expose a wind-noise mode")
    }

    private fun VivoTwsProtocol.NoiseMode.toDomainMode(): NoiseMode = when (this) {
        VivoTwsProtocol.NoiseMode.ANC -> NoiseMode.ANC
        VivoTwsProtocol.NoiseMode.OFF -> NoiseMode.OFF
        VivoTwsProtocol.NoiseMode.TRANSPARENCY -> NoiseMode.TRANSPARENCY
    }
}
