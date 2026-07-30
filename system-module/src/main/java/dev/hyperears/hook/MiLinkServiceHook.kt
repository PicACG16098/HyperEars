package dev.hyperears.hook

import android.annotation.SuppressLint
import android.app.Application
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import dev.hyperears.bridge.BridgeStage
import dev.hyperears.bridge.ModuleContract
import dev.hyperears.bridge.ProcessStateStore
import dev.hyperears.integration.ControlRequest
import dev.hyperears.integration.EarbudAdapterRegistry
import dev.hyperears.integration.EarbudState
import dev.hyperears.integration.MiLinkStateCodec
import dev.hyperears.integration.NoiseMode
import dev.hyperears.runtime.toEarbudIdentity
import java.lang.reflect.Method
import java.util.Collections
import java.util.WeakHashMap

/**
 * Supplies the minimum truthful Xiaomi identity required for native audio handoff.
 *
 * A2DP routing remains entirely owned by HyperOS/MiLink. This bridge exposes identity,
 * battery and ANC state, and translates only the three verified noise-control commands.
 */
internal class MiLinkServiceHook : HookContext() {
    private data class SessionStages(
        val sessionToken: String,
        val stages: MutableSet<BridgeStage> = mutableSetOf(),
    )

    private val knownAddresses = Collections.synchronizedSet(mutableSetOf<String>())
    private val runtimeOwners = Collections.synchronizedSet(
        Collections.newSetFromMap(WeakHashMap<Any, Boolean>()),
    )
    private val targetHeadsetAddresses = Collections.synchronizedMap(
        WeakHashMap<Any, String>(),
    )
    private val observationLock = Any()
    private val sessionStages = mutableMapOf<String, SessionStages>()
    private val pendingStages = mutableMapOf<String, MutableSet<BridgeStage>>()

    @Volatile
    private var context: Context? = null

    @Volatile
    private var receiverRegistered = false

    @Volatile
    private var lastAncBatteryController: Any? = null

    @Volatile
    private var lastProfileContext: Any? = null

    override fun install() {
        hookApplicationContext()
        val runtimeClasses = listOf(
            "com.xiaomi.mxbluetoothsdk.manager.MxBluetoothManager",
            "com.xiaomi.mxbluetoothsdk.service.MxBluetoothService",
        )
        runtimeClasses.forEach { className ->
            hookContextEntry(className)
            hookBluetoothDeviceResult(className, "checkIsMiTWS") { 1 }
            hookBluetoothDeviceResult(className, "getDeviceId") { device ->
                adapterIdentity(device)?.miLinkIdentity?.deviceId
            }
            hookBluetoothDeviceResult(className, "getBatteryLevel") { device ->
                adapterFor(device)
                    ?.takeIf { it.capabilities.battery }
                    ?.let { MiLinkStateCodec.regularBatteryLevel(stateFor(device)) }
            }
            hookBluetoothDeviceResult(className, "getAncState") { device ->
                adapterFor(device)
                    ?.takeIf { it.capabilities.noiseControl }
                    ?.let { MiLinkStateCodec.ancState(stateFor(device)) }
            }
            hookBluetoothDeviceResult(className, "getDeviceRunInfo") { 0 }
            hookBluetoothDeviceResult(className, "getWearStatus") { device ->
                "0,0".takeIf {
                    adapterFor(device)?.capabilities?.wearDetection == true
                }
            }
            hookBluetoothDeviceResult(className, "isLeAudio") { device ->
                false.takeIf {
                    adapterFor(device)?.privateProtocolRequired == true
                }
            }

            hookAddressResult(className, "isMiTWS") { true }
            hookAddressResult(className, "isSupportAudioSwitch") { 1 }
            hookAddressResult(className, "getRingFindState") { false }

            hookNoiseCommand(className, "openAnc", NoiseMode.ANC, 1)
            hookNoiseCommand(className, "closeAnc", NoiseMode.OFF, 0)
            hookNoiseCommand(className, "openTransparent", NoiseMode.TRANSPARENCY, 2)
        }

        hookHeadsetRuntime()
    }

