package dev.hyperears.integration

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MiLinkStateCodecTest {
    @Test
    fun reducerSeparatesProfileSessionFromVendorChannel() {
        val old = EarbudState(
            modelId = "old",
            address = "00:00:00:00:00:01",
            connected = true,
            handshakeAccepted = true,
            battery = EarbudBattery(left = BatteryReading(80, false)),
            noiseMode = NoiseMode.ANC,
            revision = 9,
        )

        val started = EarbudStateReducer.reduce(
            old,
            EarbudEvent.SessionStarted(
                modelId = VivoTwsAir3ProAdapter.id,
                deviceName = VivoTwsAir3ProAdapter.displayName,
                address = "00:00:00:00:00:02",
                privateProtocolRequired = true,
            ),
        )

        assertFalse(started.connected)
        assertTrue(started.sessionActive)
        assertFalse(started.handshakeAccepted)
        assertNullBattery(started.battery.left)
        assertEquals(null, started.noiseMode)
        assertEquals(10, started.revision)

        val connected = EarbudStateReducer.reduce(started, EarbudEvent.ChannelConnected)
        assertTrue(connected.connected)
        assertTrue(connected.privateChannelConnected)
        assertEquals(11, connected.revision)
    }

    @Test
    fun reducerPreservesCapabilitiesAcrossChannelReconnectWithinSession() {
        val old = EarbudState(
            modelId = VivoTwsAir3ProAdapter.id,
            deviceName = VivoTwsAir3ProAdapter.displayName,
            address = "00:00:00:00:00:01",
            connected = true,
            handshakeAccepted = true,
            battery = EarbudBattery(
                left = BatteryReading(75, false),
                right = BatteryReading(74, false),
            ),
            noiseMode = NoiseMode.OFF,
            revision = 9,
        )

        val disconnected = EarbudStateReducer.reduce(
            old,
            EarbudEvent.ChannelDisconnected,
        )
        val reconnected = EarbudStateReducer.reduce(
            disconnected,
            EarbudEvent.ChannelConnected,
        )

        assertTrue(reconnected.connected)
        assertFalse(reconnected.handshakeAccepted)
        assertEquals(75, reconnected.battery.left.percent)
        assertEquals(74, reconnected.battery.right.percent)
        assertEquals(NoiseMode.OFF, reconnected.noiseMode)
        assertEquals(11, reconnected.revision)
    }

    @Test
    fun reducerEndsTransportSessionWithoutForgettingLogicalDeviceState() {
        val connected = EarbudState(
            modelId = VivoTwsAir3ProAdapter.id,
            deviceName = VivoTwsAir3ProAdapter.displayName,
            address = "00:00:00:00:00:01",
            connected = true,
            handshakeAccepted = true,
            battery = EarbudBattery(left = BatteryReading(75, false)),
            noiseMode = NoiseMode.ANC,
            revision = 9,
        )

        val ended = EarbudStateReducer.reduce(connected, EarbudEvent.SessionEnded)

        assertFalse(ended.connected)
        assertFalse(ended.sessionActive)
        assertFalse(ended.handshakeAccepted)
        assertEquals(75, ended.battery.left.percent)
        assertEquals(NoiseMode.ANC, ended.noiseMode)
        assertEquals(10, ended.revision)
    }

    @Test
    fun reducerRestartsSameLogicalDeviceFromLastConfirmedTelemetry() {
        val dormant = EarbudState(
            modelId = VivoTwsAir3ProAdapter.id,
            deviceName = VivoTwsAir3ProAdapter.displayName,
            address = "00:00:00:00:00:01",
            battery = EarbudBattery(
                left = BatteryReading(75, false),
                right = BatteryReading(74, false),
            ),
            noiseMode = NoiseMode.TRANSPARENCY,
            revision = 9,
        )

        val restarted = EarbudStateReducer.reduce(
            dormant,
            EarbudEvent.SessionStarted(
                modelId = VivoTwsAir3ProAdapter.id,
                deviceName = VivoTwsAir3ProAdapter.displayName,
                address = dormant.address!!,
                privateProtocolRequired = true,
            ),
        )

        assertTrue(restarted.sessionActive)
        assertFalse(restarted.connected)
        assertFalse(restarted.handshakeAccepted)
        assertEquals(75, restarted.battery.left.percent)
        assertEquals(74, restarted.battery.right.percent)
        assertEquals(NoiseMode.TRANSPARENCY, restarted.noiseMode)
        assertEquals(10, restarted.revision)
    }

    @Test
    fun identityOnlyAdapterBecomesReadyWithoutPrivateChannel() {
        val started = EarbudStateReducer.reduce(
            EarbudState(),
            EarbudEvent.SessionStarted(
                modelId = StandardEarbudAdapter.ID,
                deviceName = "Example Headphones",
                address = "00:00:00:00:00:03",
                privateProtocolRequired = false,
            ),
        )

        val ready = EarbudStateReducer.reduce(started, EarbudEvent.AdapterReady)

        assertTrue(ready.sessionActive)
        assertTrue(ready.connected)
        assertFalse(ready.privateProtocolRequired)
        assertFalse(ready.privateChannelConnected)
        assertFalse(ready.handshakeAccepted)
    }

    @Test
    fun reducerDoesNotPublishDuplicateDeviceState() {
        val old = EarbudState(
            battery = EarbudBattery(
                left = BatteryReading(75, false),
                right = BatteryReading(74, false),
            ),
            noiseMode = NoiseMode.OFF,
            revision = 9,
        )

        val duplicateBattery = EarbudStateReducer.reduce(
            old,
            EarbudEvent.BatteryChanged(old.battery),
        )
        val duplicateMode = EarbudStateReducer.reduce(
            old,
            EarbudEvent.NoiseModeChanged(NoiseMode.OFF, acknowledged = true),
        )

        assertTrue(duplicateBattery === old)
        assertTrue(duplicateMode === old)
        assertEquals(9, duplicateBattery.revision)
        assertEquals(9, duplicateMode.revision)
    }

    @Test
    fun encodesMiLinkBatteryAndAncState() {
        val state = EarbudState(
            connected = true,
            battery = EarbudBattery(
                left = BatteryReading(83, true),
                right = BatteryReading(82, false),
                case = BatteryReading(null, false),
            ),
            noiseMode = NoiseMode.TRANSPARENCY,
        )

        assertEquals(listOf(-1, 83, 82, 0, 1, 0), MiLinkStateCodec.batteryLevels(state))
        assertEquals(2, MiLinkStateCodec.ancState(state))
        assertEquals(82, MiLinkStateCodec.regularBatteryLevel(state))
    }

    private fun assertNullBattery(reading: BatteryReading) {
        assertEquals(null, reading.percent)
        assertFalse(reading.charging)
    }
}
