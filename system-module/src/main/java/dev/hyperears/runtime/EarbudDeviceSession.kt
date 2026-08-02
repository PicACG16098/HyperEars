package dev.hyperears.runtime

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.Context
import android.os.SystemClock
import dev.hyperears.hook.ModuleLog
import dev.hyperears.hook.maskBluetoothAddress
import dev.hyperears.integration.ControlRequest
import dev.hyperears.integration.BatterySource
import dev.hyperears.integration.AdapterActivation
import dev.hyperears.integration.AdapterIoResult
import dev.hyperears.integration.AdapterRuntimeState
import dev.hyperears.integration.AdapterSnapshot
import dev.hyperears.integration.DeviceLifecycle
import dev.hyperears.integration.EarbudAdapter
import dev.hyperears.integration.HandshakeResult
import dev.hyperears.integration.PrivateTransportState
import dev.hyperears.integration.ProtocolEvent
import dev.hyperears.integration.ProtocolHandshakeState
import dev.hyperears.integration.SystemProfileState
import dev.hyperears.integration.TransportReadiness
import java.io.Closeable
import java.io.IOException
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeout

/**
 * One device-scoped private-protocol session.
 *
 * The object is created when the system profile connects and destroyed when that profile
 * disconnects. A channel loss only restarts the transport loop; it never creates a second
 * logical device session.
 */