    private fun hookApplicationContext() {
        runCatching {
            hookAfter(
                findMethod(
                    Application::class.java.name,
                    "attach",
                    Context::class.java,
                ),
            ) {
                registerStateReceiver(args[0] as? Context)
            }
        }.onFailure {
            ModuleLog.warn("MiLink", "Application.attach hook unavailable", it)
        }
    }

    private fun hookContextEntry(className: String) {
        runCatching {
            hookAfter(findMethod(className, "getInstanceForIsMiTWS", Context::class.java)) {
                registerStateReceiver(args[0] as? Context)
            }
        }.onFailure {
            ModuleLog.debug("MiLink", "optional $className context entry unavailable")
        }
    }

    private fun hookHeadsetRuntime() {
        hookBluetoothDeviceResult(
            "com.miui.headset.runtime.ProfileContext",
            "getDeviceId",
        ) { device ->
            adapterIdentity(device)?.miLinkIdentity?.deviceId
        }
        hookBluetoothDeviceResult(
            "com.miui.headset.runtime.ProfileContext",
            "getBatteryLevel",
        ) { device ->
            adapterFor(device)
                ?.takeIf { it.capabilities.battery }
                ?.let { MiLinkStateCodec.batteryLevels(stateFor(device)) }
        }
        hookBluetoothDeviceResult(
            "com.miui.headset.runtime.AncBatteryController",
            "getDeviceId",
        ) { device ->
            adapterIdentity(device)?.miLinkIdentity?.deviceId
        }
        hookBluetoothDeviceResult(
            "com.miui.headset.runtime.AncBatteryController",
            "getAncState",
        ) { device ->
            adapterFor(device)
                ?.takeIf { it.capabilities.noiseControl }
                ?.let { MiLinkStateCodec.ancState(stateFor(device)) }
        }
        hookBluetoothDeviceResult(
            "com.miui.headset.runtime.AncBatteryController",
            "getBatteryLevelCache",
        ) { device ->
            adapterFor(device)
                ?.takeIf { it.capabilities.battery }
                ?.let { MiLinkStateCodec.batteryLevels(stateFor(device)) }
        }
        hookHeadsetPropertyRefresh()
        hookAddressResult(
            "com.miui.headset.runtime.AncBatteryController",
            "getSwitchState",
        ) { address ->
            if (adapterForAddress(address)?.capabilities?.noiseControl == true) 1 else 0
        }

        runCatching {
            hookBefore(
                findMethod(
                    "com.miui.headset.runtime.AncBatteryController",
                    "setAncStateBlock",
                    BluetoothDevice::class.java,
                    Int::class.java,
                ),
            ) {
                val device = args[0] as? BluetoothDevice
                val mode = args[1] as? Int ?: return@hookBefore
                if (adapterFor(device)?.capabilities?.noiseControl != true) {
                    return@hookBefore
                }
                rememberRuntimeOwner(
                    "com.miui.headset.runtime.AncBatteryController",
                    instance,
                )
                sendControl(
                    ControlRequest.SetNoiseMode(
                        when (mode) {
                            1 -> NoiseMode.ANC
                            2 -> NoiseMode.TRANSPARENCY
                            else -> NoiseMode.OFF
                        },
                    ),
                    device,
                )
                result = mode.coerceIn(0, 2)
            }
        }.onFailure {
            ModuleLog.debug("MiLink", "optional setAncStateBlock unavailable")
        }

        hookHeadsetInfo("getDeviceId") { info ->
            adapterIdentity(stateForHeadsetInfo(info))?.miLinkIdentity?.deviceId
        }
        hookHeadsetInfo("component3") { info ->
            adapterIdentity(stateForHeadsetInfo(info))?.miLinkIdentity?.deviceId
        }
        hookHeadsetInfo("getName") { info ->
            val state = stateForHeadsetInfo(info)
            state.deviceName ?: adapterIdentity(state)?.displayName
        }
        hookHeadsetInfo("component2") { info ->
            val state = stateForHeadsetInfo(info)
            state.deviceName ?: adapterIdentity(state)?.displayName
        }
        hookHeadsetInfo("getPowers") { info ->
            val state = stateForHeadsetInfo(info)
            adapterIdentity(state)
                ?.takeIf { it.capabilities.battery }
                ?.let { MiLinkStateCodec.batteryLevels(state) }
        }
        hookHeadsetInfo("component4") { info ->
            val state = stateForHeadsetInfo(info)
            adapterIdentity(state)
                ?.takeIf { it.capabilities.battery }
                ?.let { MiLinkStateCodec.batteryLevels(state) }
        }
        hookHeadsetInfo("getMode") { info ->
            val state = stateForHeadsetInfo(info)
            adapterIdentity(state)
                ?.takeIf { it.capabilities.noiseControl }
                ?.let { MiLinkStateCodec.ancState(state) }
        }
        hookHeadsetInfo("component5") { info ->
            val state = stateForHeadsetInfo(info)
            adapterIdentity(state)
                ?.takeIf { it.capabilities.noiseControl }
                ?.let { MiLinkStateCodec.ancState(state) }
        }
        hookHeadsetInfo("getSwitchState") { info ->
            val state = stateForHeadsetInfo(info)
            if (adapterIdentity(state)?.capabilities?.noiseControl == true) 1 else 0
        }
        hookHeadsetInfo("component8") { info ->
            val state = stateForHeadsetInfo(info)
            if (adapterIdentity(state)?.capabilities?.noiseControl == true) 1 else 0
        }
    }

