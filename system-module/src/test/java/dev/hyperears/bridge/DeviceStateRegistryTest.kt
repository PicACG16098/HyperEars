package dev.hyperears.bridge

import dev.hyperears.integration.EarbudState
import dev.hyperears.integration.DeviceLifecycle
import dev.hyperears.integration.NoiseMode
import dev.hyperears.integration.PrivateTransportState
import dev.hyperears.integration.ProtocolHandshakeState
import dev.hyperears.integration.SystemProfileState
import dev.hyperears.integration.VivoTwsAir3ProAdapter
import dev.hyperears.integration.withNoiseMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class DeviceStateRegistryTest {
    @Test
    fun keepsIndependentStateAndTokenForEachAddress() {
        val registry = DeviceStateRegistry()
        val first = activeState(FIRST_ADDRESS, revision = 1)
        val second = activeState(SECOND_ADDRESS, revision = 7)

        registry.accept(first, "first-token")
        registry.accept(second, "second-token")

        assertSame(first, registry.state(FIRST_ADDRESS.lowercase()))
        assertSame(second, registry.state(SECOND_ADDRESS))
        assertEquals("first-token", registry.token(FIRST_ADDRESS))
        assertEquals("second-token", registry.token(SECOND_ADDRESS))
        assertEquals(2, registry.states().size)
        assertTrue(registry.contains(FIRST_ADDRESS))
        assertTrue(registry.contains(SECOND_ADDRESS))
    }

    @Test
    fun staleEndFromOldSessionCannotRemoveNewSession() {
        val registry = DeviceStateRegistry()
        val oldState = activeState(FIRST_ADDRESS, revision = 10)
        val newState = activeState(FIRST_ADDRESS, revision = 1)

        registry.accept(oldState, "old-token")
        registry.accept(newState, "new-token")
        val staleEnd = oldState.copy(
            lifecycle = DeviceLifecycle(),
            revision = 11,
        )

        assertNull(registry.accept(staleEnd, "old-token"))
        assertNull(
            registry.accept(
                oldState.copy(revision = 12),
                "old-token",
            ),
        )
        assertSame(newState, registry.state(FIRST_ADDRESS))
        assertEquals("new-token", registry.token(FIRST_ADDRESS))
    }

    @Test
    fun currentSessionEndRemovesOnlyThatAddress() {
        val registry = DeviceStateRegistry()
        registry.accept(activeState(FIRST_ADDRESS, 1), "first-token")
        val second = activeState(SECOND_ADDRESS, 1)
        registry.accept(second, "second-token")

        val ended = activeState(FIRST_ADDRESS, 2).copy(
            lifecycle = DeviceLifecycle(),
        )
        assertSame(ended, registry.accept(ended, "first-token"))

        assertFalse(registry.contains(FIRST_ADDRESS))
        assertNull(registry.state(FIRST_ADDRESS))
        assertNull(registry.token(FIRST_ADDRESS))
        assertTrue(registry.containsKnown(FIRST_ADDRESS))
        assertSame(ended, registry.knownState(FIRST_ADDRESS))
        assertEquals("first-token", registry.knownToken(FIRST_ADDRESS))
        assertSame(second, registry.primaryState())
    }

    @Test
    fun dormantStateCannotAuthorizeControlsButSeedsTheNextSession() {
        val registry = DeviceStateRegistry()
        val active = activeState(FIRST_ADDRESS, 4).withNoiseMode(NoiseMode.ANC)
        val ended = active.copy(
            lifecycle = DeviceLifecycle(),
            revision = 5,
        )

        registry.accept(active, "old-token")
        registry.accept(ended, "old-token")

        assertFalse(registry.contains(FIRST_ADDRESS))
        assertNull(registry.state(FIRST_ADDRESS))
        assertNull(registry.token(FIRST_ADDRESS))
        assertSame(ended, registry.knownState(FIRST_ADDRESS))

        val restarted = ended.copy(
            lifecycle = activeLifecycle,
            revision = 6,
        )
        assertSame(restarted, registry.accept(restarted, "new-token"))
        assertSame(restarted, registry.state(FIRST_ADDRESS))
        assertSame(restarted, registry.knownState(FIRST_ADDRESS))
        assertEquals("new-token", registry.token(FIRST_ADDRESS))
    }

    @Test
    fun rejectsRevisionRegressionWithinSameSession() {
        val registry = DeviceStateRegistry()
        val newest = activeState(FIRST_ADDRESS, revision = 9)
        registry.accept(newest, "token")

        assertNull(
            registry.accept(
                newest.copy(revision = 8),
                "token",
            ),
        )
        assertSame(newest, registry.state(FIRST_ADDRESS))
    }

    private fun activeState(address: String, revision: Long) = EarbudState(
        adapter = VivoTwsAir3ProAdapter().snapshot(),
        address = address,
        lifecycle = activeLifecycle,
        revision = revision,
    )

    private val activeLifecycle = DeviceLifecycle(
        systemProfile = SystemProfileState.CONNECTED,
        privateTransport = PrivateTransportState.CONNECTED,
        protocolHandshake = ProtocolHandshakeState.CONFIRMED,
    )

    private companion object {
        const val FIRST_ADDRESS = "00:11:22:33:44:55"
        const val SECOND_ADDRESS = "AA:BB:CC:DD:EE:FF"
    }
}
