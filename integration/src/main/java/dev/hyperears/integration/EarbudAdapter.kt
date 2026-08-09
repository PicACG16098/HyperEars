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
abstract class EarbudAdapter(
    private val transferredProtocolSession: ProtocolSession? = null,
    initialRuntimeState: AdapterRuntimeState = AdapterRuntimeState(),
) {
    abstract val id: String
    abstract val displayName: String

    /** Whether HyperEars may expose this adapter to MiLink. */
    open val integrationEnabled: Boolean = true

    /** Whether the adapter requires an additional vendor channel before it becomes ready. */
    open val privateProtocolRequired: Boolean = false

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

    /** Vendor applications that must own the private channel while their process is alive. */
    open val controlApps: List<ControlAppSpec> = emptyList()

    /** How this runtime adapter was selected. */
    open val resolution: AdapterResolution = AdapterResolution.FAMILY_MATCH

    /** Semantic request contract inherited from the standard control family by default. */
    open val controlRequestContract: ControlRequestContract = StandardControlRequestContract

    /** Typed state contract inherited from the standard feature family by default. */
    open val featureStateContract: DeviceFeatureStateContract =
        StandardDeviceFeatureStateContract

    abstract fun matches(identity: EarbudIdentity): Boolean

    /**
     * Decides how a provisional protocol-family candidate degrades when its bounded initial
     * transport and handshake attempts finish without ever confirming the private protocol.
     *
     * Concrete and already-confirmed adapters retain their identity by default. A family probe
     * may replace itself with a conservative, non-private adapter while preserving runtime state.
     */
    open fun onInitialProtocolUnavailable(): InitialProtocolFailureResolution =
        InitialProtocolFailureResolution.KeepDormant

    /**
     * Mutable wire-conversation state owned by this adapter instance.
     *
     * Registry entries are factories; a runtime adapter is never shared by two physical devices.
     */
    val protocolSession: ProtocolSession by lazy(LazyThreadSafetyMode.NONE) {
        transferredProtocolSession ?: createProtocolSession()
    }

    private var runtimeState: AdapterRuntimeState = initialRuntimeState
    private var confirmedCapabilities: EarbudCapabilities? = null
    private var confirmedNoiseModes: Set<NoiseMode>? = null
    private var confirmedBatterySource: BatterySource? = null

    protected open fun createProtocolSession(): ProtocolSession = StandardBluetoothProtocolSession()

    /** Begins the one adapter-owned protocol confirmation phase. */
    fun beginHandshake(): AdapterIoResult {
        if (!privateProtocolRequired) {
            return AdapterIoResult(handshake = HandshakeResult.Ready)
        }
        val commands = protocolSession.initialReadCommands()
        val handshake = if (transportReadiness == TransportReadiness.PROTOCOL_HANDSHAKE) {
            HandshakeResult.AwaitingEvidence
        } else {
            HandshakeResult.Ready
        }
        return AdapterIoResult(commands = commands, handshake = handshake)
    }

    /**
     * Consumes one transport read. Protocol events remain private to the adapter aggregate.
     */
    fun receive(bytes: ByteArray): AdapterIoResult {
        val previousSnapshot = snapshot()
        val events = protocolSession.offer(bytes)
        var changed = false
        var handshake: HandshakeResult? = null
        val unknown = mutableListOf<ProtocolEvent.UnknownFrame>()

        // Apply telemetry first so a replacement Adapter receives the complete runtime snapshot
        // decoded from this transport read, independent of event ordering inside a codec.
        events.forEach { event ->
            when (event) {
                is ProtocolEvent.FeatureStateChanged -> {
                    if (!featureStateContract.accepts(this, event.state)) return@forEach
                    val nextFeatures = runtimeState.features.update(event.state)
                    if (nextFeatures != runtimeState.features) {
                        runtimeState = runtimeState.copy(features = nextFeatures)
                        changed = true
                    }
                }

                is ProtocolEvent.UnknownFrame -> unknown += event
                else -> Unit
            }
        }
        events.forEach { event ->
            when (event) {
                ProtocolEvent.HandshakeAccepted -> {
                    if (handshake !is HandshakeResult.Replace) {
                        handshake = HandshakeResult.Ready
                    }
                }

                ProtocolEvent.HandshakeRejected -> {
                    if (handshake !is HandshakeResult.Replace) {
                        handshake = HandshakeResult.Rejected
                    }
                }

                is ProtocolEvent.ProductIdentified -> {
                    onProductIdentified(event.productId)?.let { handshake = it }
                }

                is ProtocolEvent.CapabilitiesIdentified -> {
                    onCapabilitiesIdentified(event.battery, event.noiseModes)
                        ?.let { handshake = it }
                }

                else -> Unit
            }
        }
        val commands = buildList {
            addAll(protocolSession.drainImmediateCommands())
            events.forEach { event -> addAll(protocolSession.followUpCommands(event)) }
        }
        changed = changed || snapshot() != previousSnapshot
        return AdapterIoResult(
            commands = commands,
            handshake = handshake,
            stateChanged = changed,
            unknownFrames = unknown,
        )
    }

    /** Maps authoritative vendor identity evidence to a new concrete adapter when needed. */
    protected open fun onProductIdentified(productId: Int): HandshakeResult? = null

    protected open fun onCapabilitiesIdentified(
        battery: Boolean,
        noiseModes: Set<NoiseMode>,
    ): HandshakeResult? {
        val nextModes = effectiveSupportedNoiseModes() + noiseModes
        val base = effectiveCapabilities()
        confirmedNoiseModes = nextModes
        confirmedCapabilities = base.copy(
            battery = base.battery || battery,
            noiseControl = nextModes.isNotEmpty(),
            windNoiseControl = NoiseMode.WIND in nextModes,
        )
        if (battery) {
            batterySourceAfterProtocolEvidence()?.let { confirmedBatterySource = it }
        }
        return null
    }

    /** Allows a family Adapter to promote system battery to private telemetry after valid evidence. */
    protected open fun batterySourceAfterProtocolEvidence(): BatterySource? = null

    fun effectiveCapabilities(): EarbudCapabilities = confirmedCapabilities ?: capabilities

    fun effectiveSupportedNoiseModes(): Set<NoiseMode> =
        confirmedNoiseModes ?: supportedNoiseModes

    fun effectiveBatterySource(): BatterySource = confirmedBatterySource ?: batterySource

    /** Validates a control against the effective adapter capability and request contract. */
    fun supportsControl(request: ControlRequest): Boolean =
        controlRequestContract.supports(this, request)

    /**
     * Returns the execution policy for one typed request. Concrete adapters override this for
     * verified device-specific confirmation or pacing requirements.
     */
    open fun controlPolicy(request: ControlRequest): ControlExecutionPolicy = when (request) {
        is StandardControlRequest.SetNoiseMode -> ControlExecutionPolicy(
            stateAfterWrite = NoiseModeFeatureState(request.mode),
        )

        else -> ControlExecutionPolicy()
    }

    fun executeControl(request: ControlRequest): AdapterControlResult {
        if (!supportsControl(request)) {
            return AdapterControlResult(accepted = false)
        }
        if (!privateProtocolRequired) {
            return AdapterControlResult(accepted = request === StandardControlRequest.Refresh)
        }
        val commands = protocolSession.encode(request)
        if (commands.isEmpty() && request !== StandardControlRequest.Refresh) {
            return AdapterControlResult(accepted = false)
        }
        val policy = controlPolicy(request)
        var changed = false
        val stateAfterWrite = policy.stateAfterWrite
        if (policy.confirmation != ControlConfirmationPolicy.DEVICE_REPORT &&
            stateAfterWrite != null
        ) {
            val nextFeatures = runtimeState.features.update(stateAfterWrite)
            if (nextFeatures != runtimeState.features) {
                runtimeState = runtimeState.copy(features = nextFeatures)
                changed = true
            }
        }
        val readback = when (policy.confirmation) {
            ControlConfirmationPolicy.PUBLISH_AFTER_WRITE -> emptyList()
            ControlConfirmationPolicy.DEVICE_REPORT,
            ControlConfirmationPolicy.PUBLISH_AFTER_WRITE_THEN_REFRESH,
            -> protocolSession.readback(request)
        }
        return AdapterControlResult(
            accepted = true,
            commands = commands,
            readback = readback,
            stateChanged = changed,
        )
    }

    fun onSystemBatteryChanged(percent: Int?): Boolean {
        if (effectiveBatterySource() != BatterySource.SYSTEM_AGGREGATE) return false
        val battery = EarbudBattery.fromSystemAggregate(percent)
        val nextFeatures = runtimeState.features.update(BatteryFeatureState(battery))
        if (nextFeatures == runtimeState.features) return false
        runtimeState = runtimeState.copy(features = nextFeatures)
        return true
    }

    /**
     * Transfers session-scoped telemetry during an Adapter replacement.
     *
     * Product and capability discovery can replace a family Adapter without reconnecting its
     * channel. The replacement receives only feature types it declares, so a former family or
     * model cannot leak a vendor-specific state into a conservative fallback.
     */
    fun adoptRuntimeState(source: AdapterRuntimeState): Boolean {
        val retained = source.features.retain { state ->
            featureStateContract.accepts(this, state)
        }
        val next = source.copy(features = retained)
        if (next == runtimeState) return false
        runtimeState = next
        return true
    }

    fun runtimeState(): AdapterRuntimeState = runtimeState

    fun snapshot(): AdapterSnapshot = AdapterSnapshot(
        id = id,
        displayName = displayName,
        resolution = resolution,
        privateProtocolRequired = privateProtocolRequired,
        batterySource = effectiveBatterySource(),
        formFactor = formFactor,
        capabilities = effectiveCapabilities(),
        supportedNoiseModes = effectiveSupportedNoiseModes(),
        presentationId = miLinkCardPresentationId,
        transportKinds = transports.mapTo(linkedSetOf()) { transport ->
            when (transport) {
                is RfcommEndpointSpec -> TransportKind.RFCOMM
                is GattTransportSpec -> TransportKind.GATT
                is L2capEndpointSpec -> TransportKind.L2CAP
            }
        },
        controlApps = controlApps,
    )

    fun resetProtocolSession() {
        protocolSession.reset()
    }

    protected fun normalizeDeviceName(value: String): String =
        value.lowercase().filter(Char::isLetterOrDigit)
}