    /**
     * Xiaomi defines getHeadsetPropertyBlock() as an operation result, not a battery getter.
     *
     * A successful native implementation refreshes its model, publishes property update type 4,
     * and returns 100. The vivo adapter already owns the current property snapshot, so it completes
     * the same lifecycle without entering Xiaomi's unsupported private-protocol request path.
     */
    private fun hookHeadsetPropertyRefresh() {
        val className = "com.miui.headset.runtime.AncBatteryController"
        runCatching {
            hookBefore(
                findMethod(
                    className,
                    "getHeadsetPropertyBlock",
                    BluetoothDevice::class.java,
                ),
            ) {
                val device = args[0] as? BluetoothDevice ?: return@hookBefore
                if (adapterFor(device) == null) return@hookBefore
                rememberRuntimeOwner(className, instance)
                captureContext(instance)
                recordBridgeStage(device, BridgeStage.CAPABILITIES_QUERIED)

                val listenerCount = notifyHeadsetPropertyChanged(
                    device = device,
                    updateTypes = setOf(HEADSET_PROPERTY_CHANGED),
                    additionalOwner = instance,
                )
                result = HEADSET_OPERATION_SUCCESS
                ModuleLog.debug(
                    "MiLink",
                    "completed property refresh for " +
                        "${maskBluetoothAddress(runCatching { device.address }.getOrNull())} " +
                        "listeners=$listenerCount result=$HEADSET_OPERATION_SUCCESS",
                )
            }
        }.onFailure {
            ModuleLog.warn(
                "MiLink",
                "required $className.getHeadsetPropertyBlock unavailable",
                it,
            )
        }
    }

    private fun hookBluetoothDeviceResult(
        className: String,
        methodName: String,
        value: (BluetoothDevice) -> Any?,
    ) {
        runCatching {
            hookAfter(findMethod(className, methodName, BluetoothDevice::class.java)) {
                val device = args[0] as? BluetoothDevice ?: return@hookAfter
                if (adapterFor(device) == null) return@hookAfter
                recordBridgeStage(device, methodName.bridgeStage())
                rememberRuntimeOwner(className, instance)
                captureContext(instance)
                value(device)?.let {
                    result = it
                }
            }
        }.onFailure {
            ModuleLog.debug("MiLink", "optional $className.$methodName unavailable")
        }
    }

    private fun hookAddressResult(
        className: String,
        methodName: String,
        value: (String) -> Any?,
    ) {
        runCatching {
            hookAfter(findMethod(className, methodName, String::class.java)) {
                val address = args[0] as? String ?: return@hookAfter
                if (!isTargetAddress(address)) return@hookAfter
                recordBridgeStage(address, methodName.bridgeStage())
                value(address)?.let { result = it }
            }
        }.onFailure {
            ModuleLog.debug("MiLink", "optional $className.$methodName unavailable")
        }
    }

