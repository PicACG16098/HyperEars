package dev.hyperears.integration

data class EarbudIdentity(
    val deviceName: String?,
    val standardHeadset: Boolean,
    val nativeSystemEarbud: Boolean = false,
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

    open val capabilities: EarbudCapabilities = EarbudCapabilities()
    open val miLinkIdentity: MiLinkIdentity? = null
    open val endpoints: List<RfcommEndpointSpec> = emptyList()

    abstract fun matches(identity: EarbudIdentity): Boolean

    /** Creates per-session mutable protocol state, if this adapter owns a private protocol. */
    open fun createProtocol(): EarbudProtocol? = null

    protected fun normalizeDeviceName(value: String): String =
        value.lowercase().filter(Char::isLetterOrDigit)
}

/**
 * Android's standard Bluetooth-headset behavior.
 *
 * This is the terminal fallback. HyperEars does not inject it into MiLink; A2DP/HFP and volume
 * remain owned by Android and the ROM.
 */
open class StandardEarbudAdapter : EarbudAdapter() {
    override val id: String = ID
    override val displayName: String = "Standard Bluetooth headset"
    override val capabilities: EarbudCapabilities = EarbudCapabilities(
        audioHandoff = true,
    )
    override val miLinkIdentity: MiLinkIdentity =
        MiLinkIdentity(deviceId = FALLBACK_MILINK_DEVICE_ID)

    override fun matches(identity: EarbudIdentity): Boolean =
        identity.standardHeadset && !identity.nativeSystemEarbud

    companion object {
        const val ID = "standard-bluetooth-headset"
        const val FALLBACK_MILINK_DEVICE_ID = "01010607"
    }
}

/**
 * Resolves the most specific eligible adapter first.
 */
object EarbudAdapterRegistry {
    private val vivoFamilyAdapter = VivoEarbudAdapter()
    private val standardAdapter = StandardEarbudAdapter()

    val adapters: List<EarbudAdapter> = listOf(
        VivoTwsAir3ProAdapter,
        vivoFamilyAdapter,
        standardAdapter,
    )

    fun resolve(identity: EarbudIdentity): EarbudAdapter? =
        adapters.firstOrNull { it.matches(identity) }

    fun forIntegration(identity: EarbudIdentity): EarbudAdapter? =
        resolve(identity)?.takeIf(EarbudAdapter::integrationEnabled)

    fun byId(id: String?): EarbudAdapter? =
        adapters.firstOrNull { it.id == id }

    fun integratedById(id: String?): EarbudAdapter? =
        byId(id)?.takeIf(EarbudAdapter::integrationEnabled)
}
