package dev.hyperears.integration

import dev.hyperears.protocol.edifier.EdifierWireCodec

/**
 * Shared Edifier (BES/恒玄) headset behavior.
 *
 * Family detection is passive: the Bluetooth name is read from the already-connected system
 * device. The private channel then queries device capabilities and battery through Edifier's
 * proprietary SPP protocol.
 */
open class EdifierEarbudAdapter : StandardEarbudAdapter() {
    override val id: String = ID
    override val displayName: String = "Edifier headset"
    override val privateProtocolRequired: Boolean = true
    override val transportReadiness: TransportReadiness =
        TransportReadiness.PROTOCOL_HANDSHAKE
    override val batterySource: BatterySource = BatterySource.PRIVATE_PROTOCOL
    override val capabilities: EarbudCapabilities = super.capabilities.copy(battery = false)
    protected open val wireConfig: EdifierWireConfig = EdifierWireConfig()
    override val transports: List<EarbudTransportSpec> = listOf(
        RfcommEndpointSpec.ServiceUuid(
            uuid = EDF_SPP_UUID,
            id = "edifier-spp-uuid",
        ),
        RfcommEndpointSpec.Channel(number = 1),
    )

    override fun matches(identity: EarbudIdentity): Boolean {
        if (!identity.standardHeadset || identity.nativeSystemEarbud) return false
        val name = normalizeDeviceName(identity.deviceName.orEmpty())
        val advertisedService = identity.serviceUuids.any {
            it.equals(EDF_SPP_UUID, ignoreCase = true)
        }
        return advertisedService || EDIFIER_NAME_MARKERS.any(name::contains)
    }

    override fun createProtocolSession(): ProtocolSession =
        EdifierProtocolSession(wireConfig)

    companion object {
        const val ID = "edifier-family"
        const val EDF_SPP_UUID = "EDF00000-EDFE-DFED-FEDF-EDFEDFEDFEDF"

        private val EDIFIER_NAME_MARKERS = setOf(
            "edifier",
            "漫步者",
            "w860nb",
            "w820nb",
            "w830nb",
        )
    }
}

/**
 * Edifier over-ear headphones family.
 *
 * The W860NB PRO is a headphones form factor. Bluetooth device class or name markers
 * distinguish headphones from TWS earbuds.
 */
open class EdifierHeadphonesAdapter : EdifierEarbudAdapter() {
    override val id: String = ID
    override val displayName: String = "Edifier headphones"
    override val formFactor: HeadsetFormFactor = HeadsetFormFactor.HEADPHONES

    override fun matches(identity: EarbudIdentity): Boolean =
        super.matches(identity) && (
            identity.bluetoothDeviceClass == BLUETOOTH_DEVICE_CLASS_HEADPHONES ||
                normalizeDeviceName(identity.deviceName.orEmpty()).let { name ->
                    HEADPHONE_MARKERS.any(name::contains)
                }
        )

    companion object {
        const val ID = "edifier-headphones-family"

        // android.bluetooth.BluetoothClass.Device.AUDIO_VIDEO_HEADPHONES
        const val BLUETOOTH_DEVICE_CLASS_HEADPHONES = 0x0418

        private val HEADPHONE_MARKERS = setOf(
            "w860nb",
            "w820nb",
            "w830nb",
            "stax",
        )
    }
}

/**
 * Concrete model adapter for Edifier W860NB PRO.
 *
 * Selected by exact normalized Bluetooth name match. The private protocol queries
 * device capabilities (D8) and battery level after connection.
 */
class EdifierW860NBProAdapter : EdifierHeadphonesAdapter() {

    override val id: String = ID
    override val displayName: String = "Edifier W860NB PRO"
    override val resolution: AdapterResolution = AdapterResolution.EXACT_MATCH
    override val miLinkCardPresentationId: MiLinkCardPresentationId = PRESENTATION_ID
    override val wireConfig: EdifierWireConfig = EdifierWireConfig(
        preverifiedAncIndex = EdifierWireCodec.ANC_INDEX,
    )
    override val capabilities: EarbudCapabilities = EarbudCapabilities(
        battery = true,
        noiseControl = true,
        windNoiseControl = true,
        audioHandoff = true,
    )
    override val supportedNoiseModes: Set<NoiseMode> = setOf(
        NoiseMode.ANC,
        NoiseMode.TRANSPARENCY,
        NoiseMode.WIND,
        NoiseMode.OFF,
    )
    override val noiseControlConfirmation: ControlConfirmationPolicy =
        ControlConfirmationPolicy.PUBLISH_AFTER_WRITE

    /**
     * The W860NB PRO plays a voice prompt for ~1.9 s after an ANC switch and ignores commands
     * during the prompt. Refuse new switches in the MiLink hook so the UI stays on the current
     * mode instead of jumping to one the headset never applied.
     */
    override val ancSwitchCooldownMs: Long = 1_800L

    override fun matches(identity: EarbudIdentity): Boolean =
        super.matches(identity) &&
            normalizeDeviceName(identity.deviceName.orEmpty()) == "edifierw860nbpro"

    companion object {
        const val ID = "edifier-w860nb-pro"
        val PRESENTATION_ID = MiLinkCardPresentationId(ID)
    }
}

/** Wire facts known before a session starts; family candidates intentionally leave them unset. */
data class EdifierWireConfig(
    val preverifiedAncIndex: Int? = null,
)

/**
 * Edifier private protocol state machine.
 *
 * Uses the BES/恒玄 SPP framing to query battery, ANC state, and device capabilities.
 * Frame format: [0xBB/0xCC][APP_CODE][CMD][LEN_H][LEN_L][PAYLOAD...][CRC8]
 */
