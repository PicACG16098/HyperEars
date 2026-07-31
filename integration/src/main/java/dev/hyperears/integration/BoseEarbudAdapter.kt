package dev.hyperears.integration

import dev.hyperears.protocol.bose.BoseBmapWireCodec
import java.util.Locale

/**
 * Shared Bose BMAP headset behavior.
 *
 * Family detection is passive: the Bluetooth name and registered Bose OUI are read from the
 * already-connected system device. The private channel then confirms Bose's product ID through
 * BMAP `[0.3]`; no BLE scan or polling is introduced.
 */
open class BoseEarbudAdapter : StandardEarbudAdapter() {
    override val id: String = ID
    override val displayName: String = "Bose BMAP headset"
    override val privateProtocolRequired: Boolean = true
    override val batterySource: BatterySource = BatterySource.PRIVATE_PROTOCOL
    override val endpoints: List<RfcommEndpointSpec> = listOf(
        RfcommEndpointSpec.Channel(number = 8),
        RfcommEndpointSpec.ServiceUuid(
            uuid = STANDARD_SPP_UUID,
            id = "spp-uuid",
        ),
        RfcommEndpointSpec.ServiceUuid(
            uuid = BMAP_UUID,
            id = "bmap-uuid",
        ),
        RfcommEndpointSpec.Channel(number = 2),
    )
    open val bmapProfile: BoseBmapProfile? = null

    override fun matches(identity: EarbudIdentity): Boolean {
        if (!identity.standardHeadset || identity.nativeSystemEarbud) return false
        val name = normalizeDeviceName(identity.deviceName.orEmpty())
        val oui = identity.deviceAddress
            ?.uppercase(Locale.ROOT)
            ?.take(8)
        return BOSE_NAME_MARKERS.any(name::contains) || oui in BOSE_OUIS
    }

    override fun createProtocol(): EarbudProtocol =
        BoseBmapEarbudProtocol(expectedProfile = bmapProfile)

    companion object {
        const val ID = "bose-bmap-family"
        const val STANDARD_SPP_UUID = "00001101-0000-1000-8000-00805f9b34fb"
        const val BMAP_UUID = "00000000-deca-fade-deca-deafdecacaff"

        private val BOSE_NAME_MARKERS = setOf(
            "bose",
            "quietcomfort",
            "qc35",
            "qc45",
        )

        /** Bose-owned OUI observed on the captured QuietComfort Headphones. */
        private val BOSE_OUIS = setOf("BC:87:FA")
    }
}

/**
 * Bose's over-ear family.
 *
 * Android's Bluetooth class remains available after a user rename, so it is a better form-factor
 * discriminator than the display name. Product identity still comes from BMAP after connection.
 */
open class BoseHeadphonesAdapter : BoseEarbudAdapter() {
    override val id: String = ID
    override val displayName: String = "Bose headphones"
    override val formFactor: HeadsetFormFactor = HeadsetFormFactor.HEADPHONES

    override fun matches(identity: EarbudIdentity): Boolean =
        super.matches(identity) &&
            (
                identity.bluetoothDeviceClass == BLUETOOTH_DEVICE_CLASS_HEADPHONES ||
                    normalizeDeviceName(identity.deviceName.orEmpty()).contains("headphones")
                )

    companion object {
        const val ID = "bose-headphones-family"

        // android.bluetooth.BluetoothClass.Device.AUDIO_VIDEO_HEADPHONES
        const val BLUETOOTH_DEVICE_CLASS_HEADPHONES = 0x0418
    }
}

/**
 * Concrete model adapter for Bose QuietComfort Headphones (`prince`, product `0x4075`).
 *
 * A renamed device may initially enter through [BoseEarbudAdapter]; `[0.3]` remains the
 * authoritative on-wire model confirmation.
 */
object BoseQuietComfortHeadphonesAdapter : BoseHeadphonesAdapter() {
    const val ID = "bose-quietcomfort-headphones-4075"
    const val PRODUCT_ID = 0x4075
    val PRESENTATION_ID = MiLinkCardPresentationId(ID)

