package dev.hyperears.runtime

/**
 * Finite recovery for an unexpected private-channel loss.
 *
 * Exhaustion deliberately returns null: the session then stays dormant until a new lifecycle
 * event or explicit refresh requests another bounded cycle.
 */
internal object ChannelRecoveryPolicy {
    private val delaysMs = longArrayOf(2_000, 10_000, 60_000)

    fun delayBeforeRetry(consecutiveFailures: Int): Long? =
        delaysMs.getOrNull(consecutiveFailures)
}
