package dev.hyperears.bridge

import dev.hyperears.integration.EarbudState
import java.util.Locale

/**
 * Address-keyed state cache used inside a single consumer process.
 *
 * Active entries authorize controls and describe the current profile lifecycle. Known entries
 * retain the latest logical-device projection after that lifecycle ends so Xiaomi's HeadsetInfo
 * source can rebuild a circulation card without waiting for the private channel to reconnect.
 *
 * Session tokens isolate successive lifecycles for the same physical address. An end event from
 * an older session therefore cannot remove or replace a newer session that has already started.
 */
internal class DeviceStateRegistry {
    data class Entry(
        val state: EarbudState,
        val sessionToken: String,
    )

    private val lock = Any()
    private val entries = linkedMapOf<String, Entry>()
    private val knownEntries = linkedMapOf<String, Entry>()
    private val latestTokens = mutableMapOf<String, String>()
    private val latestRevisions = mutableMapOf<String, Long>()
    private val retiredTokens = mutableMapOf<String, ArrayDeque<String>>()
    private var primaryAddress: String? = null

    fun primaryState(): EarbudState = synchronized(lock) {
        primaryAddress?.let(entries::get)?.state
            ?: entries.values.lastOrNull()?.state
            ?: EarbudState()
    }

    fun state(address: String): EarbudState? = synchronized(lock) {
        entries[normalizeAddress(address)]?.state
    }

    fun token(address: String): String? = synchronized(lock) {
        entries[normalizeAddress(address)]?.sessionToken
    }

    fun knownState(address: String): EarbudState? = synchronized(lock) {
        knownEntries[normalizeAddress(address)]?.state
    }

    fun knownToken(address: String): String? = synchronized(lock) {
        knownEntries[normalizeAddress(address)]?.sessionToken
    }

    fun contains(address: String): Boolean = synchronized(lock) {
        normalizeAddress(address) in entries
    }

    fun containsKnown(address: String): Boolean = synchronized(lock) {
        normalizeAddress(address) in knownEntries
    }

    fun states(): List<EarbudState> = synchronized(lock) {
        entries.values.map(Entry::state)
    }

    fun clear() = synchronized(lock) {
        entries.clear()
        knownEntries.clear()
        latestTokens.clear()
        latestRevisions.clear()
        retiredTokens.clear()
        primaryAddress = null
    }

    fun accept(state: EarbudState, sessionToken: String): EarbudState? {
        val address = state.address?.takeIf(String::isNotBlank) ?: return null
        val key = normalizeAddress(address)
        return synchronized(lock) {
            val latestToken = latestTokens[key]
            val latestRevision = latestRevisions[key] ?: Long.MIN_VALUE

            if (retiredTokens[key]?.contains(sessionToken) == true) {
                return null
            }
            if (latestToken == sessionToken && state.revision < latestRevision) {
                return null
            }
            if (latestToken != null &&
                latestToken != sessionToken &&
                !state.sessionActive
            ) {
                return null
            }
            if (latestToken != null && latestToken != sessionToken) {
                rememberRetiredToken(key, latestToken)
            }

            latestTokens[key] = sessionToken
            latestRevisions[key] = state.revision
            knownEntries[key] = Entry(state, sessionToken)
            if (state.sessionActive) {
                entries[key] = Entry(state, sessionToken)
                primaryAddress = key
            } else {
                entries.remove(key)
                if (primaryAddress == key) {
                    primaryAddress = entries.keys.lastOrNull()
                }
            }
            state
        }
    }

    private fun normalizeAddress(address: String): String =
        address.uppercase(Locale.ROOT)

    private fun rememberRetiredToken(address: String, token: String) {
        val retired = retiredTokens.getOrPut(address) { ArrayDeque() }
        if (token in retired) return
        retired.addLast(token)
        while (retired.size > MAX_RETIRED_TOKENS_PER_ADDRESS) {
            retired.removeFirst()
        }
    }

    private companion object {
        const val MAX_RETIRED_TOKENS_PER_ADDRESS = 8
    }
}