    override val id: String = ID
    override val displayName: String = "Bose QuietComfort Headphones"
    override val miLinkCardPresentationId: MiLinkCardPresentationId = PRESENTATION_ID
    override val bmapProfile: BoseBmapProfile = BoseBmapProfile(
        productId = PRODUCT_ID,
        modelId = ID,
        quietModeIndex = 0,
        awareModeIndex = 1,
        fullAwareCnc = 10,
        windModeFromConfig = true,
    )
    override val capabilities: EarbudCapabilities = super.capabilities.copy(
        noiseControl = true,
        windNoiseControl = true,
    )
    override val supportedNoiseModes: Set<NoiseMode> = setOf(
        NoiseMode.ANC,
        NoiseMode.TRANSPARENCY,
        NoiseMode.WIND,
    )

    /**
     * This adapter is selected only by BMAP product identity.
     *
     * Bluetooth names remain family hints and must never unlock model-specific controls before
     * `[0.3]` confirms product `0x4075`.
     */
    override fun matches(identity: EarbudIdentity): Boolean = false

}

/**
 * Product-specific BMAP behavior owned by a concrete Bose adapter.
 *
 * The family protocol consumes this immutable profile only after `[0.3]` confirms [productId].
 */
data class BoseBmapProfile(
    val productId: Int,
    val modelId: String,
    val quietModeIndex: Int,
    val awareModeIndex: Int,
    val fullAwareCnc: Int,
    /**
     * Whether a non-built-in ModeConfig slot with `wind=true` is exposed as the WIND mode.
     *
     * This does not enable ModeConfig editing; HyperEars only switches to the returned slot.
     */
    val windModeFromConfig: Boolean,
)

