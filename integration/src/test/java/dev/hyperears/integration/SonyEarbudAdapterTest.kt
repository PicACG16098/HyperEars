package dev.hyperears.integration

import dev.hyperears.protocol.sony.SonyHeadphonesWireCodec
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SonyEarbudAdapterTest {
    @Test
    fun resolvesConcreteModelsBeforeProtocolFamilies() {
        assertEquals("sony-wh-1000xm5", resolve("WH-1000XM5").id)
        assertEquals(HeadsetFormFactor.HEADPHONES, resolve("WH-1000XM5").formFactor)
        assertEquals("sony-wf-c510", resolve("WF-C510").id)
        assertEquals(
            setOf(NoiseMode.OFF, NoiseMode.TRANSPARENCY),
            resolve("WF-C510").supportedNoiseModes,
        )
        assertEquals(
            SonyMiLinkPresentationIds.AMBIENT_ONLY,
            resolve("WF-C510").miLinkCardPresentationId,
        )
        assertEquals("sony-linkbuds-s", resolve("LinkBuds S").id)
        assertEquals("sony-linkbuds", resolve("LinkBuds").id)
        assertEquals("sony-linkbuds", resolve("Sony LinkBuds").id)
    }

    @Test
    fun unknownModelsInKnownProductLinesUseFamilyProtocolCapabilities() {
        val noiseModel = resolve("WH-CH999N")
        assertEquals("sony-headphones-noise-protocol-family", noiseModel.id)
        assertTrue(noiseModel.capabilities.noiseControl)
        assertEquals(
            "sony-headphones-noise-protocol-family",
            resolve("Sony WH-CH999N").id,
        )

        val batteryModel = resolve("WF-C999")
        assertEquals("sony-tws-protocol-family", batteryModel.id)
        assertTrue(batteryModel.capabilities.battery)
        assertFalse(batteryModel.capabilities.noiseControl)
    }

    @Test
    fun serviceEvidenceUnlocksProtocolButLeShadowNameDoesNot() {
        val identity = identity(
            name = "Wireless Audio",
            services = setOf(SonyHeadphonesWireCodec.RFCOMM_SERVICE_V1),
        )
        assertEquals("sony-tws-protocol-family", EarbudAdapterRegistry.resolve(identity)?.id)
        assertFalse(resolve("LE_WF-C710N").privateProtocolRequired)
    }

    @Test
    fun v1HandshakeAcksAndAdvancesOneRequestPerDeviceAck() {
        val adapter = resolve("WH-1000XM3")
        val protocol = requireNotNull(adapter.createProtocol())
        val init = decode(protocol.initialReadCommands().single())
        assertArrayEquals(bytes("00 00"), init.payload)

        val handshakeEvents = protocol.offer(command(0, "01 00 40 10"))
        assertEquals(listOf(EarbudEvent.Handshake(true)), handshakeEvents)
        assertEquals(
            SonyHeadphonesWireCodec.MessageType.ACK,
            decode(protocol.drainImmediateCommands().single()).type,
        )

        protocol.offer(ack(1))
        val batteryQuery = decode(protocol.drainImmediateCommands().single())
        assertEquals(1, batteryQuery.sequence)
        assertArrayEquals(bytes("10 00"), batteryQuery.payload)

        protocol.offer(ack(0))
        val ambientQuery = decode(protocol.drainImmediateCommands().single())
        assertArrayEquals(bytes("66 02"), ambientQuery.payload)
    }

    @Test
    fun v1ParsesBatteryAndAmbientReports() {
        val protocol = requireNotNull(resolve("WH-1000XM3").createProtocol())
        protocol.initialReadCommands()
        protocol.offer(command(0, "01 00 40 10"))
        protocol.drainImmediateCommands()

        val batteryEvent = protocol.offer(command(0, "11 00 5a 00"))
            .filterIsInstance<EarbudEvent.BatteryChanged>()
            .single()
        assertEquals(90, batteryEvent.battery.overall.percent)

        val noiseEvent = protocol.offer(command(0, "67 02 01 02 02 01 00 00"))
            .filterIsInstance<EarbudEvent.NoiseModeChanged>()
            .single()
        assertEquals(NoiseMode.ANC, noiseEvent.mode)
    }

    @Test
    fun v2UsesDual2BatteryAndExtendedAmbientPayloads() {
        val protocol = requireNotNull(resolve("WF-C700N").createProtocol())
        protocol.initialReadCommands()
        protocol.offer(command(0, "01 00 03 00 00 00 00 00"))
        protocol.drainImmediateCommands()
        protocol.offer(ack(1))

        val batteryQuery = decode(protocol.drainImmediateCommands().single())
        assertArrayEquals(bytes("22 01"), batteryQuery.payload)

        val batteryEvent = protocol.offer(command(0, "23 01 4b 00 50 01"))
            .filterIsInstance<EarbudEvent.BatteryChanged>()
            .single()
        assertEquals(75, batteryEvent.battery.left.percent)
        assertEquals(80, batteryEvent.battery.right.percent)
        assertTrue(batteryEvent.battery.right.charging)
    }

    private fun resolve(name: String): EarbudAdapter = requireNotNull(
        EarbudAdapterRegistry.resolve(identity(name)),
    )

    private fun identity(
        name: String,
        services: Set<String> = emptySet(),
    ): EarbudIdentity = EarbudIdentity(
        deviceName = name,
        standardHeadset = true,
        serviceUuids = services,
    )

    private fun command(sequence: Int, payload: String): ByteArray =
        SonyHeadphonesWireCodec.encode(
            type = SonyHeadphonesWireCodec.MessageType.COMMAND_1,
            sequence = sequence,
            payload = bytes(payload),
        )

    private fun ack(sequence: Int): ByteArray = SonyHeadphonesWireCodec.encode(
        type = SonyHeadphonesWireCodec.MessageType.ACK,
        sequence = sequence,
    )

    private fun decode(bytes: ByteArray): SonyHeadphonesWireCodec.Frame =
        SonyHeadphonesWireCodec.Decoder().offer(bytes).single()

    private fun bytes(hex: String): ByteArray = hex
        .trim()
        .split(Regex("\\s+"))
        .map { it.toInt(16).toByte() }
        .toByteArray()
}