    private fun hookNoiseCommand(
        className: String,
        methodName: String,
        mode: NoiseMode,
        returnValue: Int,
    ) {
        runCatching {
            hookBefore(findMethod(className, methodName, BluetoothDevice::class.java)) {
                val device = args[0] as? BluetoothDevice
                if (adapterFor(device)?.capabilities?.noiseControl != true) {
                    return@hookBefore
                }
                rememberRuntimeOwner(className, instance)
                captureContext(instance)
                sendControl(ControlRequest.SetNoiseMode(mode), device)
                result = returnValue
            }
        }.onFailure {
            ModuleLog.debug("MiLink", "optional $className.$methodName command unavailable")
        }
    }

    private fun hookHeadsetInfo(
        methodName: String,
        value: (Any) -> Any?,
    ) {
        val method: Method = runCatching {
            findMethodByParamCount(
                "com.miui.headset.api.HeadsetInfo",
                methodName,
                0,
            )
        }.getOrElse {
            ModuleLog.debug("MiLink", "optional HeadsetInfo.$methodName unavailable")
            return
        }
        hookAfter(method) {
            val info = instance ?: return@hookAfter
            if (!isTargetHeadsetInfo(info)) return@hookAfter
            headsetAddress(info)?.let {
                recordBridgeStage(it, methodName.bridgeStage())
            }
            value(info)?.let { result = it }
        }
    }

    private fun registerStateReceiver(candidate: Context?) {
        if (candidate == null || receiverRegistered) return
        context = candidate.applicationContext ?: candidate
        context?.registerReceiver(
            object : BroadcastReceiver() {
                override fun onReceive(context: Context?, intent: Intent?) {
                    when (intent?.action) {
                        ModuleContract.ACTION_STATE_CHANGED ->
                            handleStateChanged(intent)

                        ModuleContract.ACTION_REQUEST_BRIDGE_STATUS -> {
                            val targetPackage = with(ModuleContract) {
                                intent.readReplyPackage()
                            }?.takeIf { it == ModuleContract.MODULE_PACKAGE } ?: return
                            publishCurrentBridgeStatus(targetPackage)
                        }
                    }
                }
            },
            IntentFilter().apply {
                addAction(ModuleContract.ACTION_STATE_CHANGED)
                addAction(ModuleContract.ACTION_REQUEST_BRIDGE_STATUS)
            },
            Context.RECEIVER_EXPORTED,
        )
        receiverRegistered = true
        requestState()
        ModuleLog.debug("MiLink", "state receiver registered")
    }

    private fun handleStateChanged(intent: Intent) {
        val incoming = with(ModuleContract) { intent.readState() } ?: return
        val sessionToken = with(ModuleContract) { intent.readSessionToken() } ?: return
        val previous = incoming.address
            ?.let(ProcessStateStore::knownSnapshot)
            ?: EarbudState()
        val state = ProcessStateStore.accept(intent) ?: return
        state.address?.let {
            val normalized = normalizeAddress(it)
            if (state.sessionActive) {
                knownAddresses += normalized
                observeStateAccepted(state, sessionToken)
            } else {
                synchronized(observationLock) {
                    sessionStages.remove(normalized)
                    pendingStages.remove(normalized)
                }
            }
        }
        notifyRuntimeChanged(previous, state)
    }

    private fun publishCurrentBridgeStatus(targetPackage: String) {
        context?.sendBroadcast(
            ModuleContract.bridgeRuntimeObserved(
                consumerProcess = Application.getProcessName(),
                targetPackage = targetPackage,
            ).addFlags(Intent.FLAG_RECEIVER_FOREGROUND),
        )
        ProcessStateStore.snapshots().forEach { state ->
            val address = state.address ?: return@forEach
            val token = ProcessStateStore.sessionToken(address) ?: return@forEach
            val stages = synchronized(observationLock) {
                sessionStages[normalizeAddress(address)]
                    ?.takeIf { it.sessionToken == token }
                    ?.stages
                    ?.toSet()
                    .orEmpty()
            } + BridgeStage.STATE_ACCEPTED
            stages.forEach { stage ->
                publishBridgeReceipt(state, token, targetPackage, stage)
            }
        }
    }

