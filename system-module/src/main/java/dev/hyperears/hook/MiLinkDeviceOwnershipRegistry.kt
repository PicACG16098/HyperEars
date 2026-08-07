package dev.hyperears.hook

import java.util.Locale

/**
 * Process-local ownership decided at MiLink's native headset-admission boundary.
 *
 * The first definitive admission is sticky for the lifetime of the MiLink process. This matters
 * because MiLink manager and service wrappers may call one another: an outer wrapper can observe
 * the positive value supplied by an inner HyperEars hook, but that value must not be mistaken for
 * a new native acceptance. HyperEars may supplement only after the original boundary rejected a
 * device and an active module session exists for the same Bluetooth address.
 */
internal class MiLinkDeviceOwnershipRegistry {
    enum class Owner {
        UNKNOWN,
        HYPEREARS,
        SYSTEM,
    }

    data class Decision(
        val owner: Owner,
        val systemOwnershipNewlyClaimed: Boolean,
        val hyperEarsOwnershipNewlyClaimed: Boolean,
    )

    private val lock = Any()
    private val owners = mutableMapOf<String, Owner>()

    fun observeNativeAdmission(
        address: String,
        originalResult: Any?,
        hyperEarsCandidateAvailable: Boolean,
    ): Decision = synchronized(lock) {
        val key = normalizeAddress(address)
        val previous = owners[key] ?: Owner.UNKNOWN
        val next = when {
            previous == Owner.SYSTEM -> Owner.SYSTEM
            previous == Owner.HYPEREARS -> Owner.HYPEREARS
            isNativeAdmissionAccepted(originalResult) -> Owner.SYSTEM
            hyperEarsCandidateAvailable -> Owner.HYPEREARS
            else -> previous
        }
        owners[key] = next
        Decision(
            owner = next,
            systemOwnershipNewlyClaimed =
                previous != Owner.SYSTEM && next == Owner.SYSTEM,
            hyperEarsOwnershipNewlyClaimed =
                previous != Owner.HYPEREARS && next == Owner.HYPEREARS,
        )
    }

    fun owner(address: String): Owner = synchronized(lock) {
        owners[normalizeAddress(address)] ?: Owner.UNKNOWN
    }

    fun isHyperEarsOwned(address: String): Boolean = owner(address) == Owner.HYPEREARS

    fun isSystemOwned(address: String): Boolean = owner(address) == Owner.SYSTEM

    fun claimSystemOwnership(address: String): Boolean = synchronized(lock) {
        val key = normalizeAddress(address)
        val previous = owners[key] ?: Owner.UNKNOWN
        owners[key] = Owner.SYSTEM
        previous != Owner.SYSTEM
    }

    fun clear() = synchronized(lock) {
        owners.clear()
    }

    private fun normalizeAddress(address: String): String = address.uppercase(Locale.ROOT)

    companion object {
        /** Supports the Boolean and numeric return shapes used by different MiLink releases. */
        fun isNativeAdmissionAccepted(result: Any?): Boolean = when (result) {
            is Boolean -> result
            is Number -> result.toLong() > 0L
            else -> false
        }
    }
}
