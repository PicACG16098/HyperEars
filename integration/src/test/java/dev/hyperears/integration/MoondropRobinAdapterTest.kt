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
    fun noiseModeWriteWaitsForDeviceReadbackAndExactModelKeepsDormantOnFailure() {
        val adapter = MoondropRobinAdapter()
        adapter.receive(
            MoondropRobinWireCodec.frame(
                command = 0x0A,
                subcommand = 0x83,
                opcode = 0x00,
                parameters = hex("00 04 03 01"),
            ),
        )
        adapter.receive(
            MoondropRobinWireCodec.frame(
                command = 0x1D,
                subcommand = 0x11,
                opcode = 0x03,
                parameters = byteArrayOf(0, 1, 0, 0),
            ),
        )

        val result = adapter.executeControl(
            StandardControlRequest.SetNoiseMode(NoiseMode.TRANSPARENCY),
        )
        assertTrue(result.accepted)
        assertFalse(result.stateChanged)
        assertEquals(
            listOf(
                MoondropRobinWireCodec
                    .setNoiseMode(MoondropRobinWireCodec.NoiseMode.TRANSPARENCY)
                    .toList(),
            ),
            result.commands.map(ByteArray::toList),
        )
        assertEquals(
            listOf(MoondropRobinWireCodec.queryNoiseMode.toList()),
            result.readback.map(ByteArray::toList),
        )
        assertEquals(NoiseMode.OFF, adapter.runtimeState().noiseMode)
        assertEquals(
            InitialProtocolFailureResolution.KeepDormant,
            adapter.onInitialProtocolUnavailable(),
        )
    }

    private fun hex(value: String): ByteArray = value
        .split(' ')
        .filter(String::isNotBlank)
        .map { it.toInt(16).toByte() }
        .toByteArray()
}