    private fun publishBridgeReceipt(
        state: EarbudState,
        sessionToken: String,
        targetPackage: String,
        stage: BridgeStage,
    ) {
        context?.sendBroadcast(
            ModuleContract.bridgeStateObserved(
                state = state,
                sessionToken = sessionToken,
                consumerProcess = Application.getProcessName(),
                targetPackage = targetPackage,
                stage = stage,
            ).addFlags(Intent.FLAG_RECEIVER_FOREGROUND),
        )
    }

    private fun observeStateAccepted(
        state: EarbudState,
        sessionToken: String,
    ) {
        val address = state.address ?: return
        val key = normalizeAddress(address)
        val pending = synchronized(observationLock) {
            val tracked = sessionStages[key]
                ?.takeIf { it.sessionToken == sessionToken }
                ?: SessionStages(sessionToken).also { sessionStages[key] = it }
            tracked.stages += BridgeStage.STATE_ACCEPTED
            pendingStages.remove(key)?.toSet().orEmpty()
        }
        publishBridgeReceipt(
            state,
            sessionToken,
            ModuleContract.MODULE_PACKAGE,
            BridgeStage.STATE_ACCEPTED,
        )
        pending.forEach { stage ->
            observeBridgeStage(state, sessionToken, stage)
        }
    }

    @SuppressLint("MissingPermission")
    private fun recordBridgeStage(
        device: BluetoothDevice,
        stage: BridgeStage,
    ) {
        val address = runCatching { device.address }.getOrNull() ?: return
        recordBridgeStage(address, stage)
    }

    private fun recordBridgeStage(
        address: String,
        stage: BridgeStage,
    ) {
        val state = ProcessStateStore.find(address)
        val sessionToken = ProcessStateStore.sessionToken(address)
        if (state == null || sessionToken == null) {
            synchronized(observationLock) {
                pendingStages
                    .getOrPut(normalizeAddress(address)) { mutableSetOf() }
                    .add(stage)
            }
            return
        }
        observeBridgeStage(state, sessionToken, stage)
    }

    private fun observeBridgeStage(
        state: EarbudState,
        sessionToken: String,
        stage: BridgeStage,
    ) {
        val address = state.address ?: return
        val isNew = synchronized(observationLock) {
            val key = normalizeAddress(address)
            val tracked = sessionStages[key]
                ?.takeIf { it.sessionToken == sessionToken }
                ?: SessionStages(sessionToken).also { sessionStages[key] = it }
            tracked.stages.add(stage)
        }
        if (!isNew) return
        publishBridgeReceipt(
            state,
            sessionToken,
            ModuleContract.MODULE_PACKAGE,
            stage,
        )
    }

    @SuppressLint("MissingPermission")
    private fun adapterFor(device: BluetoothDevice?) =
        device?.let { target ->
            val address = runCatching { target.address }.getOrNull()
            val stateAdapter = address
                ?.let(::stateForAddress)
                ?.modelId
                ?.let(EarbudAdapterRegistry::integratedById)
            val earbudAdapter = stateAdapter
                ?: EarbudAdapterRegistry
                    .forIntegration(target.toEarbudIdentity())
                    ?.takeIf { it.privateProtocolRequired }
            if (earbudAdapter != null && address != null) {
                knownAddresses += normalizeAddress(address)
            }
            earbudAdapter
        }

    @SuppressLint("MissingPermission")
    private fun adapterForAddress(address: String) =
        EarbudAdapterRegistry.integratedById(stateForAddress(address).modelId)
            ?: runCatching {
                context
                    ?.getSystemService(BluetoothManager::class.java)
                    ?.adapter
                    ?.getRemoteDevice(address)
            }.getOrNull()?.let { device ->
                EarbudAdapterRegistry.forIntegration(device.toEarbudIdentity())
            }?.takeIf { it.privateProtocolRequired }

