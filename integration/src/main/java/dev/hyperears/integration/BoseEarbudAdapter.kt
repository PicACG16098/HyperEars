package dev.hyperears.integration

import dev.hyperears.protocol.bose.BoseBmapWireCodec
import dev.hyperears.protocol.bose.BoseProductCatalog
import java.util.Locale

/**
 * Shared Bose BMAP headset behavior.
 *
 * Family detection only reads properties already cached by Android. The private channel then
 * confirms Bose's product ID through BMAP `[0.3]`; names and OUIs never unlock model controls.
 */
open class BoseEarbudAdapter : StandardEarbudAdapter() {
    override val id: String = ID
    override val displayName: String = "Bose BMAP headset"
    override val privateProtocolRequired: Boolean = true
    override val transportReadiness: TransportReadiness =
        TransportReadiness.PROTOCOL_HANDSHAKE
    override val batterySource: BatterySource = BatterySource.PRIVATE_PROTOCOL
    override val transports: List<EarbudTransportSpec> = listOf(
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
        val hasBmapService = identity.serviceUuids.any { uuid ->
            uuid.equals(BMAP_UUID, ignoreCase = true)
        }
        return hasBmapService || BOSE_NAME_MARKERS.any(name::contains) || oui in BOSE_OUIS
    }

    override fun createProtocol(): EarbudProtocol =
        BoseBmapEarbudProtocol(
            expectedProfile = bmapProfile,
            fallbackFormFactor = formFactor,
        )

    companion object {
        const val ID = "bose-bmap-family"
        const val STANDARD_SPP_UUID = "00001101-0000-1000-8000-00805f9b34fb"
        const val BMAP_UUID = "00000000-deca-fade-deca-deafdecacaff"

        private val BOSE_NAME_MARKERS = setOf(
            "bose",
            "quietcomfort",
            "qc30",
            "qc35",
            "qc45",
            "soundsport",
        )

        /** Bose-owned OUI observed on the locally captured QuietComfort Headphones. */
        private val BOSE_OUIS = setOf("BC:87:FA")
    }
}

/** Bose's over-ear family, selected from Android's stable Bluetooth device class. */
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

/** Wire-level noise-control dialect selected by a concrete Bose product profile. */
sealed interface BoseNoiseControlProfile {
    val supportedModes: Set<NoiseMode>

    data class AudioModes(
        val quietModeIndex: Int = 0,
        val awareModeIndex: Int = 1,
        val additionalAncModeIndices: Set<Int> = emptySet(),
        val fullAwareCnc: Int = 10,
        val modeConfigLayout: BoseBmapWireCodec.ModeConfigLayout? = null,
        val windModeFromConfig: Boolean = false,
        override val supportedModes: Set<NoiseMode>,
    ) : BoseNoiseControlProfile

    data class Anr(
        val offValue: Int = 0,
        val highValue: Int = 1,
        val windValue: Int = 2,
        override val supportedModes: Set<NoiseMode> = setOf(
            NoiseMode.ANC,
            NoiseMode.OFF,
            NoiseMode.WIND,
        ),
    ) : BoseNoiseControlProfile

    data class Cnc(
        val maximumRawLevel: Int = 10,
        override val supportedModes: Set<NoiseMode> = setOf(
            NoiseMode.ANC,
            NoiseMode.OFF,
            NoiseMode.TRANSPARENCY,
        ),
    ) : BoseNoiseControlProfile
}

/**
 * Immutable Bose session behavior.
 *
 * [productId] is present for a concrete model confirmed by `[0.3]`. It is absent only for a
 * family fallback whose wire dialect was established by a successful read-only capability probe.
 */
data class BoseBmapProfile(
    val productId: Int?,
    val modelId: String,
    val noiseControl: BoseNoiseControlProfile? = null,
)

/** Common concrete-model behavior for Bose products represented as TWS/in-ear devices. */
abstract class BoseBmapModelAdapter(
    final override val id: String,
    product: BoseProductCatalog.Product,
    noiseControl: BoseNoiseControlProfile? = null,
    final override val miLinkCardPresentationId: MiLinkCardPresentationId? = null,
) : BoseEarbudAdapter() {
    final override val displayName: String = product.displayName
    final override val bmapProfile: BoseBmapProfile = BoseBmapProfile(
        productId = product.productId,
        modelId = id,
        noiseControl = noiseControl,
    )
    final override val supportedNoiseModes: Set<NoiseMode> =
        noiseControl?.supportedModes.orEmpty()
    final override val capabilities: EarbudCapabilities = super.capabilities.copy(
        noiseControl = supportedNoiseModes.isNotEmpty(),
        windNoiseControl = NoiseMode.WIND in supportedNoiseModes,
    )

    /** Concrete Bose models are selected by BMAP product ID, never by a mutable display name. */
    final override fun matches(identity: EarbudIdentity): Boolean = false
}

/** Common concrete-model behavior for Bose over-ear products. */
abstract class BoseBmapHeadphonesModelAdapter(
    final override val id: String,
    product: BoseProductCatalog.Product,
    noiseControl: BoseNoiseControlProfile? = null,
    final override val miLinkCardPresentationId: MiLinkCardPresentationId? = null,
) : BoseHeadphonesAdapter() {
    final override val displayName: String = product.displayName
    final override val bmapProfile: BoseBmapProfile = BoseBmapProfile(
        productId = product.productId,
        modelId = id,
        noiseControl = noiseControl,
    )
    final override val supportedNoiseModes: Set<NoiseMode> =
        noiseControl?.supportedModes.orEmpty()
    final override val capabilities: EarbudCapabilities = super.capabilities.copy(
        noiseControl = supportedNoiseModes.isNotEmpty(),
        windNoiseControl = NoiseMode.WIND in supportedNoiseModes,
    )

    /** Concrete Bose models are selected by BMAP product ID, never by a mutable display name. */
    final override fun matches(identity: EarbudIdentity): Boolean = false
}

/** Opaque presentation contracts shared by models with the same native-card semantics. */
object BoseMiLinkPresentationIds {
    val TWO_MODE = MiLinkCardPresentationId("bose-anc-aware-two-mode")
    val WIND_REPLACES_OFF = MiLinkCardPresentationId("bose-wind-replaces-off")
    val WIND_REPLACES_TRANSPARENCY =
        MiLinkCardPresentationId("bose-wind-replaces-transparency")
}