internal class EarbudDeviceSession(
    private val context: Context,
    val device: BluetoothDevice,
    val deviceName: String,
    val address: String,
    initialAdapter: EarbudAdapter,
    private val connectionCoordinator: ConnectionAttemptCoordinator,
    private val listener: Listener,
    private val channelFactory: EarbudChannelFactory = AndroidEarbudChannelFactory,
) : Closeable {
    fun interface Listener {
        fun onSnapshotChanged(session: EarbudDeviceSession, snapshot: Snapshot)
    }

    data class Snapshot(
        val adapter: AdapterSnapshot,
        val runtime: AdapterRuntimeState,
        val lifecycle: DeviceLifecycle,
    )

    private val sessionJob = SupervisorJob()
    private val scope = CoroutineScope(sessionJob + Dispatchers.IO)
    private val closed = AtomicBoolean()
    private val connectionJobLock = Any()
    private val transportLock = Any()
    private val transactionMutex = Mutex()
    private val unknownFrameLogTimes = mutableMapOf<Int, Long>()

    @Volatile
    private var connectionJob: Job? = null

    @Volatile
    private var channel: EarbudChannel? = null

    @Volatile
    var adapter: EarbudAdapter = initialAdapter
        private set

    @Volatile
    private var lifecycle = DeviceLifecycle(
        systemProfile = SystemProfileState.CONNECTED,
        privateTransport = if (initialAdapter.privateProtocolRequired) {
            PrivateTransportState.IDLE
        } else {
            PrivateTransportState.NOT_REQUIRED
        },
        protocolHandshake = initialAdapter.initialHandshakeState(),
    )

    fun snapshot(): Snapshot = Snapshot(
        adapter = adapter.snapshot(),
        runtime = adapter.runtimeState(),
        lifecycle = lifecycle,
    )

    fun start() {
        if (adapter.privateProtocolRequired) {
            requestConnection()
        } else {
            publishSnapshot()
        }
        publishCachedSystemBattery()
    }

    fun onSystemBatteryChanged(percent: Int?) {
        if (closed.get() || adapter.effectiveBatterySource() != BatterySource.SYSTEM_AGGREGATE) {
            return
        }
        if (adapter.onSystemBatteryChanged(percent)) publishSnapshot()
    }

    /**
     * Starts one bounded connection cycle.
     *
     * Re-register and explicit refresh events may wake a dormant session, but duplicate
     * requests never create concurrent socket attempts for the same device.
     */
    fun requestConnection(): Boolean {
        if (closed.get()) return false
        if (!adapter.privateProtocolRequired) return true
        var createdNewJob = false
        val job = synchronized(connectionJobLock) {
            if (closed.get()) return false
            if (channel != null || connectionJob?.isActive == true) return true
            scope.launch(start = CoroutineStart.LAZY) {
                runConnectionCycle()
            }.also { created ->
                createdNewJob = true
                connectionJob = created
                created.invokeOnCompletion {
                    synchronized(connectionJobLock) {
                        if (connectionJob === created) connectionJob = null
                    }
                }
            }
        }
        if (createdNewJob) {
            updateLifecycle(
                privateTransport = PrivateTransportState.CONNECTING,
                protocolHandshake = adapter.initialHandshakeState(),
            )
        }
        job.start()
        return true
    }

    fun execute(request: ControlRequest): Boolean {
        if (closed.get()) return false
        if (!adapter.privateProtocolRequired) {
            return request === ControlRequest.Refresh
        }
        if (request is ControlRequest.SetNoiseMode &&
            (
                !adapter.effectiveCapabilities().noiseControl ||
                    request.mode !in adapter.effectiveSupportedNoiseModes()
                )
        ) {
            return false
        }
        val activeChannel = channel ?: return false
        scope.launch {
            runCatching {
                transactionMutex.withLock {
                    val result = adapter.executeControl(request)
                    if (!result.accepted) return@withLock
                    sendCommands(
                        activeChannel = activeChannel,
                        commands = result.commands,
                        gapMs = COMMAND_GAP_MS,
                        description = request.description(),
                    )
                    if (result.stateChanged) publishSnapshot()
                    val readback = result.readback
                    if (readback.isNotEmpty()) {
                        delay(CONTROL_READBACK_DELAY_MS)
                        sendCommands(
                            activeChannel = activeChannel,
                            commands = readback,
                            gapMs = COMMAND_GAP_MS,
                            description = "${request.description()} readback",
                        )
                    }
                }
            }.onFailure {
                if (it !is CancellationException) {
                    ModuleLog.warn(
                        COMPONENT,
                        "control write failed: ${request.javaClass.simpleName}",
                        it,
                    )
                    activeChannel.close()
                }
            }
        }
        return true
    }

    override fun close() {
        if (!closed.compareAndSet(false, true)) return
        val activeChannel = synchronized(transportLock) {
            channel.also {
                channel = null
                adapter.resetProtocolSession()
            }
        }
        activeChannel?.close()
        synchronized(connectionJobLock) {
            connectionJob.also { connectionJob = null }
        }?.cancel()
        scope.cancel()
        lifecycle = lifecycle.copy(
            systemProfile = SystemProfileState.DISCONNECTED,
            privateTransport = if (adapter.privateProtocolRequired) {
                PrivateTransportState.IDLE
            } else {
                PrivateTransportState.NOT_REQUIRED
            },
            protocolHandshake = if (adapter.privateProtocolRequired) {
                adapter.initialHandshakeState()
            } else {
                ProtocolHandshakeState.NOT_REQUIRED
            },
        )
        ModuleLog.debug(COMPONENT, "closed ${maskBluetoothAddress(address)}")
    }

    @SuppressLint("MissingPermission")
    private suspend fun runConnectionCycle() {
        var consecutiveFailures = 0
        cancelDiscoveryOnce()

        while (currentCoroutineContext().isActive && !closed.get()) {
            var connectedAt = 0L
            try {
                val connectedTransport = connectFirstTransport()
                val activeChannel = connectedTransport.channel
                currentCoroutineContext().ensureActive()
                if (closed.get()) {
                    activeChannel.close()
                    return
                }

                synchronized(transportLock) {
                    channel = activeChannel
                }
                connectedAt = SystemClock.elapsedRealtime()
                ModuleLog.debug(
                    COMPONENT,
                    "channel connected endpoint=${activeChannel.endpointId} " +
                        "address=${maskBluetoothAddress(address)}",
                )

                updateLifecycle(
                    privateTransport = PrivateTransportState.CONNECTED,
                    protocolHandshake = adapter.readyHandshakeState(),
                )
                readFrames(activeChannel)
                throw IOException("vendor channel stream ended")
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Throwable) {
                clearTransport()
                if (closed.get() || !currentCoroutineContext().isActive) return
                val stableConnection =
                    connectedAt != 0L &&
                        SystemClock.elapsedRealtime() - connectedAt >= STABLE_CONNECTION_MS
                if (stableConnection) consecutiveFailures = 0
                val waitMs = ChannelRecoveryPolicy.delayBeforeRetry(consecutiveFailures)
                if (waitMs == null) {
                    updateLifecycle(
                        privateTransport = PrivateTransportState.DORMANT,
                        protocolHandshake = adapter.initialHandshakeState(),
                    )
                    ModuleLog.warn(
                        COMPONENT,
                        "channel dormant after bounded recovery: ${error.message}",
                    )
                    return
                }
                consecutiveFailures += 1
                updateLifecycle(
                    privateTransport = PrivateTransportState.RECOVERING,
                    protocolHandshake = adapter.initialHandshakeState(),
                )
                ModuleLog.warn(
                    COMPONENT,
                    "channel unavailable; retry in ${waitMs}ms: ${error.message}",
                )
                delay(waitMs)
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun cancelDiscoveryOnce() {
        runCatching {
            context.getSystemService(BluetoothManager::class.java)
                ?.adapter
                ?.takeIf { it.isDiscovering }
                ?.cancelDiscovery()
        }
    }

    private fun publishCachedSystemBattery() {
        if (adapter.effectiveBatterySource() != BatterySource.SYSTEM_AGGREGATE) return
        onSystemBatteryChanged(BluetoothSystemBattery.cachedLevel(device))
    }

    private suspend fun connectFirstTransport(): ConnectedTransport {
        return connectionCoordinator.run {
            connectFirstTransportSerially()
        }
    }

    private suspend fun connectFirstTransportSerially(): ConnectedTransport {
        var lastError: Throwable? = null
        adapter.transports.forEach { transport ->
            currentCoroutineContext().ensureActive()
            val candidate = channelFactory.create(context, device, transport)
            adapter.resetProtocolSession()
            synchronized(transportLock) { channel = candidate }
            try {
                withTimeout(CONNECT_TIMEOUT_MS) { candidate.connect() }
                updateLifecycle(
                    privateTransport = PrivateTransportState.CONNECTED,
                    protocolHandshake = adapter.initialHandshakeState(),
                )
                val initial = adapter.beginHandshake()
                sendCommands(
                    activeChannel = candidate,
                    commands = initial.commands,
                    gapMs = INITIAL_COMMAND_GAP_MS,
                    description = "adapter handshake",
                )
                when (val handshake = initial.handshake) {
                    HandshakeResult.Ready, null -> return ConnectedTransport(candidate)
                    HandshakeResult.AwaitingEvidence -> awaitAcceptedHandshake(candidate)
                    is HandshakeResult.Replace -> applyReplacement(handshake, candidate)
                    HandshakeResult.Rejected -> throw IOException("adapter handshake rejected")
                }
                return ConnectedTransport(candidate)
            } catch (error: Throwable) {
                adapter.resetProtocolSession()
                candidate.close()
                synchronized(transportLock) {
                    if (channel === candidate) channel = null
                }
                if (error is CancellationException &&
                    (!currentCoroutineContext().isActive || closed.get())
                ) {
                    throw error
                }
                lastError = error
                ModuleLog.debug(
                    COMPONENT,
                    "transport ${transport.id} failed: ${error.javaClass.simpleName}",
                )
            }
        }
        throw IOException("all vendor-channel endpoints failed", lastError)
    }

    private suspend fun awaitAcceptedHandshake(
        candidate: EarbudChannel,
    ) = withTimeout(PROTOCOL_HANDSHAKE_TIMEOUT_MS) {
        val buffer = ByteArray(READ_BUFFER_SIZE)
        while (true) {
            val count = candidate.read(buffer)
            if (count < 0) throw IOException("vendor channel ended before protocol handshake")
            offerAdapterBytes(candidate, buffer.copyOf(count))
            if (lifecycle.protocolReady) return@withTimeout
        }
    }

    private suspend fun readFrames(
        activeChannel: EarbudChannel,
    ) {
        val buffer = ByteArray(READ_BUFFER_SIZE)
        while (currentCoroutineContext().isActive && !closed.get()) {
            val count = activeChannel.read(buffer)
            if (count < 0) return
            offerAdapterBytes(activeChannel, buffer.copyOf(count))
        }
    }

    private suspend fun offerAdapterBytes(
        activeChannel: EarbudChannel,
        bytes: ByteArray,
    ): AdapterIoResult = transactionMutex.withLock {
        val result = adapter.receive(bytes)
        if (result.commands.isNotEmpty()) {
            sendCommands(
                activeChannel = activeChannel,
                commands = result.commands,
                gapMs = 0L,
                description = "adapter response",
            )
        }
        result.unknownFrames.forEach(::logUnknownFrame)
        var publishRequired = result.stateChanged
        when (val handshake = result.handshake) {
            HandshakeResult.Ready -> {
                publishRequired = setLifecycle(
                    protocolHandshake = adapter.readyHandshakeState(),
                ) || publishRequired
            }

            HandshakeResult.Rejected -> {
                setLifecycle(protocolHandshake = ProtocolHandshakeState.REJECTED)
                publishSnapshot()
                throw IOException("protocol rejected active adapter")
            }

            is HandshakeResult.Replace -> {
                applyReplacement(handshake, activeChannel)
                publishRequired = true
                when (handshake.activation) {
                    AdapterActivation.KEEP_CHANNEL_READY -> {
                        publishRequired = setLifecycle(
                            protocolHandshake = adapter.readyHandshakeState(),
                        ) || publishRequired
                    }

                    AdapterActivation.RESTART_ON_CURRENT_CHANNEL -> {
                        publishRequired = setLifecycle(
                            protocolHandshake = adapter.initialHandshakeState(),
                        ) || publishRequired
                        val restart = adapter.beginHandshake()
                        sendCommands(
                            activeChannel,
                            restart.commands,
                            INITIAL_COMMAND_GAP_MS,
                            "replacement handshake",
                        )
                        when (restart.handshake) {
                            HandshakeResult.Ready, null -> {
                                publishRequired = setLifecycle(
                                    protocolHandshake = adapter.readyHandshakeState(),
                                ) || publishRequired
                            }

                            HandshakeResult.AwaitingEvidence -> Unit
                            HandshakeResult.Rejected -> {
                                setLifecycle(protocolHandshake = ProtocolHandshakeState.REJECTED)
                                publishSnapshot()
                                throw IOException("replacement adapter handshake rejected")
                            }

                            is HandshakeResult.Replace ->
                                throw IOException("nested adapter replacement is not supported")
                        }
                    }

                    AdapterActivation.RECONNECT ->
                        throw IOException("adapter replacement requires reconnect")
                }
            }

            HandshakeResult.AwaitingEvidence, null -> Unit
        }
        if (publishRequired) publishSnapshot()
        result
    }

    private fun applyReplacement(
        replacement: HandshakeResult.Replace,
        activeChannel: EarbudChannel,
    ) {
        if (channel !== activeChannel) throw CancellationException("stale adapter replacement")
        adapter = replacement.adapter
    }

    private fun updateLifecycle(
        systemProfile: SystemProfileState = lifecycle.systemProfile,
        privateTransport: PrivateTransportState = lifecycle.privateTransport,
        protocolHandshake: ProtocolHandshakeState = lifecycle.protocolHandshake,
    ) {
        if (setLifecycle(systemProfile, privateTransport, protocolHandshake)) publishSnapshot()
    }

    private fun setLifecycle(
        systemProfile: SystemProfileState = lifecycle.systemProfile,
        privateTransport: PrivateTransportState = lifecycle.privateTransport,
        protocolHandshake: ProtocolHandshakeState = lifecycle.protocolHandshake,
    ): Boolean {
        val next = DeviceLifecycle(systemProfile, privateTransport, protocolHandshake)
        if (next == lifecycle) return false
        lifecycle = next
        return true
    }

    private fun publishSnapshot() {
        listener.onSnapshotChanged(this, snapshot())
    }

    private suspend fun sendCommands(
        activeChannel: EarbudChannel,
        commands: List<ByteArray>,
        gapMs: Long,
        description: String,
    ) {
        commands.forEachIndexed { index, command ->
            currentCoroutineContext().ensureActive()
            if (closed.get() || channel !== activeChannel) {
                throw CancellationException("stale vendor-channel writer")
            }
            activeChannel.write(command)
            ModuleLog.debug(
                COMPONENT,
                "$description wrote bytes=${command.toHex()}",
            )
            if (index != commands.lastIndex) delay(gapMs)
        }
    }

    private fun clearTransport() {
        val oldChannel = synchronized(transportLock) {
            channel.also {
                channel = null
                adapter.resetProtocolSession()
            }
        }
        oldChannel?.close()
    }

    private fun logUnknownFrame(event: ProtocolEvent.UnknownFrame) {
        val key = (event.vendor shl 16) or event.command
        val now = SystemClock.elapsedRealtime()
        val last = unknownFrameLogTimes[key] ?: Long.MIN_VALUE
        if (now - last < UNKNOWN_FRAME_LOG_INTERVAL_MS) return
        unknownFrameLogTimes[key] = now
        ModuleLog.debug(
            COMPONENT,
            "unmapped frame vendor=%04X command=%04X bytes=%d".format(
                event.vendor,
                event.command,
                event.payloadSize,
            ),
        )
    }

    private fun ControlRequest.description(): String = when (this) {
        ControlRequest.Refresh -> "refresh"
        is ControlRequest.SetNoiseMode -> "noise=${mode.name}"
    }

    private fun ByteArray.toHex(): String =
        joinToString(separator = " ") { "%02X".format(it.toInt() and 0xFF) }

    private fun EarbudAdapter.initialHandshakeState(): ProtocolHandshakeState =
        if (privateProtocolRequired && transportReadiness == TransportReadiness.PROTOCOL_HANDSHAKE) {
            ProtocolHandshakeState.PENDING
        } else {
            ProtocolHandshakeState.NOT_REQUIRED
        }

    private fun EarbudAdapter.readyHandshakeState(): ProtocolHandshakeState =
        if (privateProtocolRequired && transportReadiness == TransportReadiness.PROTOCOL_HANDSHAKE) {
            ProtocolHandshakeState.CONFIRMED
        } else {
            ProtocolHandshakeState.NOT_REQUIRED
        }

    private companion object {
        data class ConnectedTransport(
            val channel: EarbudChannel,
        )

        const val COMPONENT = "DeviceSession"
        const val CONNECT_TIMEOUT_MS = 60_000L
        const val PROTOCOL_HANDSHAKE_TIMEOUT_MS = 2_500L
        const val INITIAL_COMMAND_GAP_MS = 150L
        const val COMMAND_GAP_MS = 120L
        const val CONTROL_READBACK_DELAY_MS = 120L
        const val STABLE_CONNECTION_MS = 30_000L
        const val UNKNOWN_FRAME_LOG_INTERVAL_MS = 5 * 60_000L
        const val READ_BUFFER_SIZE = 1_024
    }
}
