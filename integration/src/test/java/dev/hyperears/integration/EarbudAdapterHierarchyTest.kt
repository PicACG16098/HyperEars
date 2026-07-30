package dev.hyperears.integration

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EarbudAdapterHierarchyTest {
    @Test
    fun registryResolvesFromConcreteModelToFamilyAndStandardFallback() {
        assertEquals(
            VivoTwsAir3ProAdapter,
            EarbudAdapterRegistry.resolve(identity("vivo TWS Air3 Pro")),
        )
        assertEquals(
            VivoTwsAir3ProAdapter,
            EarbudAdapterRegistry.resolve(identity("VIVO-TWS Air3 Pro")),
        )
        assertTrue(
            EarbudAdapterRegistry.resolve(identity("vivo TWS 3e")) is VivoEarbudAdapter,
        )
        assertTrue(
            EarbudAdapterRegistry.resolve(
                identity("LE-Headset", standardHeadset = true),
            ) is StandardEarbudAdapter,
        )
        assertNull(EarbudAdapterRegistry.resolve(identity("Living Room Speaker")))
        assertNull(EarbudAdapterRegistry.resolve(identity(null)))
        assertNull(
            EarbudAdapterRegistry.resolve(
                identity(
                    "REDMI Buds 6 Pro",
                    standardHeadset = true,
                    nativeSystemEarbud = true,
                ),
            ),
        )
    }

    @Test
    fun familyAndStandardFallbacksAreIdentityOnlyIntegrations() {
        assertEquals(
            VivoTwsAir3ProAdapter,
            EarbudAdapterRegistry.forIntegration(identity("vivo TWS Air3 Pro")),
        )

        val vivoFallback =
            EarbudAdapterRegistry.forIntegration(identity("vivo TWS 3e"))
        val standardFallback = EarbudAdapterRegistry.forIntegration(
            identity("LE-Headset", standardHeadset = true),
        )

        assertTrue(vivoFallback is VivoEarbudAdapter)
        assertFalse(requireNotNull(vivoFallback).privateProtocolRequired)
        assertFalse(vivoFallback.capabilities.battery)
        assertFalse(vivoFallback.capabilities.noiseControl)
        assertTrue(vivoFallback.capabilities.audioHandoff)
        assertNull(vivoFallback.createProtocol())

        assertTrue(standardFallback is StandardEarbudAdapter)
        assertFalse(requireNotNull(standardFallback).privateProtocolRequired)
        assertTrue(standardFallback.capabilities.audioHandoff)
        assertNull(standardFallback.createProtocol())

        assertNull(EarbudAdapterRegistry.forIntegration(identity("Living Room Speaker")))
    }

    @Test
    fun air3ProInheritsStandardAndVivoFamilyBehavior() {
        assertEquals(
            VivoEarbudAdapter::class.java,
            VivoTwsAir3ProAdapter.javaClass.superclass,
        )
        assertEquals(
            StandardEarbudAdapter::class.java,
            VivoEarbudAdapter::class.java.superclass,
        )

        val first = VivoTwsAir3ProAdapter.endpoints.first() as RfcommEndpointSpec.ServiceUuid
        assertEquals(1, VivoTwsAir3ProAdapter.endpoints.size)
        assertEquals(VivoEarbudAdapter.VIVO_GAIA_UUID, first.uuid)
        assertTrue(VivoTwsAir3ProAdapter.capabilities.battery)
        assertTrue(VivoTwsAir3ProAdapter.capabilities.noiseControl)
        assertTrue(VivoTwsAir3ProAdapter.capabilities.audioHandoff)
        assertFalse(VivoTwsAir3ProAdapter.capabilities.spatialAudio)
        assertFalse(VivoTwsAir3ProAdapter.capabilities.wearDetection)
    }

    @Test
    fun protocolMapsCapturedResponsesToDomainEvents() {
        val protocol = requireNotNull(VivoTwsAir3ProAdapter.createProtocol())
        val events = protocol.offer(
            hex(
                "FF 03 00 04 00 0A 83 00 00 03 03 01 " +
                    "FF 03 00 04 00 1B 82 30 00 02 04 00 " +
                    "FF 03 00 05 00 1B 82 07 00 53 52 5F 00",
            ),
        )

        assertEquals(EarbudEvent.Handshake(true), events[0])
        assertEquals(
            EarbudEvent.NoiseModeChanged(NoiseMode.TRANSPARENCY, acknowledged = false),
            events[1],
        )
        assertEquals(
            EarbudEvent.BatteryChanged(
                EarbudBattery(
                    BatteryReading(83, false),
                    BatteryReading(82, false),
                    BatteryReading(95, false),
                ),
            ),
            events[2],
        )
    }

    @Test
    fun air3ProProtocolUsesCapturedWriteShape() {
        val protocol = requireNotNull(VivoTwsAir3ProAdapter.createProtocol())

        val anc = protocol.encode(ControlRequest.SetNoiseMode(NoiseMode.ANC)).single()
        val off = protocol.encode(ControlRequest.SetNoiseMode(NoiseMode.OFF)).single()
        val transparency =
            protocol.encode(ControlRequest.SetNoiseMode(NoiseMode.TRANSPARENCY)).single()

        assertEquals("FF 03 00 03 00 1B 01 30 00 04 00", anc.hex())
        assertEquals("FF 03 00 03 00 1B 01 30 01 04 00", off.hex())
        assertEquals("FF 03 00 03 00 1B 01 30 02 04 00", transparency.hex())
    }

    private fun hex(value: String): ByteArray {
        val compact = value.filter { it.isDigit() || it.lowercaseChar() in 'a'..'f' }
        return ByteArray(compact.length / 2) { index ->
            compact.substring(index * 2, index * 2 + 2).toInt(16).toByte()
        }
    }

    private fun ByteArray.hex(): String =
        joinToString(" ") { "%02X".format(it.toInt() and 0xFF) }

    private fun identity(
        name: String?,
        standardHeadset: Boolean = false,
        nativeSystemEarbud: Boolean = false,
    ): EarbudIdentity = EarbudIdentity(
        deviceName = name,
        standardHeadset = standardHeadset,
        nativeSystemEarbud = nativeSystemEarbud,
    )
}
