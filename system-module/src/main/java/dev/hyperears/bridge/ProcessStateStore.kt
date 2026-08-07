package dev.hyperears.bridge

import android.content.Intent
import dev.hyperears.integration.EarbudState

object ProcessStateStore {
    private val registry = DeviceStateRegistry()

    fun snapshot(): EarbudState = registry.primaryState()

    fun snapshot(address: String): EarbudState =
        registry.state(address) ?: EarbudState()

    fun find(address: String): EarbudState? = registry.state(address)

    fun sessionToken(address: String): String? = registry.token(address)

    fun knownSnapshot(address: String): EarbudState =
        registry.knownState(address) ?: EarbudState()

    fun accept(intent: Intent): EarbudState? {
        val incoming = with(ModuleContract) { intent.readState() } ?: return null
        val token = with(ModuleContract) { intent.readSessionToken() } ?: return null
        return registry.accept(incoming, token)
    }

    fun contains(address: String): Boolean = registry.contains(address)

    fun containsKnown(address: String): Boolean = registry.containsKnown(address)

    fun snapshots(): List<EarbudState> = registry.states()

    fun clear() = registry.clear()
}
