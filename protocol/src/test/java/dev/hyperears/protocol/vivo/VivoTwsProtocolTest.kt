package dev.hyperears.protocol.vivo

import dev.hyperears.protocol.vivo.VivoTwsProtocol.NoiseMode
import dev.hyperears.protocol.vivo.VivoTwsProtocol.WireConfig
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VivoTwsProtocolTest {
    @Test
    fun buildsAllKnownNoiseVariants() {
        assertArrayEquals(
            hex("FF 03 00 03 00 1B 01 30 00 04 00"),
            VivoTwsProtocol.setNoiseMode(NoiseMode.ANC, WireConfig.AIR3_PRO_CAPTURED),
        )
        assertArrayEquals(
            hex("FF 04 00 03 00 1B 01 30 02 03 01"),
            VivoTwsProtocol.setNoiseMode(
                NoiseMode.TRANSPARENCY,
                WireConfig.FAMILY_DEFAULT_V4,
            ),
        )
        assertArrayEquals(
            hex("FF 03 00 02 00 1B 01 30 01 03"),
            VivoTwsProtocol.setNoiseMode(NoiseMode.OFF, WireConfig.TWS_3E_V3),
        )
    }

    @Test
    fun buildsReadOnlyProbePackets() {
        assertArrayEquals(
            hex("FF 04 00 00 00 0A 03 00"),
            VivoTwsProtocol.handshake(),
        )
        assertArrayEquals(
            hex("FF 03 00 00 00 1B 02 30"),
            VivoTwsProtocol.queryNoiseMode(WireConfig.AIR3_PRO_CAPTURED),
        )
        assertArrayEquals(
            hex("FF 04 00 01 00 1B 02 30 00"),
            VivoTwsProtocol.queryNoiseMode(WireConfig.FAMILY_DEFAULT_V4),
        )
        assertArrayEquals(
            hex("FF 04 00 00 00 1B 02 07"),
            VivoTwsProtocol.queryBattery(),
        )
    }

    @Test
    fun decoderResynchronizesAndParsesSplitNoiseReport() {
        val decoder = VivoTwsProtocol.Decoder()
        assertTrue(decoder.offer(hex("11 22 FF 03 00 04 00 1B")).isEmpty())

        val frame = decoder.offer(hex("82 30 00 02 04 00")).single()
        val state = VivoTwsProtocol.parseNoiseState(frame)

        assertEquals(NoiseMode.TRANSPARENCY, state?.mode)
        assertEquals(4, state?.noiseEffect)
        assertEquals(0, state?.transparencyEffect)
        assertFalse(state?.acknowledged ?: true)
    }

    @Test
    fun parsesShortFamilyNoiseStateWithoutInventingOptionalEffects() {
        val frame = VivoTwsProtocol.Decoder()
            .offer(hex("FF 03 00 03 00 1B 81 30 00 02 03"))
            .single()

        val state = VivoTwsProtocol.parseNoiseState(frame)

        assertEquals(NoiseMode.TRANSPARENCY, state?.mode)
        assertEquals(3, state?.noiseEffect)
        assertNull(state?.transparencyEffect)
        assertTrue(state?.acknowledged == true)
    }

    @Test
    fun parsesBatteryAndChargingBitmap() {
        val frame = VivoTwsProtocol.Decoder()
            .offer(hex("FF 03 00 05 00 1B 82 07 00 5C 59 48 05"))
            .single()

        val battery = VivoTwsProtocol.parseBatteryState(frame)

        assertEquals(92, battery?.leftPercent)
        assertEquals(89, battery?.rightPercent)
        assertEquals(72, battery?.casePercent)
        assertTrue(battery?.leftCharging == true)
        assertFalse(battery?.rightCharging == true)
        assertTrue(battery?.caseCharging == true)
    }

    @Test
    fun parsesAir3ProResponsesCapturedOnDevice() {
        val decoder = VivoTwsProtocol.Decoder()
        val handshake = decoder
            .offer(hex("FF 03 00 04 00 0A 83 00 00 03 03 01"))
            .single()
        val noise = decoder
            .offer(hex("FF 03 00 04 00 1B 82 30 00 01 04 00"))
            .single()
        val battery = decoder
            .offer(hex("FF 03 00 05 00 1B 82 07 00 53 52 5F 00"))
            .single()
        val noiseAck = decoder
            .offer(hex("FF 03 00 04 00 1B 81 30 00 02 04 00"))
            .single()

        assertTrue(VivoTwsProtocol.parseHandshakeState(handshake)?.accepted == true)
        assertEquals(3, VivoTwsProtocol.parseHandshakeState(handshake)?.version)

        assertEquals(NoiseMode.OFF, VivoTwsProtocol.parseNoiseState(noise)?.mode)
        assertFalse(VivoTwsProtocol.parseNoiseState(noise)?.acknowledged ?: true)

        assertEquals(83, VivoTwsProtocol.parseBatteryState(battery)?.leftPercent)
        assertEquals(82, VivoTwsProtocol.parseBatteryState(battery)?.rightPercent)
        assertEquals(95, VivoTwsProtocol.parseBatteryState(battery)?.casePercent)

        assertEquals(NoiseMode.TRANSPARENCY, VivoTwsProtocol.parseNoiseState(noiseAck)?.mode)
        assertTrue(VivoTwsProtocol.parseNoiseState(noiseAck)?.acknowledged == true)
    }

    @Test
    fun decoderParsesConcatenatedAir3ProReports() {
        val frames = VivoTwsProtocol.Decoder().offer(
            hex(
                "FF 03 00 04 00 1B 82 30 00 01 04 00 " +
                    "FF 03 00 05 00 1B 82 07 00 53 52 FF 00 " +
                    "FF 03 00 02 00 1B 82 0D 00 04",
            ),
        )

        assertEquals(3, frames.size)
        assertEquals(VivoTwsProtocol.REPORT_NOISE_MODE, frames[0].command)
        assertEquals(VivoTwsProtocol.REPORT_BATTERY, frames[1].command)
        assertNull(VivoTwsProtocol.parseBatteryState(frames[1])?.casePercent)
        assertEquals(0x820D, frames[2].command)
    }

    @Test
    fun rejectsFailedOrUnrelatedFrames() {
        val failedNoise = VivoTwsProtocol.Decoder()
            .offer(hex("FF 03 00 04 00 1B 82 30 03 02 04 00"))
            .single()
        val unrelated = VivoTwsProtocol.Decoder()
            .offer(hex("FF 03 00 01 00 1B 82 55 00"))
            .single()

        assertNull(VivoTwsProtocol.parseNoiseState(failedNoise))
        assertNull(VivoTwsProtocol.parseBatteryState(unrelated))
    }

    private fun hex(value: String): ByteArray {
        val compact = value.filter { it.isDigit() || it.lowercaseChar() in 'a'..'f' }
        return ByteArray(compact.length / 2) { index ->
            compact.substring(index * 2, index * 2 + 2).toInt(16).toByte()
        }
    }
}
