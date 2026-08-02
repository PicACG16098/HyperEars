package dev.hyperears.ui.dashboard

import dev.hyperears.bridge.BridgeReceipt
import dev.hyperears.bridge.BridgeStage
import dev.hyperears.integration.AppleAirPodsMaxAdapter
import dev.hyperears.integration.BatteryReading
import dev.hyperears.integration.EarbudBattery
import dev.hyperears.integration.EarbudState
import org.junit.Assert.assertEquals
import org.junit.Test

class DashboardUiStateTest {
    @Test
    fun `two addresses remain as two independent sessions`() {
        val left = activeState("02:00:00:00:00:01", "vivo TWS Air3 Pro")
        val right = activeState("AA:BB:CC:DD:EE:FF", "Second TWS")

        val afterFirst = DeviceSessionReducer.reduce(
            DeviceSessionCollection(),
            left,
            "token-1",
        )
        val afterSecond = DeviceSessionReducer.reduce(afterFirst, right, "token-2")

        assertEquals(2, afterSecond.sessions.size)
        assertEquals(
            "token-1",
            afterSecond.sessions["02:00:00:00:00:01"]?.sessionToken,
        )
        assertEquals(
            "token-2",
            afterSecond.sessions["AA:BB:CC:DD:EE:FF"]?.sessionToken,
        )
    }

    @Test
    fun `new state replaces only the matching address`() {
        val first = activeState("02:00:00:00:00:01", "vivo TWS Air3 Pro")
        val second = activeState("AA:BB:CC:DD:EE:FF", "Second TWS")
        val initial = DeviceSessionReducer.reduce(
            DeviceSessionReducer.reduce(
                DeviceSessionCollection(),
                first,
                "token-1",
            ),
            second,
            "token-2",
        )

        val connectedFirst = first.copy(connected = true, revision = 2)
        val updated = DeviceSessionReducer.reduce(initial, connectedFirst, "token-1")

        assertEquals(2, updated.sessions.size)
        assertEquals(
            true,
            updated.sessions["02:00:00:00:00:01"]?.state?.connected,
        )
        assertEquals(
            false,
            updated.sessions["AA:BB:CC:DD:EE:FF"]?.state?.connected,
        )
    }

    @Test
    fun `session end removes only the matching address`() {
        val first = activeState("02:00:00:00:00:01", "vivo TWS Air3 Pro")
        val second = activeState("AA:BB:CC:DD:EE:FF", "Second TWS")
        val initial = DeviceSessionReducer.reduce(
            DeviceSessionReducer.reduce(
                DeviceSessionCollection(),
                first,
                "token-1",
            ),
            second,
            "token-2",
        )

        val updated = DeviceSessionReducer.reduce(
            initial,
            first.copy(sessionActive = false, connected = false),
            "token-1",
        )

        assertEquals(setOf("AA:BB:CC:DD:EE:FF"), updated.sessions.keys)
    }

    @Test
    fun `matching MiLink receipt is observed for the exact revision`() {
        val state = activeState("02:00:00:00:00:01", "First").copy(
            connected = true,
            handshakeAccepted = true,
            revision = 8,
        )
        val initial = DeviceSessionReducer.reduce(
            DeviceSessionCollection(),
            state,
            "token-1",
        )

        val updated = DeviceSessionReducer.acceptBridgeReceipt(
            initial,
            receipt(state, token = "token-1", revision = 8),
        )

        val session = updated.sessions.getValue("02:00:00:00:00:01")
        assertEquals(true, session.bridgeObserved)
        assertEquals(DevicePhase.STATE_ACCEPTED, session.phase)
    }

    @Test
    fun `old MiLink receipt does not mark a newer state observed`() {
        val state = activeState("02:00:00:00:00:01", "First").copy(
            connected = true,
            handshakeAccepted = true,
            revision = 9,
        )
        val initial = DeviceSessionReducer.reduce(
            DeviceSessionCollection(),
            state,
            "token-1",
        )

        val updated = DeviceSessionReducer.acceptBridgeReceipt(
            initial,
            receipt(state, token = "token-1", revision = 8),
        )

        val session = updated.sessions.getValue("02:00:00:00:00:01")
        assertEquals(false, session.bridgeObserved)
        assertEquals(DevicePhase.WAITING_FOR_MILINK, session.phase)
    }

    @Test
    fun `connected readiness does not require a synthetic handshake`() {
        val state = activeState("02:00:00:00:00:01", "Connected-ready device").copy(
            connected = true,
            privateProtocolRequired = true,
            privateChannelConnected = true,
            handshakeAccepted = false,
        )

        val session = DeviceSessionSnapshot(state = state, sessionToken = "token-1")

        assertEquals(DevicePhase.WAITING_FOR_MILINK, session.phase)
        assertEquals(true, session.miLinkLifecycle.first().active)
    }

