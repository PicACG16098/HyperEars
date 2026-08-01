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

    abstract fun matches(identity: EarbudIdentity): Boolean

    /** Creates per-session mutable protocol state, if this adapter owns a private protocol. */
    open fun createProtocol(): EarbudProtocol? = null

    protected fun normalizeDeviceName(value: String): String =
        value.lowercase().filter(Char::isLetterOrDigit)
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
    private val standardAdapter = StandardEarbudAdapter()

    val adapters: List<EarbudAdapter> = listOf(
        VivoTwsAir3ProAdapter,
        VivoTws3eAdapter,
        vivoFamilyAdapter,
        StarRingUltraAdapter,
        starRingFamilyAdapter,
        OppoEncoAir2ProAdapter,
        OppoEncoFree4Adapter,
        OppoEncoX3Adapter,
        OppoEncoAir5Adapter,
        oppoFamilyAdapter,
        BoseQuietComfortHeadphonesAdapter,
        boseHeadphonesAdapter,
        boseFamilyAdapter,
        EdifierW860NBProAdapter,
        edifierHeadphonesAdapter,
        edifierFamilyAdapter,
        standardAdapter,
    )

    private val byAdapterId = adapters.associateBy(EarbudAdapter::id)

    init {
        require(byAdapterId.size == adapters.size) {
            "Earbud adapter IDs must be unique"
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
