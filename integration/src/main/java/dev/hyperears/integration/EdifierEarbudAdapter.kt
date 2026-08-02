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
    override val batterySource: BatterySource = BatterySource.PRIVATE_PROTOCOL
    override val transports: List<EarbudTransportSpec> = listOf(
        RfcommEndpointSpec.ServiceUuid(
            uuid = EDF_SPP_UUID,
            id = "edifier-spp-uuid",
        ),
        RfcommEndpointSpec.Channel(number = 1),
    )

    /** ANC slot index used by this model. W860NB PRO = 0x10; Evo Pro = 0x1B. */
    open val ancIndex: Int = EdifierWireCodec.ANC_INDEX

    /** When true, battery is read from the 0xF2 device-state push instead of a D0 response. */
    open val batteryFromDeviceState: Boolean = false

    override fun matches(identity: EarbudIdentity): Boolean {
        if (!identity.standardHeadset || identity.nativeSystemEarbud) return false
        val name = normalizeDeviceName(identity.deviceName.orEmpty())
        return EDIFIER_NAME_MARKERS.any(name::contains)
    }

    override fun createProtocol(): EarbudProtocol =
        EdifierEarbudProtocol(
            ancIndex = ancIndex,
            batteryFromDeviceState = batteryFromDeviceState,
        )

    companion object {
        const val ID = "edifier-family"
        const val EDF_SPP_UUID = "EDF00000-EDFE-DFED-FEDF-EDFEDFEDFEDF"

        private val EDIFIER_NAME_MARKERS = setOf(
            "edifier",
            "w860nb",
            "w820nb",
            "w830nb",
            "evopro",
            "花再",
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
object EdifierW860NBProAdapter : EdifierHeadphonesAdapter() {
    const val ID = "edifier-w860nb-pro"
    val PRESENTATION_ID = MiLinkCardPresentationId(ID)

    override val id: String = ID
    override val displayName: String = "Edifier W860NB PRO"
    override val miLinkCardPresentationId: MiLinkCardPresentationId = PRESENTATION_ID
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
}

/**
 * Concrete model adapter for Edifier 花再 Evo Pro.
 *
 * Evo Pro is a TWS earbud using the same BES SPP framing and XOR 0xA5 encryption, but with a
 * different ANC slot (0x1B vs 0x10) and battery delivered via the 0xF2 device-state push
 * instead of a D0 query response.
 */
object EdifierEvoProAdapter : EdifierEarbudAdapter() {
    const val ID = "edifier-evo-pro"
    val PRESENTATION_ID = MiLinkCardPresentationId(ID)

    override val id: String = ID
    override val displayName: String = "Edifier 花再 Evo Pro"
    override val miLinkCardPresentationId: MiLinkCardPresentationId = PRESENTATION_ID

    /** Evo Pro uses ANC slot 0x1B (27), not W860NB PRO's 0x10. */
    override val ancIndex: Int = 0x1B

    /** Evo Pro reports battery via the 0xF2 device-state push, not a D0 response. */
    override val batteryFromDeviceState: Boolean = true

    override val capabilities: EarbudCapabilities = EarbudCapabilities(
        battery = true,
        noiseControl = true,
        audioHandoff = true,
    )
    override val supportedNoiseModes: Set<NoiseMode> = setOf(
        NoiseMode.ANC,
        NoiseMode.TRANSPARENCY,
        NoiseMode.OFF,
    )
    override val noiseControlConfirmation: ControlConfirmationPolicy =
        ControlConfirmationPolicy.PUBLISH_AFTER_WRITE

    override fun matches(identity: EarbudIdentity): Boolean {
        if (!super.matches(identity)) return false
        val name = normalizeDeviceName(identity.deviceName.orEmpty())
        return EVO_PRO_MARKERS.any(name::contains)
    }

    private val EVO_PRO_MARKERS = setOf(
        "evopro",
        "花再evopro",
        "花再",
    )
}

/**
 * Edifier private protocol state machine.
 *
 * Uses the BES/恒玄 SPP framing to query battery, ANC state, and device capabilities.
 * Frame format: [0xBB/0xCC][APP_CODE][CMD][LEN_H][LEN_L][PAYLOAD...][CRC8]
 */
private class EdifierEarbudProtocol(
    /** ANC slot index. W860NB PRO = 0x10; Evo Pro = 0x1B. */
    private val ancIndex: Int = EdifierWireCodec.ANC_INDEX,
    /** When true, battery is read from the 0xF2 device-state push instead of a D0 response. */
    private val batteryFromDeviceState: Boolean = false,
) : EarbudProtocol {
    private val decoder = EdifierWireCodec.Decoder()
    private var functionQueried = false

    override fun initialReadCommands(): List<ByteArray> = buildList {
        add(EdifierWireCodec.queryBattery)
        add(EdifierWireCodec.queryAnc)
        add(EdifierWireCodec.queryFunction)
        // Evo Pro pushes battery via 0xF2; a D0 query gets no response, so also poll device state.
        if (batteryFromDeviceState) add(EdifierWireCodec.queryDeviceState)
    }

    override fun encode(request: ControlRequest): List<ByteArray> = when (request) {
        ControlRequest.Refresh -> buildList {
            add(EdifierWireCodec.queryBattery)
            add(EdifierWireCodec.queryAnc)
            if (batteryFromDeviceState) add(EdifierWireCodec.queryDeviceState)
        }
        is ControlRequest.SetNoiseMode -> {
            val ancValue = request.mode.toEdifierAncValue()
            if (ancValue != null) {
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

    override fun offer(bytes: ByteArray): List<EarbudEvent> = buildList {
        decoder.offer(bytes).forEach { frame ->
            // Battery via D0 response (W860NB PRO), or 0xF2 device-state push (Evo Pro).
            val batteryAllowed = !batteryFromDeviceState ||
                frame.commandIndex == EdifierWireCodec.CMD_DEVICE_STATE_QUERY
            if (batteryAllowed) {
                EdifierWireCodec.parseBatteryState(frame)?.let { battery ->
                    add(
                        EarbudEvent.BatteryChanged(
                            EarbudBattery(
                                overall = BatteryReading(battery.wholeUnit, charging = false),
                            ),
                        ),
                    )
                    return@forEach
                }
            }

            EdifierWireCodec.parseAncState(frame)?.let { anc ->
                // anc.mode = ancIndex (0x10), anc.level = ancValue (1-5)
                val mode = anc.level?.toNoiseMode()
                if (mode != null) {
                    add(EarbudEvent.NoiseModeChanged(mode, acknowledged = true))
                }
                return@forEach
            }

            // Function query response (D8) — confirms device capabilities
            if (frame.commandIndex == EdifierWireCodec.CMD_FUNCTION_QUERY) {
                functionQueried = true
                add(EarbudEvent.Handshake(accepted = true))
                return@forEach
            }

            add(
                EarbudEvent.UnknownFrame(
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
        functionQueried = false
    }

    private fun NoiseMode.toEdifierAncValue(): Int? {
        // Evo Pro uses a shifted ANC value mapping (ancIndex=0x1B)
        if (ancIndex == 0x1B) {
            return when (this) {
                NoiseMode.ANC -> 1
                NoiseMode.WIND -> 4
                NoiseMode.TRANSPARENCY -> 5
                NoiseMode.OFF -> 6
            }
        }
        // W860NB PRO uses ANC16 slot; 1=depth, 2=comfort, 3=wind, 4=ambient, 5=off
        return when (this) {
            NoiseMode.ANC -> EdifierWireCodec.ANC_VALUE_DEEP
            NoiseMode.WIND -> EdifierWireCodec.ANC_VALUE_WIND
            NoiseMode.TRANSPARENCY -> EdifierWireCodec.ANC_VALUE_AMBIENT
            NoiseMode.OFF -> EdifierWireCodec.ANC_VALUE_OFF
        }
    }

    /**
     * Map Edifier ANC response mode byte to HyperEars NoiseMode.
     *
     * Verified on W860NB PRO hardware (ancIndex=0x10):
     * - 1: 深度降噪 (deep NC) -> ANC
     * - 2: 舒适降噪 (comfort NC) -> ANC
     * - 3: 防风噪 (wind noise) -> WIND
     * - 4: 环境声 (ambient/transparency) -> TRANSPARENCY
     * - 5: 降噪关 (NC off) -> OFF
     *
     * Evo Pro (ancIndex=0x1B) uses a shifted mapping:
     * - 1: 降噪 -> ANC
     * - 2: 降噪(舒适) -> ANC
     * - 3: 防风噪 -> WIND
     * - 5: 环境声/通透 -> TRANSPARENCY
     * - 6: 关闭 -> OFF
     */
    private fun Int.toNoiseMode(): NoiseMode? {
        // Evo Pro uses a shifted ANC value mapping (ancIndex=0x1B)
        if (ancIndex == 0x1B) {
            return when (this) {
                1, 2, 3 -> NoiseMode.ANC
                4 -> NoiseMode.WIND
                5 -> NoiseMode.TRANSPARENCY
                6 -> NoiseMode.OFF
                else -> null
            }
        }
        // W860NB PRO and default (ancIndex=0x10)
        return when (this) {
            EdifierWireCodec.ANC_VALUE_DEEP,
            EdifierWireCodec.ANC_VALUE_COMFORT,
            -> NoiseMode.ANC

            EdifierWireCodec.ANC_VALUE_WIND -> NoiseMode.WIND
            EdifierWireCodec.ANC_VALUE_AMBIENT -> NoiseMode.TRANSPARENCY
            EdifierWireCodec.ANC_VALUE_OFF -> NoiseMode.OFF
            else -> null
        }
    }
}