    @Test
    fun `private transport is reported as preparing until adapter readiness`() {
        val state = activeState("02:00:00:00:00:01", "Private device").copy(
            privateProtocolRequired = true,
            privateChannelConnected = true,
            connected = false,
        )

        val session = DeviceSessionSnapshot(state = state, sessionToken = "token-1")

        assertEquals(DevicePhase.PREPARING_PRIVATE_CHANNEL, session.phase)
    }

    @Test
    fun `projector owns headset battery topology and readiness labels`() {
        val state = activeState("02:00:00:00:00:01", "AirPods Max").copy(
            modelId = AppleAirPodsMaxAdapter.ID,
            connected = true,
            privateProtocolRequired = true,
            privateChannelConnected = true,
            battery = EarbudBattery(left = BatteryReading(82, charging = false)),
        )

        val card = DeviceSessionUiProjector.project(
            DeviceSessionSnapshot(state = state, sessionToken = "token-1"),
        )

        assertEquals("Apple AirPods Max", card.profileName)
        assertEquals(DeviceMetric("整机", "82%"), card.metrics.first())
        assertEquals("连接即就绪", card.headsetLifecycle.last().label)
        assertEquals(true, card.headsetLifecycle.last().complete)
    }

    @Test
    fun `receipt arriving before state is attached when revision catches up`() {
        val state = activeState("02:00:00:00:00:01", "First").copy(revision = 4)
        val receiptFirst = DeviceSessionReducer.acceptBridgeReceipt(
            DeviceSessionCollection(),
            receipt(state, token = "token-1", revision = 4),
        )

        val updated = DeviceSessionReducer.reduce(receiptFirst, state, "token-1")

        assertEquals(
            true,
            updated.sessions.getValue("02:00:00:00:00:01").bridgeObserved,
        )
    }

    @Test
    fun `dashboard reports independently observed counters`() {
        val readyState = activeState("02:00:00:00:00:01", "First").copy(
            connected = true,
            handshakeAccepted = true,
            revision = 3,
        )
        val dashboard = DashboardUiState(
            runtimeResponsive = true,
            sessions = listOf(
                DeviceSessionSnapshot(
                    state = readyState,
                    sessionToken = "token-1",
                    bridgeReceipts = setOf(
                        receipt(readyState, token = "token-1", revision = 3),
                        receipt(
                            readyState,
                            token = "token-1",
                            revision = 2,
                            stage = BridgeStage.IDENTITY_QUERIED,
                        ),
                        receipt(
                            readyState,
                            token = "token-1",
                            revision = 2,
                            stage = BridgeStage.CAPABILITIES_QUERIED,
                        ),
                    ),
                ),
                DeviceSessionSnapshot(
                    activeState("AA:BB:CC:DD:EE:FF", "Second"),
                    "token-2",
                ),
            ),
        )

        assertEquals(2, dashboard.sessions.size)
        assertEquals(1, dashboard.connectedCount)
        assertEquals(1, dashboard.handshakeCount)
        assertEquals(1, dashboard.miLinkObservedCount)
        assertEquals(1, dashboard.identityQueriedCount)
        assertEquals(1, dashboard.capabilitiesQueriedCount)
    }

    @Test
    fun `session scoped MiLink calls survive a later state revision`() {
        val initialState = activeState("02:00:00:00:00:01", "First").copy(
            connected = true,
            handshakeAccepted = true,
            revision = 3,
        )
        val initial = DeviceSessionReducer.reduce(
            DeviceSessionCollection(),
            initialState,
            "token-1",
        )
        val withCapabilityCall = DeviceSessionReducer.acceptBridgeReceipt(
            initial,
            receipt(
                initialState,
                token = "token-1",
                revision = 3,
                stage = BridgeStage.CAPABILITIES_QUERIED,
            ),
        )

        val updated = DeviceSessionReducer.reduce(
            withCapabilityCall,
            initialState.copy(revision = 4),
            "token-1",
        )

        val session = updated.sessions.getValue("02:00:00:00:00:01")
        assertEquals(true, session.capabilitiesQueried)
        assertEquals(false, session.bridgeObserved)
    }

    private fun activeState(address: String, name: String) = EarbudState(
        modelId = "test-profile",
        deviceName = name,
        address = address,
        sessionActive = true,
        revision = 1,
    )

    private fun receipt(
        state: EarbudState,
        token: String,
        revision: Long,
        stage: BridgeStage = BridgeStage.STATE_ACCEPTED,
    ) = BridgeReceipt(
        address = requireNotNull(state.address),
        sessionToken = token,
        revision = revision,
        consumerProcess = "com.milink.service:core",
        stage = stage,
    )
}
