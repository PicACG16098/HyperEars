package dev.hyperears.ui.dashboard

import dev.hyperears.bridge.BridgeReceipt
import dev.hyperears.bridge.BridgeStage
import dev.hyperears.integration.BoseHeadphonesAdapter
import dev.hyperears.integration.DeviceLifecycle
import dev.hyperears.integration.EarbudState
import dev.hyperears.integration.EarbudCapabilities
import dev.hyperears.integration.NoiseMode
import dev.hyperears.integration.PrivateTransportState
import dev.hyperears.integration.ProtocolHandshakeState
import dev.hyperears.integration.StandardEarbudAdapter
import dev.hyperears.integration.SystemProfileState
import dev.hyperears.integration.VivoEarbudAdapter
import dev.hyperears.integration.withNoiseMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DashboardUiStateTest {
    @Test
    fun reducerKeepsIndependentAddressKeyedSessions() {
        val first = activeState(FIRST_ADDRESS, "First")
        val second = activeState(SECOND_ADDRESS, "Second")

        val collection = DeviceSessionReducer.reduce(
            DeviceSessionReducer.reduce(DeviceSessionCollection(), first, "first-token"),
            second,
            "second-token",
        )

        assertEquals(2, collection.sessions.size)
        assertEquals("First", collection.sessions[FIRST_ADDRESS]?.state?.deviceName)
        assertEquals("Second", collection.sessions[SECOND_ADDRESS]?.state?.deviceName)
    }

    @Test
    fun disconnectedSystemLifecycleRemovesOnlyThatSession() {
        val both = DeviceSessionReducer.reduce(
            DeviceSessionReducer.reduce(
                DeviceSessionCollection(),
                activeState(FIRST_ADDRESS, "First"),
                "first-token",
            ),
            activeState(SECOND_ADDRESS, "Second"),
            "second-token",
        )

        val ended = activeState(FIRST_ADDRESS, "First").copy(lifecycle = DeviceLifecycle())
        val remaining = DeviceSessionReducer.reduce(both, ended, "first-token")

        assertFalse(FIRST_ADDRESS in remaining.sessions)
        assertTrue(SECOND_ADDRESS in remaining.sessions)
    }

    @Test
    fun phaseComesDirectlyFromExplicitTransportAndHandshakeStates() {
        val connecting = snapshot(
            lifecycle = privateLifecycle(PrivateTransportState.CONNECTING),
        )
        val recovering = snapshot(
            lifecycle = privateLifecycle(PrivateTransportState.RECOVERING),
        )
        val confirming = snapshot(
            lifecycle = privateLifecycle(
                PrivateTransportState.CONNECTED,
                ProtocolHandshakeState.PENDING,
            ),
        )

        assertEquals(DevicePhase.TRANSPORT_CONNECTING, connecting.phase)
        assertEquals(DevicePhase.TRANSPORT_RECOVERING, recovering.phase)
        assertEquals(DevicePhase.PROTOCOL_CONFIRMING, confirming.phase)
    }

    @Test
    fun projectorUsesAdapterSnapshotWithoutRegistryLookup() {
        val adapter = BoseHeadphonesAdapter().snapshot()
        val session = DeviceSessionSnapshot(
            state = EarbudState(
                adapter = adapter,
                deviceName = "Custom Bluetooth Name",
                address = FIRST_ADDRESS,
                lifecycle = privateLifecycle(
                    PrivateTransportState.CONNECTED,
                    ProtocolHandshakeState.CONFIRMED,
                ),
            ),
            sessionToken = "token",
        )

        val card = DeviceSessionUiProjector.project(session)

        assertEquals("Custom Bluetooth Name", card.deviceName)
        assertEquals(adapter.displayName, card.adapterName)
        assertEquals(adapter.id, card.adapterId)
        assertTrue(card.adapterResolved)
        assertEquals(4, card.headsetLifecycle.size)
    }

    @Test
    fun projectorExposesOnlyConfirmedGenericNoiseControl() {
        val base = VivoEarbudAdapter().snapshot()
        val state = EarbudState(
            adapter = base.copy(
                capabilities = EarbudCapabilities(
                    battery = true,
                    noiseControl = true,
                    audioHandoff = true,
                ),
                supportedNoiseModes = setOf(
                    NoiseMode.TRANSPARENCY,
                    NoiseMode.ANC,
                    NoiseMode.OFF,
                ),
            ),
            address = FIRST_ADDRESS,
            lifecycle = privateLifecycle(
                PrivateTransportState.CONNECTED,
                ProtocolHandshakeState.CONFIRMED,
            ),
        ).withNoiseMode(NoiseMode.ANC)

        val card = DeviceSessionUiProjector.project(DeviceSessionSnapshot(state, "token"))

        val control = requireNotNull(card.noiseControl)
        assertTrue(control.enabled)
        assertEquals(NoiseMode.ANC, control.selectedMode)
        assertEquals(
            listOf(NoiseMode.ANC, NoiseMode.OFF, NoiseMode.TRANSPARENCY),
            control.supportedModes,
        )
        assertEquals(
            DeviceMetricKind.NOISE_MODE,
            card.metrics.single { it.label == "模式" }.kind,
        )
        assertEquals("token", card.sessionToken)
    }

    @Test
    fun bridgeReceiptsAdvanceOnlyTheObservedMiLinkStages() {
        val state = activeState(FIRST_ADDRESS, "Headset")
        val session = DeviceSessionSnapshot(state, "token")
        val receipt = BridgeReceipt(
            address = FIRST_ADDRESS,
            sessionToken = "token",
            revision = state.revision,
            consumerProcess = "com.milink.service",
            stage = BridgeStage.STATE_ACCEPTED,
        )

        val collection = DeviceSessionReducer.acceptBridgeReceipt(
            DeviceSessionCollection(sessions = mapOf(FIRST_ADDRESS to session)),
            receipt,
        )
        val updated = requireNotNull(collection.sessions[FIRST_ADDRESS])

        assertTrue(updated.bridgeObserved)
        assertFalse(updated.identityQueried)
        assertEquals(DevicePhase.STATE_ACCEPTED, updated.phase)
    }

    private fun activeState(address: String, name: String) = EarbudState(
        adapter = StandardEarbudAdapter().snapshot(),
        deviceName = name,
        address = address,
        lifecycle = DeviceLifecycle(
            SystemProfileState.CONNECTED,
            PrivateTransportState.NOT_REQUIRED,
            ProtocolHandshakeState.NOT_REQUIRED,
        ),
        revision = 1,
    )

    private fun snapshot(lifecycle: DeviceLifecycle) = DeviceSessionSnapshot(
        state = EarbudState(
            adapter = VivoEarbudAdapter().snapshot(),
            address = FIRST_ADDRESS,
            lifecycle = lifecycle,
        ),
        sessionToken = "token",
    )

    private fun privateLifecycle(
        transport: PrivateTransportState,
        handshake: ProtocolHandshakeState = ProtocolHandshakeState.PENDING,
    ) = DeviceLifecycle(
        systemProfile = SystemProfileState.CONNECTED,
        privateTransport = transport,
        protocolHandshake = handshake,
    )

    private companion object {
        const val FIRST_ADDRESS = "00:11:22:33:44:55"
        const val SECOND_ADDRESS = "AA:BB:CC:DD:EE:FF"
    }
}
