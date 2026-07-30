package dev.hyperears.runtime

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ChannelRecoveryPolicyTest {
    @Test
    fun recoveryIsFinite() {
        assertEquals(2_000L, ChannelRecoveryPolicy.delayBeforeRetry(0))
        assertEquals(10_000L, ChannelRecoveryPolicy.delayBeforeRetry(1))
        assertEquals(60_000L, ChannelRecoveryPolicy.delayBeforeRetry(2))
        assertNull(ChannelRecoveryPolicy.delayBeforeRetry(3))
        assertNull(ChannelRecoveryPolicy.delayBeforeRetry(100))
    }
}
