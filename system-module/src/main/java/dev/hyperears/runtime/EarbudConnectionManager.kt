package dev.hyperears.runtime

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.content.Context
import dev.hyperears.bridge.StateBroadcaster
import dev.hyperears.hook.ModuleLog
import dev.hyperears.hook.maskBluetoothAddress
import dev.hyperears.integration.ControlRequest
import dev.hyperears.integration.EarbudAdapterRegistry
import dev.hyperears.integration.EarbudEvent
import dev.hyperears.integration.EarbudState
import dev.hyperears.integration.EarbudStateReducer
import java.io.Closeable
import java.util.Locale
import java.util.UUID

/**
 * Address-keyed owner of device sessions and the states exposed to system consumers.
 *
 * The shape mirrors Xiaomi's connected-device manager: every registered device owns an
 * independent logical session, while physical channel connection attempts are serialized by
 * a shared coordinator.
 */
internal class EarbudConnectionManager(
    context: Context,
) : Closeable {
    data class Snapshot(
        val state: EarbudState,
        val sessionToken: String,
    )

    private data class SessionRecord(
        val session: EarbudDeviceSession,
        val token: String,
        var state: EarbudState,
    )

    private sealed interface Registration {
        data class Existing(val record: SessionRecord) : Registration
        data class Created(val record: SessionRecord) : Registration
    }

    private data class Removal(
        val record: SessionRecord,
        val finalSnapshot: Snapshot,
    )

    private val appContext = context.applicationContext ?: context
    private val lifecycleLock = Any()
    private val sessions = linkedMapOf<String, SessionRecord>()
    private val knownDevices = linkedMapOf<String, Snapshot>()
    private val lastRevisions = mutableMapOf<String, Long>()
    private val connectionCoordinator = ConnectionAttemptCoordinator()

    @Volatile
    private var closed = false

    fun snapshots(): List<Snapshot> = synchronized(lifecycleLock) {
        knownDevices.values.toList()
    }

    @SuppressLint("MissingPermission")
    fun registerDevice(device: BluetoothDevice): Boolean {
        if (closed) return false
        val identity = device.toEarbudIdentity()
        val name = identity.deviceName
        val earbudAdapter = EarbudAdapterRegistry.forIntegration(identity) ?: return false
        val address = runCatching { device.address }.getOrNull() ?: return false
        val key = normalizeAddress(address)

        val registration = synchronized(lifecycleLock) {
            if (closed) return false
            sessions[key]?.let(Registration::Existing) ?: run {
                val initialState = knownDevices[key]
                    ?.state
                    ?.copy(
                        sessionActive = false,
                        connected = false,
                        handshakeAccepted = false,
                        revision = lastRevisions[key] ?: 0,
                    )
                    ?: EarbudState(
                        revision = lastRevisions[key] ?: 0,
                    )
                val session = EarbudDeviceSession(
                    context = appContext,
                    device = device,
                    deviceName = name ?: earbudAdapter.displayName,
                    address = address,
                    earbudAdapter = earbudAdapter,
                    connectionCoordinator = connectionCoordinator,
                    listener = ::onSessionEvent,
                )
                val state = EarbudStateReducer.reduce(
                    initialState,
                    EarbudEvent.SessionStarted(
                        modelId = earbudAdapter.id,
                        deviceName = session.deviceName,
                        address = address,
                        privateProtocolRequired = earbudAdapter.privateProtocolRequired,
                    ),
                )
                val record = SessionRecord(
                    session = session,
                    token = UUID.randomUUID().toString(),
                    state = state,
                )
                sessions[key] = record
                lastRevisions[key] = state.revision
                knownDevices[key] = Snapshot(state, record.token)
                Registration.Created(record)
            }
        }

        return when (registration) {
            is Registration.Existing -> {
                registration.record.session.requestConnection()
                ModuleLog.debug(
                    COMPONENT,
                    "reconnect requested for ${maskBluetoothAddress(address)}",
                )
                true
            }

            is Registration.Created -> {
                publish(registration.record)
                registration.record.session.start()
                ModuleLog.debug(
                    COMPONENT,
                    "registered ${earbudAdapter.id} at ${maskBluetoothAddress(address)}",
                )
                true
            }
        }
    }

    fun unregisterDevice(device: BluetoothDevice?): Boolean {
        val address = if (device == null) {
            null
        } else {
            runCatching { device.address }.getOrNull() ?: return false
        }
        val removals = synchronized(lifecycleLock) {
            removeLocked(address)
        }
        finishRemovals(removals)
        return removals.isNotEmpty()
    }

    fun execute(
        request: ControlRequest,
        address: String,
        sessionToken: String,
    ): Boolean {
        val target = synchronized(lifecycleLock) {
            sessions[normalizeAddress(address)]?.takeIf {
                it.token == sessionToken &&
                    it.state.sessionActive &&
                    it.state.address.equals(address, ignoreCase = true)
            }?.let { it to it.state.connected }
        } ?: return false
        val (record, connected) = target

        if (request is ControlRequest.SetNoiseMode &&
            !record.session.earbudAdapter.capabilities.noiseControl
        ) {
            return false
        }
        if (request is ControlRequest.Refresh && !connected) {
            return record.session.requestConnection()
        }
        if (!connected) return false
        return record.session.execute(request)
    }

    override fun close() {
        val removals = synchronized(lifecycleLock) {
            if (closed) return
            closed = true
            removeLocked(address = null)
        }
        finishRemovals(removals)
    }

    private fun onSessionEvent(session: EarbudDeviceSession, event: EarbudEvent) {
        val snapshot = synchronized(lifecycleLock) {
            val key = normalizeAddress(session.address)
            val record = sessions[key]?.takeIf { it.session === session }
                ?: return
            val previous = record.state
            val next = EarbudStateReducer.reduce(previous, event)
            if (next === previous) return
            record.state = next
            lastRevisions[key] = next.revision
            Snapshot(next, record.token).also {
                knownDevices[key] = it
            }
        }
        publish(snapshot)
    }

    private fun removeLocked(address: String?): List<Removal> {
        val keys = if (address == null) {
            sessions.keys.toList()
        } else {
            listOf(normalizeAddress(address))
        }
        return keys.mapNotNull { key ->
            val record = sessions.remove(key) ?: return@mapNotNull null
            val ended = EarbudStateReducer.reduce(record.state, EarbudEvent.SessionEnded)
            record.state = ended
            lastRevisions[key] = ended.revision
            val finalSnapshot = Snapshot(ended, record.token)
            knownDevices[key] = finalSnapshot
            Removal(record, finalSnapshot)
        }
    }

    private fun finishRemovals(removals: List<Removal>) {
        removals.forEach { removal ->
            removal.record.session.close()
            publish(removal.finalSnapshot)
            ModuleLog.debug(
                COMPONENT,
                "unregistered ${maskBluetoothAddress(removal.record.session.address)}",
            )
        }
    }

    private fun publish(record: SessionRecord) {
        publish(Snapshot(record.state, record.token))
    }

    private fun publish(snapshot: Snapshot) {
        StateBroadcaster.publish(
            appContext,
            snapshot.state,
            snapshot.sessionToken,
        )
        val state = snapshot.state
        ModuleLog.debug(
            COMPONENT,
            "state address=${maskBluetoothAddress(state.address)} rev=${state.revision} " +
                "active=${state.sessionActive} connected=${state.connected} " +
                "handshake=${state.handshakeAccepted} anc=${state.noiseMode} " +
                "battery=${state.battery.left.percent}/${state.battery.right.percent}/" +
                "${state.battery.case.percent}",
        )
    }

    private fun normalizeAddress(address: String): String =
        address.uppercase(Locale.ROOT)

    private companion object {
        const val COMPONENT = "ConnectManager"
    }
}