private class EdifierProtocolSession(
    private val configuration: EdifierWireConfig,
) : ProtocolSession {
    private val decoder = EdifierWireCodec.Decoder()
    private var handshakePublished = false
    private var activeAncIndex: Int? = configuration.preverifiedAncIndex

    override fun initialReadCommands(): List<ByteArray> = listOf(
        EdifierWireCodec.queryBattery,
        EdifierWireCodec.queryAnc,
        EdifierWireCodec.queryFunction,
    )

    override fun encode(request: ControlRequest): List<ByteArray> = when (request) {
        ControlRequest.Refresh -> listOf(
            EdifierWireCodec.queryBattery,
            EdifierWireCodec.queryAnc,
        )
        is ControlRequest.SetNoiseMode -> {
            val ancValue = request.mode.toEdifierAncValue()
            val ancIndex = activeAncIndex
            if (ancValue != null && ancIndex != null) {
                listOf(EdifierWireCodec.setAnc(ancValue = ancValue, ancIndex = ancIndex))
            } else {
                emptyList()
            }
        }
    }

    override fun readback(request: ControlRequest): List<ByteArray> = when (request) {
        // The W860NB PRO executes ANC writes immediately and reports state via the write
        // acknowledgement. Skip the extra readback round-trip to reduce perceived latency.
        ControlRequest.Refresh -> emptyList()
        is ControlRequest.SetNoiseMode -> emptyList()
    }

    override fun offer(bytes: ByteArray): List<ProtocolEvent> = buildList {
        decoder.offer(bytes).forEach { frame ->
            EdifierWireCodec.parseBatteryState(frame)?.let { battery ->
                add(ProtocolEvent.CapabilitiesIdentified(battery = true))
                add(
                    ProtocolEvent.BatteryChanged(
                        EarbudBattery(
                            overall = BatteryReading(battery.wholeUnit, charging = false),
                        ),
                    ),
                )
                publishHandshakeIfNeeded()
                return@forEach
            }

            EdifierWireCodec.parseAncState(frame)?.let { anc ->
                // anc.mode = ancIndex (0x10), anc.level = ancValue (1-5)
                activeAncIndex = anc.mode
                val mode = anc.level?.toNoiseMode()
                if (mode != null) {
                    add(
                        ProtocolEvent.CapabilitiesIdentified(
                            battery = false,
                            noiseModes = EDIFIER_NOISE_MODES,
                        ),
                    )
                    add(ProtocolEvent.NoiseModeChanged(mode))
                }
                publishHandshakeIfNeeded()
                return@forEach
            }

            // Function query response (D8) — confirms device capabilities
            if (
                frame.commandIndex == EdifierWireCodec.CMD_FUNCTION_QUERY &&
                EdifierWireCodec.isProtocolResponse(frame)
            ) {
                // This proves the BES command family only. It does not itself prove that a
                // battery or ANC command is implemented by this particular headset.
                publishHandshakeIfNeeded()
                return@forEach
            }

            add(
                ProtocolEvent.UnknownFrame(
                    version = 0,
                    vendor = 0,
                    command = frame.commandIndex,
                    payloadSize = frame.payload.size,
                ),
            )
        }
    }

    override fun reset() {
        decoder.reset()
        handshakePublished = false
        activeAncIndex = configuration.preverifiedAncIndex
    }

    private fun MutableList<ProtocolEvent>.publishHandshakeIfNeeded() {
        if (handshakePublished) return
        handshakePublished = true
        add(ProtocolEvent.HandshakeAccepted)
    }

    private fun NoiseMode.toEdifierAncValue(): Int? = when (this) {
        // W860NB PRO uses ANC16 slot; 1=depth, 2=comfort, 3=wind, 4=ambient, 5=off
        NoiseMode.ANC -> EdifierWireCodec.ANC_VALUE_DEEP
        NoiseMode.WIND -> EdifierWireCodec.ANC_VALUE_WIND
        NoiseMode.TRANSPARENCY -> EdifierWireCodec.ANC_VALUE_AMBIENT
        NoiseMode.OFF -> EdifierWireCodec.ANC_VALUE_OFF
    }

    /**
     * Map Edifier ANC response mode byte to HyperEars NoiseMode.
     *
     * Verified on W860NB PRO hardware:
     * - 1: 深度降噪 (deep NC) -> ANC
     * - 2: 舒适降噪 (comfort NC) -> ANC
     * - 3: 防风噪 (wind noise) -> WIND
     * - 4: 环境声 (ambient/transparency) -> TRANSPARENCY
     * - 5: 降噪关 (NC off) -> OFF
     */
    private fun Int.toNoiseMode(): NoiseMode? = when (this) {
        EdifierWireCodec.ANC_VALUE_DEEP,
        EdifierWireCodec.ANC_VALUE_COMFORT,
        -> NoiseMode.ANC

        EdifierWireCodec.ANC_VALUE_WIND -> NoiseMode.WIND
        EdifierWireCodec.ANC_VALUE_AMBIENT -> NoiseMode.TRANSPARENCY
        EdifierWireCodec.ANC_VALUE_OFF -> NoiseMode.OFF
        else -> null
    }

    private companion object {
        val EDIFIER_NOISE_MODES = setOf(
            NoiseMode.ANC,
            NoiseMode.OFF,
            NoiseMode.TRANSPARENCY,
            NoiseMode.WIND,
        )
    }
}
