package dev.hyperears.integration

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DeviceLifecycleTest {
    @Test
    fun standardSessionIsOperationalWithoutPrivateTransportState() {
        val state = EarbudState(
            adapter = StandardEarbudAdapter().snapshot(),
            lifecycle = DeviceLifecycle(
                systemProfile = SystemProfileState.CONNECTED,
                privateTransport = PrivateTransportState.NOT_REQUIRED,
                protocolHandshake = ProtocolHandshakeState.NOT_REQUIRED,
            ),
        )

        assertTrue(state.sessionActive)
        assertTrue(state.connected)
        assertFalse(state.privateProtocolRequired)
        assertFalse(state.privateChannelConnected)
        assertFalse(state.handshakeAccepted)
    }

    @Test
    fun privateSessionRequiresBothTransportAndProtocolConfirmation() {
        val adapter = VivoEarbudAdapter().snapshot()
        val pending = EarbudState(
            adapter = adapter,
            lifecycle = DeviceLifecycle(
                systemProfile = SystemProfileState.CONNECTED,
                privateTransport = PrivateTransportState.CONNECTED,
                protocolHandshake = ProtocolHandshakeState.PENDING,
            ),
        )
        val ready = pending.copy(
            lifecycle = pending.lifecycle.copy(
                protocolHandshake = ProtocolHandshakeState.CONFIRMED,
            ),
        )

        assertFalse(pending.connected)
        assertTrue(pending.privateChannelConnected)
        assertFalse(pending.handshakeAccepted)
        assertTrue(ready.connected)
        assertTrue(ready.handshakeAccepted)
    }

    @Test
    fun standardAdapterDoesNotNeedAFabricatedProtocolConfirmation() {
        val state = EarbudState(
            adapter = StandardEarbudAdapter().snapshot(),
            lifecycle = DeviceLifecycle(
                systemProfile = SystemProfileState.CONNECTED,
                privateTransport = PrivateTransportState.NOT_REQUIRED,
                protocolHandshake = ProtocolHandshakeState.NOT_REQUIRED,
            ),
        )

        assertTrue(state.connected)
        assertFalse(state.handshakeAccepted)
    }

    @Test
    fun recoveryAndDormancyCannotBeMisreportedAsReady() {
        listOf(
            PrivateTransportState.CONNECTING,
            PrivateTransportState.RECOVERING,
            PrivateTransportState.DORMANT,
        ).forEach { transport ->
            val state = EarbudState(
                adapter = BoseEarbudAdapter().snapshot(),
                lifecycle = DeviceLifecycle(
                    systemProfile = SystemProfileState.CONNECTED,
                    privateTransport = transport,
                    protocolHandshake = ProtocolHandshakeState.PENDING,
                ),
            )
            assertTrue(state.sessionActive)
            assertFalse(state.connected)
        }
    }

    @Test
    fun disconnectedSystemProfileDominatesOtherwiseReadyPrivateState() {
        val state = EarbudState(
            adapter = BoseEarbudAdapter().snapshot(),
            lifecycle = DeviceLifecycle(
                systemProfile = SystemProfileState.DISCONNECTED,
                privateTransport = PrivateTransportState.CONNECTED,
                protocolHandshake = ProtocolHandshakeState.CONFIRMED,
            ),
        )

        assertFalse(state.sessionActive)
        assertFalse(state.connected)
    }
}