private class BoseBmapEarbudProtocol(
    private val expectedProfile: BoseBmapProfile? = null,
) : EarbudProtocol {
    private val decoder = BoseBmapWireCodec.Decoder()
    private val modeConfigs = mutableMapOf<Int, BoseBmapWireCodec.ModeConfig>()
    private var identityAccepted: Boolean? = null
    private var activeProfile: BoseBmapProfile? = null
    private var pendingBattery: EarbudEvent.BatteryChanged? = null
    private var currentModeIndex: Int? = null

    override fun initialReadCommands(): List<ByteArray> = listOf(
        BoseBmapWireCodec.queryProductIdentity,
        BoseBmapWireCodec.queryBattery,
    )

    override fun encode(request: ControlRequest): List<ByteArray> = when (request) {
        ControlRequest.Refresh -> initialReadCommands() + activeProfile.modeReadCommands()
        is ControlRequest.SetNoiseMode -> when (request.mode) {
            NoiseMode.ANC -> activeProfile
                ?.let { listOf(BoseBmapWireCodec.switchMode(it.quietModeIndex)) }
                .orEmpty()

            NoiseMode.TRANSPARENCY -> activeProfile
                ?.let { listOf(BoseBmapWireCodec.switchMode(it.awareModeIndex)) }
                .orEmpty()

            NoiseMode.WIND -> activeProfile
                ?.windModeIndex()
                ?.let { listOf(BoseBmapWireCodec.switchMode(it)) }
                .orEmpty()

            NoiseMode.OFF -> emptyList()
        }
    }

    override fun followUpCommands(event: EarbudEvent): List<ByteArray> =
        if (event is EarbudEvent.ModelIdentified && event.modelId == activeProfile?.modelId) {
            activeProfile.modeReadCommands()
        } else {
            emptyList()
        }

    override fun readback(request: ControlRequest): List<ByteArray> = when (request) {
        ControlRequest.Refresh -> emptyList()
        is ControlRequest.SetNoiseMode ->
            listOf(BoseBmapWireCodec.queryCurrentMode).takeIf { activeProfile != null }.orEmpty()
    }

    override fun offer(bytes: ByteArray): List<EarbudEvent> = buildList {
        decoder.offer(bytes).forEach { frame ->
            BoseBmapWireCodec.parseProductIdentity(frame)?.let { identity ->
                identityAccepted =
                    expectedProfile == null || identity.productId == expectedProfile.productId
                activeProfile = BoseBmapProfileRegistry
                    .find(identity.productId)
                    ?.takeIf { identityAccepted == true }
                activeProfile?.let { profile ->
                    add(EarbudEvent.ModelIdentified(profile.modelId))
                }
                add(EarbudEvent.Handshake(identityAccepted == true))
                if (identityAccepted == true) {
                    pendingBattery?.let(::add)
                }
                pendingBattery = null
                return@forEach
            }

            BoseBmapWireCodec.parseBatteryState(frame)?.let { battery ->
                val overall = battery.overallPercent
                val event = EarbudEvent.BatteryChanged(
                    EarbudBattery(
                        left = BatteryReading(battery.leftPercent, charging = false),
                        right = BatteryReading(battery.rightPercent, charging = false),
                        case = BatteryReading(battery.casePercent, charging = false),
                        overall = BatteryReading(overall, charging = false),
                    ),
                )
                when {
                    expectedProfile == null || identityAccepted == true -> add(event)
                    identityAccepted == null -> pendingBattery = event
                }
                return@forEach
            }

            BoseBmapWireCodec.parseModeConfig(frame)?.let { config ->
                if (activeProfile == null) return@forEach
                modeConfigs[config.index] = config
                currentModeIndex
                    ?.takeIf { it == config.index }
                    ?.toNoiseMode(requireNotNull(activeProfile))
                    ?.let { add(EarbudEvent.NoiseModeChanged(it, acknowledged = true)) }
                return@forEach
            }

            BoseBmapWireCodec.parseCurrentMode(frame)?.let { modeIndex ->
                currentModeIndex = modeIndex
                activeProfile?.let { profile ->
                    modeIndex.toNoiseMode(profile)
                }?.let { mode ->
                    add(EarbudEvent.NoiseModeChanged(mode, acknowledged = true))
                }
                return@forEach
            }

            add(
                EarbudEvent.UnknownFrame(
                    version = 0,
                    vendor = frame.functionBlock,
                    command = frame.function,
                    payloadSize = frame.payload.size,
                ),
            )
        }
    }

    override fun reset() {
        decoder.reset()
        modeConfigs.clear()
        identityAccepted = null
        activeProfile = null
        pendingBattery = null
        currentModeIndex = null
    }

    private fun Int.toNoiseMode(profile: BoseBmapProfile): NoiseMode? = when (this) {
        profile.quietModeIndex -> NoiseMode.ANC
        profile.awareModeIndex -> NoiseMode.TRANSPARENCY
        else -> modeConfigs[this]?.let { config ->
            when {
                profile.windModeFromConfig && config.wind -> NoiseMode.WIND
                config.rawCnc >= profile.fullAwareCnc -> NoiseMode.TRANSPARENCY
                else -> NoiseMode.ANC
            }
        }
    }

    private fun BoseBmapProfile.windModeIndex(): Int? {
        if (!windModeFromConfig) return null
        return modeConfigs.values
            .asSequence()
            .filterNot { it.index == quietModeIndex || it.index == awareModeIndex }
            .firstOrNull { it.wind }
            ?.index
    }
}

/**
 * Composition root for concrete Bose product profiles.
 *
 * Adding a model registers the profile declared by that adapter; the common protocol never
 * contains product constants or model-specific command semantics.
 */
private object BoseBmapProfileRegistry {
    private val profiles by lazy {
        listOf(
            BoseQuietComfortHeadphonesAdapter.bmapProfile,
        ).associateBy(BoseBmapProfile::productId)
    }

    fun find(productId: Int): BoseBmapProfile? = profiles[productId]
}

private fun BoseBmapProfile?.modeReadCommands(): List<ByteArray> =
    if (this == null) {
        emptyList()
    } else {
        listOf(
            BoseBmapWireCodec.queryModeConfigs,
            BoseBmapWireCodec.queryCurrentMode,
        )
    }
