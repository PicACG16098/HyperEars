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
    override val endpoints: List<RfcommEndpointSpec> = listOf(
        RfcommEndpointSpec.ServiceUuid(
            uuid = EDF_SPP_UUID,
            id = "edifier-spp-uuid",
        ),
        RfcommEndpointSpec.Channel(number = 1),
    )

    override fun matches(identity: EarbudIdentity): Boolean {
        if (!identity.standardHeadset || identity.nativeSystemEarbud) return false
        val name = normalizeDeviceName(identity.deviceName.orEmpty())
        return EDIFIER_NAME_MARKERS.any(name::contains)
    }

    override fun createProtocol(): EarbudProtocol =
        EdifierEarbudProtocol()

    companion object {
        const val ID = "edifier-family"
        const val EDF_SPP_UUID = "EDF00000-EDFE-DFED-FEDF-EDFEDFEDFEDF"

        private val EDIFIER_NAME_MARKERS = setOf(
            "edifier",
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
object EdifierW860NBProAdapter : EdifierHeadphonesAdapter() {
    const val ID = "edifier-w860nb-pro"

    override val id: String = ID
    override val displayName: String = "Edifier W860NB PRO"
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

    override fun matches(identity: EarbudIdentity): Boolean {
        val name = normalizeDeviceName(identity.deviceName.orEmpty())
        return name.contains("w860nbpro") || name.contains("w860nb")
    }
}

/**
 * Edifier private protocol state machine.
 *
 * Uses the BES/恒玄 SPP framing to query battery, ANC state, and device capabilities.
 * Frame format: [0xBB/0xCC][APP_CODE][CMD][LEN_H][LEN_L][PAYLOAD...][CRC8]
 */
private class EdifierEarbudProtocol : EarbudProtocol {
    private val decoder = EdifierWireCodec.Decoder()
    private var functionQueried = false

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
            if (ancValue != null) {
                listOf(EdifierWireCodec.setAnc(ancValue = ancValue))
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
}
