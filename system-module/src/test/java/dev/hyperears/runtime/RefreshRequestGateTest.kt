package dev.hyperears.runtime

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RefreshRequestGateTest {
    @Test
    fun requestsInsideTheIntervalAreCoalesced() {
        var now = 1_000L
        val gate = RefreshRequestGate(intervalMs = 1_500L, clock = { now })

        assertTrue(gate.tryAcquire())
        now = 2_499L
        assertFalse(gate.tryAcquire())
        now = 2_500L
        assertTrue(gate.tryAcquire())
    }

    @Test
    fun aResetClockStartsANewInterval() {
        var now = 10_000L
        val gate = RefreshRequestGate(intervalMs = 1_500L, clock = { now })

        assertTrue(gate.tryAcquire())
        now = 25L
        assertTrue(gate.tryAcquire())
    }
}