    private fun adapterIdentity(device: BluetoothDevice?) =
        EarbudAdapterRegistry.integratedById(stateFor(device).modelId)
            ?: adapterFor(device)

    private fun adapterIdentity(state: EarbudState) =
        EarbudAdapterRegistry.integratedById(state.modelId)

    @SuppressLint("MissingPermission")
    private fun stateFor(device: BluetoothDevice?): EarbudState {
        val address = runCatching { device?.address }.getOrNull()
        return address?.let(::stateForAddress) ?: EarbudState()
    }

    @SuppressLint("MissingPermission")
    private fun isTargetAddress(address: String): Boolean {
        val normalized = normalizeAddress(address)
        if (normalized in knownAddresses ||
            ProcessStateStore.containsKnown(normalized)
        ) {
            return true
        }
        val device = runCatching {
            context
                ?.getSystemService(BluetoothManager::class.java)
                ?.adapter
                ?.getRemoteDevice(address)
        }.getOrNull() ?: return false
        val supported = EarbudAdapterRegistry
            .forIntegration(device.toEarbudIdentity())
            ?.privateProtocolRequired == true
        if (supported) knownAddresses += normalized
        return supported
    }

    private fun isTargetHeadsetInfo(info: Any?): Boolean {
        return headsetAddress(info) != null
    }

    private fun headsetAddress(info: Any?): String? {
        if (info == null) return null
        targetHeadsetAddresses[info]?.let { return it }
        val address = listOf("getAddress", "component1").firstNotNullOfOrNull { methodName ->
            val address = runCatching { callMethod(info, methodName) as? String }.getOrNull()
            address?.takeIf(::isTargetAddress)
        }
        if (address != null) targetHeadsetAddresses[info] = address
        return address
    }

    private fun stateForHeadsetInfo(info: Any?): EarbudState {
        val address = headsetAddress(info) ?: return EarbudState()
        return stateForAddress(address)
    }

    private fun stateForAddress(address: String): EarbudState {
        return ProcessStateStore.knownSnapshot(address)
    }

    private fun rememberRuntimeOwner(className: String, owner: Any?) {
        owner?.let { runtimeOwners += it }
        when (className) {
            "com.miui.headset.runtime.AncBatteryController" ->
                lastAncBatteryController = owner

            "com.miui.headset.runtime.ProfileContext" ->
                lastProfileContext = owner
        }
    }

    private fun captureContext(owner: Any?) {
        if (receiverRegistered) return
        val candidate = listOf(owner, lastProfileContext, lastAncBatteryController)
            .firstNotNullOfOrNull {
                runCatching { getObjectField(it, "context") as? Context }.getOrNull()
            }
        registerStateReceiver(candidate)
    }

