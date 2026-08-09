package dev.hyperears.runtime

import android.os.SystemClock

/**
 * Coalesces idempotent full-state refresh requests without pacing user controls.
 *
 * Each device session owns one instance. UI surfaces and MiLink processes may request a refresh at
 * nearly the same time; only the first request within [intervalMs] reaches the private protocol.
 */
internal class RefreshRequestGate(
    private val intervalMs: Long,
    private val clock: () -> Long = SystemClock::elapsedRealtime,
) {
    private val lock = Any()
    private var lastAcceptedAt = Long.MIN_VALUE

    init {
        require(intervalMs >= 0L) { "Refresh interval cannot be negative" }
    }

    fun tryAcquire(): Boolean {
        val now = clock()
        return synchronized(lock) {
            if (
                lastAcceptedAt != Long.MIN_VALUE &&
                now >= lastAcceptedAt &&
                now - lastAcceptedAt < intervalMs
            ) {
                false
            } else {
                lastAcceptedAt = now
                true
            }
        }
    }
}