private class StandardBluetoothProtocolSession : ProtocolSession {
    override fun initialReadCommands(): List<ByteArray> = emptyList()

    override fun encode(request: ControlRequest): List<ByteArray> = emptyList()

    override fun offer(bytes: ByteArray): List<ProtocolEvent> = emptyList()

    override fun reset() = Unit
}

enum class TransportReadiness {
    /** A successful link-layer connection is sufficient. */
    CONNECTED,

    /** The candidate must also return an accepted protocol handshake. */
    PROTOCOL_HANDSHAKE,
}

sealed interface InitialProtocolFailureResolution {
    data object KeepDormant : InitialProtocolFailureResolution

    data class FallbackTo(
        val adapter: EarbudAdapter,
    ) : InitialProtocolFailureResolution
}

/**
 * Android's standard Bluetooth-headset behavior.
 *
 * This is the terminal fallback. A2DP/HFP, routing and volume remain owned by Android and the ROM;
 * HyperEars contributes only its form factor and Android's already-cached aggregate battery.
 */
open class StandardEarbudAdapter(
    transferredProtocolSession: ProtocolSession? = null,
    initialRuntimeState: AdapterRuntimeState = AdapterRuntimeState(),
) : EarbudAdapter(transferredProtocolSession, initialRuntimeState) {
    override val id: String = ID
    override val displayName: String = "Standard Bluetooth headset"
    override val batterySource: BatterySource = BatterySource.SYSTEM_AGGREGATE
    override val capabilities: EarbudCapabilities = EarbudCapabilities(
        battery = true,
        audioHandoff = true,
    )
    override val resolution: AdapterResolution = AdapterResolution.STANDARD
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
    private val factories: List<() -> EarbudAdapter> = buildList {
        add(::VivoTwsAir3ProAdapter)
        add(::VivoTws3eAdapter)
        add(::VivoEarbudAdapter)
        add(::StarRingUltraAdapter)
        add(::StarRingEarbudAdapter)
        add(::OppoEncoAir2ProAdapter)
        add(::OppoEncoFree4Adapter)
        add(::OppoEncoX3Adapter)
        add(::OppoEncoAir5Adapter)
        add(::OppoEarbudAdapter)
        add(::BoseHeadphonesAdapter)
        add(::BoseEarbudAdapter)
        add(::EdifierW860NBProAdapter)
        add(::EdifierEvoProAdapter)
        add(::EdifierHeadphonesAdapter)
        add(::EdifierEarbudAdapter)
        add(::FurinaEndlessAdapter)
        add(::RoseEarfreeI5Adapter)
        add(::RoseEarfreeProtocolFamilyAdapter)
        add(::RoseBudsFeelMk2Adapter)
        add(::RoseBudsFeelProtocolFamilyAdapter)
        add(::RoseEarbudAdapter)
        add(::NiceHckYuanDaoOrigAdapter)
        add(::NiceHckEarbudAdapter)
        add(::HonorX5sProAdapter)
        // Apple devices are handled by the platform; keep AAP code available for explicit use,
        // but do not add Apple adapters to HyperEars' default matching chain.
        addAll(SonyAdapterRegistry.factories)
        add(::StandardEarbudAdapter)
    }

    val adapters: List<EarbudAdapter> get() = factories.map { it() }

    private val adapterIds = factories.map { it().id }

    init {
        require(adapterIds.distinct().size == adapterIds.size) {
            val duplicates = adapterIds
                .groupingBy { it }
                .eachCount()
                .filterValues { it > 1 }
                .keys
            "Earbud adapter IDs must be unique: $duplicates"
        }
    }

    fun resolve(identity: EarbudIdentity): EarbudAdapter? {
        if (PlatformReservedHeadsetPolicy.reserves(identity)) return null
        return factories.asSequence().map { it() }.firstOrNull { it.matches(identity) }
    }

    fun forIntegration(identity: EarbudIdentity): EarbudAdapter? =
        resolve(identity)?.takeIf(EarbudAdapter::integrationEnabled)

}