    @SuppressLint("MissingPermission")
    private fun notifyRuntimeChanged(previous: EarbudState, snapshot: EarbudState) {
        val owners = synchronized(runtimeOwners) { runtimeOwners.toList() }
        val propertyListeners = propertyChangeListeners()
        if (owners.isEmpty() && propertyListeners.isEmpty()) return

        val capabilities = adapterIdentity(snapshot)?.capabilities ?: return
        val identityChanged =
            previous.modelId != snapshot.modelId ||
                previous.address != snapshot.address
        val connectionChanged = identityChanged || previous.connected != snapshot.connected
        val batteryChanged =
            capabilities.battery &&
                (identityChanged || previous.battery != snapshot.battery)
        val ancChanged =
            capabilities.noiseControl &&
                (identityChanged || previous.noiseMode != snapshot.noiseMode)
        if (!connectionChanged && !batteryChanged && !ancChanged) return

        val address = snapshot.address ?: return
        val device = runCatching {
            context
                ?.getSystemService(BluetoothManager::class.java)
                ?.adapter
                ?.getRemoteDevice(address)
        }.getOrNull() ?: return

        val updateTypes = buildSet {
            if (identityChanged || batteryChanged || connectionChanged) {
                add(HEADSET_PROPERTY_CHANGED)
            }
            if (identityChanged || ancChanged) add(8)
        }
        notifyHeadsetPropertyChanged(
            device = device,
            updateTypes = updateTypes,
            listeners = propertyListeners,
        )

        val battery = MiLinkStateCodec.batteryLevels(snapshot).toIntArray()
        val anc = MiLinkStateCodec.ancState(snapshot)
        val deviceId = adapterIdentity(snapshot)?.miLinkIdentity?.deviceId ?: return
        owners.forEach { owner ->
            val callbackCollections = listOf("mCallbacks", "callbacks")
                .mapNotNull { field ->
                    runCatching { getObjectField(owner, field) as? Collection<*> }
                        .getOrNull()
                }
            callbackCollections
                .flatMap { it.filterNotNull() }
                .distinctBy(System::identityHashCode)
                .forEach { callback ->
                    if (identityChanged) {
                        runCatching {
                            callMethod(callback, "onDeviceIdUpdate", device, deviceId)
                        }
                    }
                    if (batteryChanged) {
                        runCatching { callMethod(callback, "onBatteryLevel", device, battery) }
                    }
                    if (ancChanged) {
                        runCatching { callMethod(callback, "onAncStateChanged", device, anc) }
                        runCatching { callMethod(callback, "onReportAncState", device, anc) }
                    }
                    if (connectionChanged) runCatching {
                        callMethod(
                            callback,
                            "onConnectMmaStateChanged",
                            device,
                            snapshot.connected,
                        )
                    }
                }
        }
        if (snapshot.sessionActive) {
            recordBridgeStage(address, BridgeStage.RUNTIME_NOTIFIED)
        }
    }

    private fun notifyHeadsetPropertyChanged(
        device: BluetoothDevice,
        updateTypes: Set<Int>,
        additionalOwner: Any? = null,
        listeners: List<Any> = propertyChangeListeners(additionalOwner),
    ): Int {
        listeners.forEach { listener ->
            updateTypes.forEach { updateType ->
                runCatching { callMethod(listener, "invoke", device, updateType) }
                    .onFailure {
                        ModuleLog.warn(
                            "MiLink",
                            "headset property callback failed type=$updateType",
                            it,
                        )
                    }
            }
        }
        return listeners.size
    }

    private fun propertyChangeListeners(additionalOwner: Any? = null): List<Any> =
        listOf(additionalOwner, lastAncBatteryController, lastProfileContext)
            .filterNotNull()
            .mapNotNull { owner ->
                runCatching {
                    getObjectField(owner, "headsetPropertyChangeListener")
                }.getOrNull()
            }
            .distinctBy(System::identityHashCode)

    private fun requestState() {
        context?.sendBroadcast(
            ModuleContract.requestState(packageName)
                .addFlags(Intent.FLAG_RECEIVER_FOREGROUND),
        )
    }

    @SuppressLint("MissingPermission")
    private fun sendControl(request: ControlRequest, device: BluetoothDevice?) {
        val address = runCatching { device?.address }.getOrNull() ?: return
        val snapshot = ProcessStateStore.find(address) ?: return
        if (!snapshot.sessionActive) return
        val token = ProcessStateStore.sessionToken(address) ?: return
        context?.sendBroadcast(
            ModuleContract.control(request, address, token)
                .addFlags(Intent.FLAG_RECEIVER_FOREGROUND),
        )
        ModuleLog.debug("MiLink", "forwarded ${request.javaClass.simpleName}")
    }

    private fun normalizeAddress(address: String): String = address.uppercase()

    private fun String.bridgeStage(): BridgeStage = when (this) {
        "checkIsMiTWS",
        "getDeviceId",
        "isMiTWS",
        "getName",
        "component2",
        "component3",
        -> BridgeStage.IDENTITY_QUERIED

        else -> BridgeStage.CAPABILITIES_QUERIED
    }

    private companion object {
        const val HEADSET_OPERATION_SUCCESS = 100
        const val HEADSET_PROPERTY_CHANGED = 4
    }
}
