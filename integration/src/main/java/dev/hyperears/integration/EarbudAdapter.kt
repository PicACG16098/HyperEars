package dev.hyperears.integration

data class EarbudIdentity(
    val deviceName: String?,
    val standardHeadset: Boolean,
    val nativeSystemEarbud: Boolean = false,
    val deviceAddress: String? = null,
    val bluetoothDeviceClass: Int? = null,
    val serviceUuids: Set<String> = emptySet(),
)

/**
 * A complete earbud-model adapter.
 *
 * Adapters form a strict inheritance hierarchy. Each vendor or model inherits the behavior and
 * capabilities of its parent, then overrides only verified differences. Transport ownership stays
 * outside this hierarchy so selecting an adapter never creates a Bluetooth connection by itself.
 */
abstract class EarbudAdapter {
    abstract val id: String
    abstract val displayName: String

    /** Whether HyperEars may expose this adapter to MiLink. */
    open val integrationEnabled: Boolean = true

    /** Whether the adapter requires an additional vendor channel before it becomes ready. */
    open val privateProtocolRequired: Boolean = false

    /** How the session confirms and publishes a successful noise-control write. */
    open val noiseControlConfirmation: ControlConfirmationPolicy =
        ControlConfirmationPolicy.DEVICE_REPORT

    /** Noise states that this model can truthfully expose through MiLink's native controls. */
    open val supportedNoiseModes: Set<NoiseMode> = emptySet()

    /** The authoritative source for this adapter's battery telemetry. */
    open val batterySource: BatterySource = BatterySource.NONE

    /** Physical form used by platform presentation bridges. */
    open val formFactor: HeadsetFormFactor = HeadsetFormFactor.TWS

    open val capabilities: EarbudCapabilities = EarbudCapabilities()
    open val miLinkCardPresentationId: MiLinkCardPresentationId? = null

    /** Ordered transport candidates owned by this model adapter. */
    open val transports: List<EarbudTransportSpec> = emptyList()

    /** Evidence required before a transport candidate becomes the session's active channel. */
    open val transportReadiness: TransportReadiness = TransportReadiness.CONNECTED

    /** Minimum ms between ANC switch commands; 0 = no cooldown. */
    open val ancSwitchCooldownMs: Long = 0L

    abstract fun matches(identity: EarbudIdentity): Boolean

    /** Creates per-session mutable protocol state, if this adapter owns a private protocol. */
    open fun createProtocol(): EarbudProtocol? = null

    protected fun normalizeDeviceName(value: String): String =
        value.lowercase().filter(Char::isLetterOrDigit)
}

enum class TransportReadiness {
    /** A successful link-layer connection is sufficient. */
    CONNECTED,

    /** The candidate must also return an accepted protocol handshake. */
    PROTOCOL_HANDSHAKE,
}

/**
 * Android's standard Bluetooth-headset behavior.
 *
 * This is the terminal fallback. A2DP/HFP, routing and volume remain owned by Android and the ROM;
 * HyperEars contributes only its form factor and Android's already-cached aggregate battery.
 */
open class StandardEarbudAdapter : EarbudAdapter() {
    override val id: String = ID
    override val displayName: String = "Standard Bluetooth headset"
    override val batterySource: BatterySource = BatterySource.SYSTEM_AGGREGATE
    override val capabilities: EarbudCapabilities = EarbudCapabilities(
        battery = true,
        audioHandoff = true,
    )
    override fun matches(identity: EarbudIdentity): Boolean =
        identity.standardHeadset && !identity.nativeSystemEarbud

    companion object {
        const val ID = "standard-bluetooth-headset"
    }
}

/**
 * Resolves the most specific eligible adapter first.
 */
object EarbudAdapterRegistry {
    private val vivoFamilyAdapter = VivoEarbudAdapter()
    private val starRingFamilyAdapter = StarRingEarbudAdapter()
    private val oppoFamilyAdapter = OppoEarbudAdapter()
    private val boseFamilyAdapter = BoseEarbudAdapter()
    private val boseHeadphonesAdapter = BoseHeadphonesAdapter()
    private val edifierFamilyAdapter = EdifierEarbudAdapter()
    private val edifierHeadphonesAdapter = EdifierHeadphonesAdapter()
    private val roseEarfreeFamilyAdapter = RoseEarfreeProtocolFamilyAdapter()
    private val roseBudsFeelFamilyAdapter = RoseBudsFeelProtocolFamilyAdapter()
    private val roseFamilyAdapter = RoseEarbudAdapter()
    private val niceHckFamilyAdapter = NiceHckEarbudAdapter()
    private val appleAirPodsFamilyAdapter = AppleAirPodsAdapter()
    private val standardAdapter = StandardEarbudAdapter()

    val adapters: List<EarbudAdapter> = buildList {
        add(VivoTwsAir3ProAdapter)
        add(VivoTws3eAdapter)
        add(vivoFamilyAdapter)
        add(StarRingUltraAdapter)
        add(starRingFamilyAdapter)
        add(OppoEncoAir2ProAdapter)
        add(OppoEncoFree4Adapter)
        add(OppoEncoX3Adapter)
        add(OppoEncoAir5Adapter)
        add(oppoFamilyAdapter)
        addAll(BoseBmapModelRegistry.adapters)
        addAll(BoseCapabilityAdapterRegistry.adapters)
        add(boseHeadphonesAdapter)
        add(boseFamilyAdapter)
        add(EdifierW860NBProAdapter)
        add(edifierHeadphonesAdapter)
        add(edifierFamilyAdapter)
        add(RoseEarfreeI5Adapter)
        add(roseEarfreeFamilyAdapter)
        add(RoseBudsFeelMk2Adapter)
        add(roseBudsFeelFamilyAdapter)
        add(roseFamilyAdapter)
        add(NiceHckYuanDaoOrigAdapter)
        add(niceHckFamilyAdapter)
        add(AppleAirPodsProAdapter)
        add(AppleAirPodsMaxAdapter)
        add(appleAirPodsFamilyAdapter)
        addAll(SonyAdapterRegistry.adapters)
        add(standardAdapter)
    }

    private val byAdapterId = adapters.associateBy(EarbudAdapter::id)

    init {
        require(byAdapterId.size == adapters.size) {
            val duplicates = adapters
                .groupingBy(EarbudAdapter::id)
                .eachCount()
                .filterValues { it > 1 }
                .keys
            "Earbud adapter IDs must be unique: $duplicates"
        }
    }

    fun resolve(identity: EarbudIdentity): EarbudAdapter? =
        adapters.firstOrNull { it.matches(identity) }

    fun forIntegration(identity: EarbudIdentity): EarbudAdapter? =
        resolve(identity)?.takeIf(EarbudAdapter::integrationEnabled)

    fun byId(id: String?): EarbudAdapter? = id?.let(byAdapterId::get)

    fun integratedById(id: String?): EarbudAdapter? =
        byId(id)?.takeIf(EarbudAdapter::integrationEnabled)
}
