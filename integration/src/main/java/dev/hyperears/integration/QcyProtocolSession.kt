package dev.hyperears.integration

import dev.hyperears.protocol.qcy.QcyWireCodec

/** One stateful QCY command conversation for one physical headset session. */
internal class QcyProtocolSession : ProtocolSession {
    private val decoder = QcyWireCodec.Decoder()
    private var handshakePublished = false

    override fun initialReadCommands(): List<ByteArray> = listOf(
        QcyWireCodec.queryBattery,
        QcyWireCodec.queryNoiseMode,
    )

    override fun encode(request: ControlRequest): List<ByteArray> = when {
        request === StandardControlRequest.Refresh -> initialReadCommands()
        request is StandardControlRequest.SetNoiseMode -> request.mode
            .toQcyMode()
            ?.let(QcyWireCodec::setNoiseMode)
            ?.let(::listOf)
            .orEmpty()

        else -> emptyList()
    }

    override fun readback(request: ControlRequest): List<ByteArray> = when (request) {
        is StandardControlRequest.SetNoiseMode -> listOf(QcyWireCodec.queryNoiseMode)
        else -> emptyList()
    }

    override fun offer(bytes: ByteArray): List<ProtocolEvent> = buildList {
        var accepted = false
        decoder.offer(bytes).forEach { frame ->
            QcyWireCodec.parseBattery(frame)?.let { battery ->
                accepted = true
                add(ProtocolEvent.CapabilitiesIdentified(battery = true))
                add(
                    ProtocolEvent.FeatureStateChanged(
                        BatteryFeatureState(
                            EarbudBattery(
                                left = battery.left.toBatteryReading(),
                                right = battery.right.toBatteryReading(),
                                case = battery.case.toBatteryReading(),
                            ),
                        ),
                    ),
                )
            }
            QcyWireCodec.parseNoiseMode(frame)?.let { mode ->
                accepted = true
                add(
                    ProtocolEvent.CapabilitiesIdentified(
                        battery = false,
                        noiseModes = THREE_STATE_NOISE_MODES,
                    ),
                )
                add(
                    ProtocolEvent.FeatureStateChanged(
                        NoiseModeFeatureState(mode.toDomainMode()),
                    ),
                )
            }
        }
        if (accepted && !handshakePublished) {
            handshakePublished = true
            add(ProtocolEvent.HandshakeAccepted)
        }
    }

    override fun reset() {
        decoder.reset()
        handshakePublished = false
    }

    private fun QcyWireCodec.BatteryCell.toBatteryReading(): BatteryReading =
        BatteryReading(percent = percent, charging = charging)

    private fun NoiseMode.toQcyMode(): QcyWireCodec.NoiseMode? = when (this) {
        NoiseMode.ANC -> QcyWireCodec.NoiseMode.ANC
        NoiseMode.OFF -> QcyWireCodec.NoiseMode.OFF
        NoiseMode.TRANSPARENCY -> QcyWireCodec.NoiseMode.TRANSPARENCY
        NoiseMode.WIND -> null
    }

    private fun QcyWireCodec.NoiseMode.toDomainMode(): NoiseMode = when (this) {
        QcyWireCodec.NoiseMode.OFF -> NoiseMode.OFF
        QcyWireCodec.NoiseMode.ANC,
        QcyWireCodec.NoiseMode.OUTDOOR,
        -> NoiseMode.ANC

        QcyWireCodec.NoiseMode.TRANSPARENCY -> NoiseMode.TRANSPARENCY
    }

    private companion object {
        val THREE_STATE_NOISE_MODES = setOf(
            NoiseMode.ANC,
            NoiseMode.OFF,
            NoiseMode.TRANSPARENCY,
        )
    }
}
