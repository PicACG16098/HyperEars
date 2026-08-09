package dev.hyperears.integration

import dev.hyperears.protocol.moondrop.MoondropRobinWireCodec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MoondropRobinAdapterTest {
    @Test
    fun exactNameSelectsRobinWithoutCachedSppUuid() {
        val adapter = requireNotNull(
            EarbudAdapterRegistry.resolve(
                EarbudIdentity(
                    deviceName = "Robin's Earphones",
                    standardHeadset = true,
                ),
            ),
        )
        assertTrue(adapter is MoondropRobinAdapter)
        assertEquals(MoondropRobinAdapter.ID, adapter.id)
    }

    @Test
    fun standardSppUuidDoesNotSelectMoondrop() {
        val adapter = requireNotNull(
            EarbudAdapterRegistry.resolve(
                EarbudIdentity(
                    deviceName = "Unrelated headset",
                    standardHeadset = true,
                    serviceUuids = setOf(MoondropRobinAdapter.STANDARD_SPP_UUID),
                ),
            ),
        )
        assertTrue(adapter is StandardEarbudAdapter)
        assertFalse(adapter is MoondropEarbudAdapter)
    }

    @Test
    fun brandQualifiedRobinKeywordIsStillANameOnlyCandidate() {
        val adapter = requireNotNull(
            EarbudAdapterRegistry.resolve(
                EarbudIdentity(
                    deviceName = "MOONDROP Robin LE",
                    standardHeadset = true,
                ),
            ),
        )
        assertTrue(adapter is MoondropRobinAdapter)
    }

    @Test
    fun privateCapabilitiesRemainClosedUntilHandshakeAndReadResponses() {
        val adapter = MoondropRobinAdapter()
        assertTrue(
            adapter.beginHandshake().commands.single()
                .contentEquals(MoondropRobinWireCodec.handshake),
        )
        assertTrue(adapter.snapshot().capabilities.battery)
        assertFalse(adapter.snapshot().capabilities.noiseControl)
        assertTrue(adapter.snapshot().supportedNoiseModes.isEmpty())
        assertEquals(BatterySource.SYSTEM_AGGREGATE, adapter.snapshot().batterySource)

        val handshake = MoondropRobinWireCodec.frame(
            command = 0x0A,
            subcommand = 0x83,
            opcode = 0x00,
            parameters = hex("00 04 03 01"),
        )
        val accepted = adapter.receive(handshake)
        assertEquals(HandshakeResult.Ready, accepted.handshake)
        assertEquals(
            listOf(
                MoondropRobinWireCodec.queryBattery.toList(),
                MoondropRobinWireCodec.queryNoiseMode.toList(),
            ),
            accepted.commands.map(ByteArray::toList),
        )
        assertFalse(adapter.snapshot().capabilities.noiseControl)

        adapter.receive(
            MoondropRobinWireCodec.frame(
                command = 0x1D,
                subcommand = 0x1B,
                opcode = 0x01,
                parameters = byteArrayOf(1, 91, 2, 76),
            ),
        )
        assertEquals(BatterySource.PRIVATE_PROTOCOL, adapter.snapshot().batterySource)
        assertEquals(91, adapter.runtimeState().battery.left.percent)
        assertEquals(76, adapter.runtimeState().battery.right.percent)
        assertFalse(adapter.snapshot().capabilities.noiseControl)

        adapter.receive(
            MoondropRobinWireCodec.frame(
                command = 0x1D,
                subcommand = 0x11,
                opcode = 0x03,
                parameters = byteArrayOf(1, 1, 0, 0),
            ),
        )
        assertTrue(adapter.snapshot().capabilities.noiseControl)
        assertEquals(
            setOf(NoiseMode.ANC, NoiseMode.OFF, NoiseMode.TRANSPARENCY),
            adapter.snapshot().supportedNoiseModes,
        )
        assertEquals(NoiseMode.ANC, adapter.runtimeState().noiseMode)
    }

    @Test
    fun provisionalBootstrapBatteryKeepsSystemFallbackAndSchedulesOneModelOwnedRead() {
        val adapter = readyAdapter(systemBattery = 73)

        val result = adapter.receive(batteryFrame(left = 0, right = 100))

        assertFalse(result.stateChanged)
        assertEquals(BatterySource.SYSTEM_AGGREGATE, adapter.snapshot().batterySource)
        assertEquals(73, adapter.runtimeState().battery.left.percent)
        assertEquals(73, adapter.runtimeState().battery.right.percent)
        val followUp = result.requireStateRequest()
        assertEquals(BatteryFeatureState.FEATURE_ID, followUp.featureId)
        assertEquals(MoondropRobinAdapter.BATTERY_BOOTSTRAP_DELAYS_MS.first(), followUp.delayMs)
        assertEquals(
            listOf(MoondropRobinWireCodec.queryBattery.toList()),
            adapter.queryState(followUp.featureId).map(ByteArray::toList),
        )
    }

    @Test
    fun completeBootstrapBatteryCommitsPrivateStateAndCancelsPendingRead() {
        val adapter = readyAdapter(systemBattery = 73)
        adapter.receive(batteryFrame(left = 0, right = 100))

        val result = adapter.receive(batteryFrame(left = 96, right = 100))

        assertTrue(result.stateChanged)
        assertEquals(BatterySource.PRIVATE_PROTOCOL, adapter.snapshot().batterySource)
        assertEquals(96, adapter.runtimeState().battery.left.percent)
        assertEquals(100, adapter.runtimeState().battery.right.percent)
        assertEquals(
            listOf(AdapterEffect.CancelStateRequest(BatteryFeatureState.FEATURE_ID)),
            result.effects,
        )

        val realZeroAfterConfirmation = adapter.receive(batteryFrame(left = 0, right = 100))
        assertTrue(realZeroAfterConfirmation.stateChanged)
        assertEquals(0, adapter.runtimeState().battery.left.percent)
        assertTrue(realZeroAfterConfirmation.effects.isEmpty())
    }

    @Test
    fun boundedBootstrapEventuallyAcceptsAStableZeroWithoutPollingForever() {
        val adapter = readyAdapter(systemBattery = 73)

        MoondropRobinAdapter.BATTERY_BOOTSTRAP_DELAYS_MS.forEach { delayMs ->
            val result = adapter.receive(batteryFrame(left = 0, right = 100))
            assertFalse(result.stateChanged)
            val followUp = result.requireStateRequest()
            assertEquals(delayMs, followUp.delayMs)
            assertEquals(BatteryFeatureState.FEATURE_ID, followUp.featureId)
        }

        val finalResult = adapter.receive(batteryFrame(left = 0, right = 100))

        assertTrue(finalResult.stateChanged)
        assertEquals(BatterySource.PRIVATE_PROTOCOL, adapter.snapshot().batterySource)
        assertEquals(0, adapter.runtimeState().battery.left.percent)
        assertEquals(100, adapter.runtimeState().battery.right.percent)
        assertEquals(
            listOf(AdapterEffect.CancelStateRequest(BatteryFeatureState.FEATURE_ID)),
            finalResult.effects,
        )
    }

    @Test
    fun noiseModeWritePublishesOptimisticallyThenRequestsDelayedReadback() {
        val adapter = readyAdapterWithNoiseMode(NoiseMode.OFF)

        val result = adapter.executeControl(
            StandardControlRequest.SetNoiseMode(NoiseMode.TRANSPARENCY),
        )
        val policy = adapter.controlPolicy(
            StandardControlRequest.SetNoiseMode(NoiseMode.TRANSPARENCY),
        )
        assertTrue(result.accepted)
        assertTrue(result.stateChanged)
        assertEquals(ControlConfirmationPolicy.PUBLISH_AFTER_WRITE, policy.confirmation)
        assertEquals(
            NoiseModeFeatureState(NoiseMode.TRANSPARENCY),
            policy.stateAfterWrite,
        )
        assertEquals(
            listOf(
                MoondropRobinWireCodec
                    .setNoiseMode(MoondropRobinWireCodec.NoiseMode.TRANSPARENCY)
                    .toList(),
            ),
            result.commands.map(ByteArray::toList),
        )
        assertTrue(result.readback.isEmpty())
        assertEquals(NoiseMode.TRANSPARENCY, adapter.runtimeState().noiseMode)
        assertEquals(
            listOf(
                AdapterEffect.CancelStateRequest(NoiseModeFeatureState.FEATURE_ID),
                AdapterEffect.RequestState(
                    NoiseModeFeatureState.FEATURE_ID,
                    MoondropRobinAdapter.INITIAL_MODE_QUERY_DELAY_MS,
                ),
            ),
            adapter.controlWritten(StandardControlRequest.SetNoiseMode(NoiseMode.TRANSPARENCY)),
        )
        assertEquals(
            InitialProtocolFailureResolution.KeepDormant,
            adapter.onInitialProtocolUnavailable(),
        )
    }

    @Test
    fun staleModeReadbacksKeepTheOptimisticStateAndScheduleBoundedReads() {
        val adapter = readyAdapterWithNoiseMode(NoiseMode.OFF)
        val control = adapter.executeControl(
            StandardControlRequest.SetNoiseMode(NoiseMode.ANC),
        )
        adapter.controlWritten(StandardControlRequest.SetNoiseMode(NoiseMode.ANC))
        assertTrue(control.accepted)
        assertEquals(NoiseMode.ANC, adapter.runtimeState().noiseMode)

        MoondropRobinAdapter.MODE_CONFIRMATION_DELAYS_MS.forEach { delayMs ->
            val result = adapter.receive(noiseModeFrame(NoiseMode.OFF))

            assertFalse(result.stateChanged)
            assertEquals(NoiseMode.ANC, adapter.runtimeState().noiseMode)
            val followUp = result.requireStateRequest()
            assertEquals(NoiseModeFeatureState.FEATURE_ID, followUp.featureId)
            assertEquals(delayMs, followUp.delayMs)
            assertEquals(
                listOf(MoondropRobinWireCodec.queryNoiseMode.toList()),
                adapter.queryState(followUp.featureId).map(ByteArray::toList),
            )
        }

        val finalResult = adapter.receive(noiseModeFrame(NoiseMode.OFF))

        assertTrue(finalResult.stateChanged)
        assertEquals(NoiseMode.OFF, adapter.runtimeState().noiseMode)
        assertEquals(
            listOf(AdapterEffect.CancelStateRequest(NoiseModeFeatureState.FEATURE_ID)),
            finalResult.effects,
        )
    }

    @Test
    fun expectedModeReadbackCompletesConfirmationWithoutRepublishingTheSameState() {
        val adapter = readyAdapterWithNoiseMode(NoiseMode.OFF)
        adapter.executeControl(StandardControlRequest.SetNoiseMode(NoiseMode.TRANSPARENCY))
        adapter.controlWritten(StandardControlRequest.SetNoiseMode(NoiseMode.TRANSPARENCY))
        adapter.receive(noiseModeFrame(NoiseMode.OFF))

        val result = adapter.receive(noiseModeFrame(NoiseMode.TRANSPARENCY))

        assertFalse(result.stateChanged)
        assertEquals(NoiseMode.TRANSPARENCY, adapter.runtimeState().noiseMode)
        assertEquals(
            listOf(AdapterEffect.CancelStateRequest(NoiseModeFeatureState.FEATURE_ID)),
            result.effects,
        )

        val unsolicited = adapter.receive(noiseModeFrame(NoiseMode.OFF))
        assertTrue(unsolicited.stateChanged)
        assertEquals(NoiseMode.OFF, adapter.runtimeState().noiseMode)
        assertTrue(unsolicited.effects.isEmpty())
    }

    @Test
    fun aConfirmedModeInTheSameTransportReadCancelsAnEarlierDeferredQuery() {
        val adapter = readyAdapterWithNoiseMode(NoiseMode.OFF)
        adapter.executeControl(StandardControlRequest.SetNoiseMode(NoiseMode.ANC))
        adapter.controlWritten(StandardControlRequest.SetNoiseMode(NoiseMode.ANC))

        val result = adapter.receive(
            noiseModeFrame(NoiseMode.OFF) + noiseModeFrame(NoiseMode.ANC),
        )

        assertFalse(result.stateChanged)
        assertEquals(NoiseMode.ANC, adapter.runtimeState().noiseMode)
        assertEquals(
            listOf(
                AdapterEffect.RequestState(
                    NoiseModeFeatureState.FEATURE_ID,
                    MoondropRobinAdapter.MODE_CONFIRMATION_DELAYS_MS.first(),
                ),
                AdapterEffect.CancelStateRequest(NoiseModeFeatureState.FEATURE_ID),
            ),
            result.effects,
        )
    }

    @Test
    fun aNewModeControlReplacesThePreviousConfirmationTarget() {
        val adapter = readyAdapterWithNoiseMode(NoiseMode.OFF)
        adapter.executeControl(StandardControlRequest.SetNoiseMode(NoiseMode.ANC))
        adapter.controlWritten(StandardControlRequest.SetNoiseMode(NoiseMode.ANC))
        adapter.receive(noiseModeFrame(NoiseMode.OFF))

        adapter.executeControl(StandardControlRequest.SetNoiseMode(NoiseMode.TRANSPARENCY))
        adapter.controlWritten(StandardControlRequest.SetNoiseMode(NoiseMode.TRANSPARENCY))
        val result = adapter.receive(noiseModeFrame(NoiseMode.TRANSPARENCY))

        assertFalse(result.stateChanged)
        assertEquals(NoiseMode.TRANSPARENCY, adapter.runtimeState().noiseMode)
        assertEquals(
            listOf(AdapterEffect.CancelStateRequest(NoiseModeFeatureState.FEATURE_ID)),
            result.effects,
        )
    }

    @Test
    fun protocolResetClearsModelOwnedConfirmationState() {
        val adapter = readyAdapterWithNoiseMode(NoiseMode.OFF)
        adapter.executeControl(StandardControlRequest.SetNoiseMode(NoiseMode.ANC))
        adapter.controlWritten(StandardControlRequest.SetNoiseMode(NoiseMode.ANC))

        adapter.resetProtocolSession()
        acceptHandshake(adapter)
        val result = adapter.receive(noiseModeFrame(NoiseMode.OFF))

        assertTrue(result.stateChanged)
        assertEquals(NoiseMode.OFF, adapter.runtimeState().noiseMode)
        assertTrue(result.effects.isEmpty())
    }

    @Test
    fun nonModeControlsKeepTheDefaultReadbackDelay() {
        val policy = MoondropRobinAdapter().controlPolicy(StandardControlRequest.Refresh)

        assertEquals(
            ControlExecutionPolicy.DEFAULT_READBACK_DELAY_MS,
            policy.readbackDelayMs,
        )
    }

    private fun hex(value: String): ByteArray = value
        .split(' ')
        .filter(String::isNotBlank)
        .map { it.toInt(16).toByte() }
        .toByteArray()

    private fun readyAdapter(systemBattery: Int): MoondropRobinAdapter =
        MoondropRobinAdapter().also { adapter ->
            assertTrue(adapter.onSystemBatteryChanged(systemBattery))
            acceptHandshake(adapter)
        }

    private fun readyAdapterWithNoiseMode(initialMode: NoiseMode): MoondropRobinAdapter =
        MoondropRobinAdapter().also { adapter ->
            acceptHandshake(adapter)
            adapter.receive(noiseModeFrame(initialMode))
            assertEquals(initialMode, adapter.runtimeState().noiseMode)
        }

    private fun acceptHandshake(adapter: MoondropRobinAdapter) {
        val result = adapter.receive(
            MoondropRobinWireCodec.frame(
                command = 0x0A,
                subcommand = 0x83,
                opcode = 0x00,
                parameters = hex("00 04 03 01"),
            ),
        )
        assertEquals(HandshakeResult.Ready, result.handshake)
    }

    private fun batteryFrame(left: Int, right: Int): ByteArray =
        MoondropRobinWireCodec.frame(
            command = 0x1D,
            subcommand = 0x1B,
            opcode = 0x01,
            parameters = byteArrayOf(1, left.toByte(), 2, right.toByte()),
        )

    private fun noiseModeFrame(mode: NoiseMode): ByteArray =
        MoondropRobinWireCodec.frame(
            command = 0x1D,
            subcommand = 0x11,
            opcode = 0x03,
            parameters = byteArrayOf(
                when (mode) {
                    NoiseMode.OFF -> 0
                    NoiseMode.ANC -> 1
                    NoiseMode.TRANSPARENCY -> 2
                    NoiseMode.WIND -> error("Robin does not support wind mode")
                },
                1,
                0,
                0,
            ),
        )

    private fun AdapterIoResult.requireStateRequest(): AdapterEffect.RequestState {
        assertEquals(1, effects.size)
        return effects.single() as AdapterEffect.RequestState
    }
}
