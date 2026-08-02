package dev.hyperears.runtime

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.Context
import android.os.SystemClock
import dev.hyperears.hook.ModuleLog
import dev.hyperears.hook.maskBluetoothAddress
import dev.hyperears.integration.ControlRequest
import dev.hyperears.integration.ControlConfirmationPolicy
import dev.hyperears.integration.BatterySource
import dev.hyperears.integration.EarbudBattery
import dev.hyperears.integration.EarbudAdapter
import dev.hyperears.integration.EarbudAdapterRegistry
import dev.hyperears.integration.EarbudEvent
import dev.hyperears.integration.EarbudProtocol
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
    val earbudAdapter: EarbudAdapter,
    private val connectionCoordinator: ConnectionAttemptCoordinator,
    private val listener: Listener,
    private val channelFactory: EarbudChannelFactory = AndroidEarbudChannelFactory,
) : Closeable {
    fun interface Listener {
        fun onEvent(session: EarbudDeviceSession, event: EarbudEvent)
    }

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
    private var protocol: EarbudProtocol? = null

    @Volatile
    var effectiveAdapter: EarbudAdapter = earbudAdapter
        private set

    fun start() {
        if (earbudAdapter.privateProtocolRequired) {
            requestConnection()
        } else {
            listener.onEvent(this, EarbudEvent.AdapterReady)
        }
        publishCachedSystemBattery()
    }

    fun onSystemBatteryChanged(percent: Int?) {
        if (closed.get() || earbudAdapter.batterySource != BatterySource.SYSTEM_AGGREGATE) {
            return
        }
        listener.onEvent(
            this,
            EarbudEvent.BatteryChanged(EarbudBattery.fromSystemAggregate(percent)),
        )
    }

    /**
     * Starts one bounded connection cycle.
     *
     * Re-register and explicit refresh events may wake a dormant session, but duplicate
     * requests never create concurrent socket attempts for the same device.
     */
    fun requestConnection(): Boolean {
        if (closed.get()) return false
        if (!earbudAdapter.privateProtocolRequired) return true
        val job = synchronized(connectionJobLock) {
            if (closed.get()) return false
            if (channel != null || connectionJob?.isActive == true) return true
            scope.launch(start = CoroutineStart.LAZY) {
                runConnectionCycle()
            }.also { created ->
                connectionJob = created
                created.invokeOnCompletion {
                    synchronized(connectionJobLock) {
                        if (connectionJob === created) connectionJob = null
                    }
                }
            }
        }
        job.start()
        return true
    }

    fun execute(request: ControlRequest): Boolean {
        if (closed.get()) return false
        if (!earbudAdapter.privateProtocolRequired) {
            return request === ControlRequest.Refresh
        }
        if (request is ControlRequest.SetNoiseMode &&
            (
                !effectiveAdapter.capabilities.noiseControl ||
                    request.mode !in effectiveAdapter.supportedNoiseModes
                )
        ) {
            return false
        }
        val activeChannel = channel ?: return false
        val activeProtocol = protocol ?: return false
        scope.launch {
            runCatching {
                transactionMutex.withLock {
                    sendCommands(
                        activeChannel = activeChannel,
                        commands = activeProtocol.encode(request),
                        gapMs = COMMAND_GAP_MS,
                        description = request.description(),
                    )
                    publishWrittenStateIfConfigured(request)
                    val readback = activeProtocol.readback(request)
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
                protocol?.reset()
                protocol = null
            }
        }
        activeChannel?.close()
        synchronized(connectionJobLock) {
            connectionJob.also { connectionJob = null }
        }?.cancel()
        scope.cancel()
        ModuleLog.debug(COMPONENT, "closed ${maskBluetoothAddress(address)}")
    }

    @SuppressLint("MissingPermission")
    private suspend fun runConnectionCycle() {
        var consecutiveFailures = 0
        cancelDiscoveryOnce()

        while (currentCoroutineContext().isActive && !closed.get()) {
            var connectedAt = 0L
            var wasConnected = false
            try {
                val connectedTransport = connectFirstTransport()
                val activeChannel = connectedTransport.channel
                val activeProtocol = connectedTransport.protocol
                currentCoroutineContext().ensureActive()
                if (closed.get()) {
                    activeChannel.close()
                    return
                }

                synchronized(transportLock) {
                    channel = activeChannel
                    protocol = activeProtocol
                }
                connectedAt = SystemClock.elapsedRealtime()
                wasConnected = true
                listener.onEvent(this, EarbudEvent.ChannelConnected)
                ModuleLog.debug(
                    COMPONENT,
                    "channel connected endpoint=${activeChannel.endpointId} " +
                        "address=${maskBluetoothAddress(address)}",
                )

                if (!connectedTransport.initialReadSent) {
                    transactionMutex.withLock {
                        sendCommands(
                            activeChannel = activeChannel,
                            commands = activeProtocol.initialReadCommands(),
                            gapMs = INITIAL_COMMAND_GAP_MS,
                            description = "initial read",
                        )
                    }
                }
                connectedTransport.bufferedEvents.forEach { event ->
                    dispatchProtocolEvent(activeChannel, activeProtocol, event)
                }
                readFrames(activeChannel, activeProtocol)
                throw IOException("vendor channel stream ended")
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Throwable) {
                clearTransport()
                if (closed.get() || !currentCoroutineContext().isActive) return
                if (wasConnected) {
                    listener.onEvent(this, EarbudEvent.ChannelDisconnected)
                }

                val stableConnection =
                    connectedAt != 0L &&
                        SystemClock.elapsedRealtime() - connectedAt >= STABLE_CONNECTION_MS
                if (stableConnection) consecutiveFailures = 0
                val waitMs = ChannelRecoveryPolicy.delayBeforeRetry(consecutiveFailures)
                if (waitMs == null) {
                    ModuleLog.warn(
                        COMPONENT,
                        "channel dormant after bounded recovery: ${error.message}",
                    )
                    return
                }
                consecutiveFailures += 1
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
        if (earbudAdapter.batterySource != BatterySource.SYSTEM_AGGREGATE) return
        onSystemBatteryChanged(BluetoothSystemBattery.cachedLevel(device))
    }

    private suspend fun connectFirstTransport(): ConnectedTransport {
        return connectionCoordinator.run {
            connectFirstTransportSerially()
        }
    }

    private suspend fun connectFirstTransportSerially(): ConnectedTransport {
        var lastError: Throwable? = null
        earbudAdapter.transports.forEach { transport ->
            currentCoroutineContext().ensureActive()
            val candidate = channelFactory.create(context, device, transport)
            val candidateProtocol = requireNotNull(earbudAdapter.createProtocol()) {
                "integrated adapter ${earbudAdapter.id} has no private protocol"
            }
            synchronized(transportLock) { channel = candidate }
            try {
                withTimeout(CONNECT_TIMEOUT_MS) { candidate.connect() }
                if (earbudAdapter.transportReadiness == TransportReadiness.CONNECTED) {
                    return ConnectedTransport(
                        channel = candidate,
                        protocol = candidateProtocol,
                    )
                }

                sendCommands(
                    activeChannel = candidate,
                    commands = candidateProtocol.initialReadCommands(),
                    gapMs = INITIAL_COMMAND_GAP_MS,
                    description = "transport probe",
                )
                val bufferedEvents = awaitAcceptedHandshake(candidate, candidateProtocol)
                return ConnectedTransport(
                    channel = candidate,
                    protocol = candidateProtocol,
                    initialReadSent = true,
                    bufferedEvents = bufferedEvents,
                )
            } catch (error: Throwable) {
                candidateProtocol.reset()
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
        candidateProtocol: EarbudProtocol,
    ): List<EarbudEvent> = withTimeout(PROTOCOL_HANDSHAKE_TIMEOUT_MS) {
        val buffer = ByteArray(READ_BUFFER_SIZE)
        val bufferedEvents = mutableListOf<EarbudEvent>()
        while (true) {
            val count = candidate.read(buffer)
            if (count < 0) throw IOException("vendor channel ended before protocol handshake")
            val events = offerProtocolBytes(candidate, candidateProtocol, buffer.copyOf(count))
            events.forEach { event ->
                if (event is EarbudEvent.UnknownFrame) {
                    logUnknownFrame(event)
                } else {
                    bufferedEvents += event
                }
            }
            val handshake = events.filterIsInstance<EarbudEvent.Handshake>().lastOrNull()
            if (handshake != null) {
                if (!handshake.accepted) throw IOException("protocol handshake rejected")
                return@withTimeout bufferedEvents
            }
        }
        @Suppress("UNREACHABLE_CODE")
        bufferedEvents
    }

    private suspend fun readFrames(
        activeChannel: EarbudChannel,
        activeProtocol: EarbudProtocol,
    ) {
        val buffer = ByteArray(READ_BUFFER_SIZE)
        while (currentCoroutineContext().isActive && !closed.get()) {
            val count = activeChannel.read(buffer)
            if (count < 0) return
            offerProtocolBytes(activeChannel, activeProtocol, buffer.copyOf(count)).forEach { event ->
                dispatchProtocolEvent(activeChannel, activeProtocol, event)
            }
        }
    }

    private suspend fun offerProtocolBytes(
        activeChannel: EarbudChannel,
        activeProtocol: EarbudProtocol,
        bytes: ByteArray,
    ): List<EarbudEvent> {
        val events = activeProtocol.offer(bytes)
        val immediateCommands = activeProtocol.drainImmediateCommands()
        if (immediateCommands.isNotEmpty()) {
            transactionMutex.withLock {
                sendCommands(
                    activeChannel = activeChannel,
                    commands = immediateCommands,
                    gapMs = 0L,
                    description = "protocol response",
                )
            }
        }
        return events
    }

    private suspend fun dispatchProtocolEvent(
        activeChannel: EarbudChannel,
        activeProtocol: EarbudProtocol,
        event: EarbudEvent,
    ) {
        if (event is EarbudEvent.UnknownFrame) {
            logUnknownFrame(event)
            return
        }
        if (event is EarbudEvent.ModelIdentified) {
            EarbudAdapterRegistry.integratedById(event.modelId)?.let {
                effectiveAdapter = it
            }
        }
        listener.onEvent(this, event)
        val followUp = activeProtocol.followUpCommands(event)
        if (followUp.isNotEmpty()) {
            transactionMutex.withLock {
                sendCommands(
                    activeChannel = activeChannel,
                    commands = followUp,
                    gapMs = INITIAL_COMMAND_GAP_MS,
                    description = "model follow-up",
                )
            }
        }
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

    private fun publishWrittenStateIfConfigured(request: ControlRequest) {
        if (request !is ControlRequest.SetNoiseMode) return
        if (effectiveAdapter.noiseControlConfirmation ==
            ControlConfirmationPolicy.DEVICE_REPORT
        ) {
            return
        }
        listener.onEvent(
            this,
            EarbudEvent.NoiseModeChanged(
                mode = request.mode,
                acknowledged = false,
            ),
        )
    }

    private fun clearTransport() {
        val oldChannel = synchronized(transportLock) {
            channel.also {
                channel = null
                protocol?.reset()
                protocol = null
            }
        }
        oldChannel?.close()
    }

    private fun logUnknownFrame(event: EarbudEvent.UnknownFrame) {
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

    private companion object {
        data class ConnectedTransport(
            val channel: EarbudChannel,
            val protocol: EarbudProtocol,
            val initialReadSent: Boolean = false,
            val bufferedEvents: List<EarbudEvent> = emptyList(),
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
