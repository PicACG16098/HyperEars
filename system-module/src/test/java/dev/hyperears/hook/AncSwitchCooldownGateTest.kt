package dev.hyperears.hook

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class AncSwitchCooldownGateTest {
    @Test
    fun cooldownIsIsolatedByNormalizedDeviceAddress() {
        var now = 1_000L
        var writes = 0
        val gate = AncSwitchCooldownGate { now }

        assertTrue(gate.runIfReady("aa:bb:cc:dd:ee:ff", 1_800L) { writes++ })
        now = 2_799L
        assertFalse(gate.runIfReady("AA:BB:CC:DD:EE:FF", 1_800L) { writes++ })
        assertTrue(gate.runIfReady("11:22:33:44:55:66", 1_800L) { writes++ })
        now = 2_800L
        assertTrue(gate.runIfReady("AA:BB:CC:DD:EE:FF", 1_800L) { writes++ })

        assertEquals(3, writes)
    }

    @Test
    fun zeroCooldownNeverRetainsState() {
        var writes = 0
        val gate = AncSwitchCooldownGate { 1_000L }

        repeat(3) {
            assertTrue(gate.runIfReady("AA:BB:CC:DD:EE:FF", 0L) { writes++ })
        }

        assertEquals(3, writes)
    }

    @Test
    fun failedActionDoesNotStartCooldown() {
        var now = 1_000L
        var writes = 0
        val gate = AncSwitchCooldownGate { now }

        assertThrows(IllegalStateException::class.java) {
            gate.runIfReady("AA:BB:CC:DD:EE:FF", 1_800L) {
                error("broadcast failed")
            }
        }
        now = 1_001L
        assertTrue(gate.runIfReady("AA:BB:CC:DD:EE:FF", 1_800L) { writes++ })

        assertEquals(1, writes)
    }
}
