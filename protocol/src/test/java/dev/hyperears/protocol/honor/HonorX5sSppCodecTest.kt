package dev.hyperears.protocol.honor

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HonorX5sSppCodecTest {

    @Test
    fun modeCommandsMatchCapturedVendorFrames() {
        assertEquals(
            "5A 00 07 00 2B 04 01 02 01 00 E1 1C",
            HonorX5sSppCodec.modeCommand(HonorX5sSppCodec.NoiseMode.ANC).hex(),
        )
        assertEquals(
            "5A 00 07 00 2B 04 01 02 02 00 B4 4F",
            HonorX5sSppCodec.modeCommand(HonorX5sSppCodec.NoiseMode.TRANSPARENCY).hex(),
        )
        assertEquals(
            "5A 00 07 00 2B 04 01 02 00 00 D2 2D",
            HonorX5sSppCodec.modeCommand(HonorX5sSppCodec.NoiseMode.OFF).hex(),
        )
    }

    @Test
    fun ancDepthCommandsMatchCapturedVendorFrames() {
        assertEquals(
            "5A 00 07 00 2B 04 01 02 01 01 F1 3D",
            HonorX5sSppCodec.modeCommand(
                HonorX5sSppCodec.NoiseMode.ANC,
                HonorX5sSppCodec.AncDepth.SMART,
            ).hex(),
        )
        assertEquals(
            "5A 00 07 00 2B 04 01 02 01 02 C1 5E",
            HonorX5sSppCodec.modeCommand(
                HonorX5sSppCodec.NoiseMode.ANC,
                HonorX5sSppCodec.AncDepth.LIGHT,
            ).hex(),
        )
        assertEquals(
            "5A 00 07 00 2B 04 01 02 01 03 D1 7F",
            HonorX5sSppCodec.modeCommand(
                HonorX5sSppCodec.NoiseMode.ANC,
                HonorX5sSppCodec.AncDepth.MEDIUM,
            ).hex(),
        )
    }

    @Test
    fun decoderSplitsCompleteFrames() {
        val decoder = HonorX5sSppCodec.Decoder()
        val frames = decoder.offer(
            hex("5A 00 07 00 2B 2A 01 02 01 00 26 00") +
                hex("5A 00 07 00 2B 2A 01 02 00 00 15 31"),
        )
        assertEquals(2, frames.size)
        assertArrayEquals(hex("5A 00 07 00 2B 2A 01 02 01 00 26 00"), frames[0])
        assertArrayEquals(hex("5A 00 07 00 2B 2A 01 02 00 00 15 31"), frames[1])
    }

    @Test
    fun decoderBuffersPartialFramesAcrossOffers() {
        val decoder = HonorX5sSppCodec.Decoder()
        val full = hex("5A 00 07 00 2B 2A 01 02 01 01 36 21")
        assertEquals(0, decoder.offer(full.copyOfRange(0, 5)).size)
        val frames = decoder.offer(full.copyOfRange(5, full.size))
        assertEquals(1, frames.size)
        assertArrayEquals(full, frames[0])
    }

    @Test
    fun decoderDropsLeadingNoiseAndResyncs() {
        val decoder = HonorX5sSppCodec.Decoder()
        val frames = decoder.offer(
            byteArrayOf(0x01, 0x02, 0x03) + hex("5A 00 07 00 2B 2A 01 02 00 00 15 31"),
        )
        assertEquals(1, frames.size)
        assertArrayEquals(hex("5A 00 07 00 2B 2A 01 02 00 00 15 31"), frames[0])
    }

    @Test
    fun decoderSkipsMalformedLengthAndResyncs() {
        val decoder = HonorX5sSppCodec.Decoder()
        // First marker claims an invalid length (0x00 payload); the decoder must skip it and
        // resync on the next marker.
        val frames = decoder.offer(
            hex("5A 00 00 00 01 02 03 04") + hex("5A 00 07 00 2B 2A 01 02 00 00 15 31"),
        )
        assertEquals(1, frames.size)
        assertArrayEquals(hex("5A 00 07 00 2B 2A 01 02 00 00 15 31"), frames[0])
    }

    @Test
    fun decoderResetClearsBuffer() {
        val decoder = HonorX5sSppCodec.Decoder()
        decoder.offer(hex("5A 00 07 00 2B 2A 01 02"))
        decoder.reset()
        val frames = decoder.offer(hex("5A 00 07 00 2B 2A 01 02 00 00 15 31"))
        assertEquals(1, frames.size)
    }

    @Test
    fun decoderEmptyInputReturnsNothing() {
        assertEquals(0, HonorX5sSppCodec.Decoder().offer(ByteArray(0)).size)
    }

    @Test
    fun batteryReportFramesDecodeComponents() {
        val report = HonorX5sSppCodec.parseBatteryFrame(
            hex("5A 00 10 00 01 27 01 01 47 02 03 64 64 47 03 03 64 64 00 EF 9F"),
        )!!
        assertEquals(100, report.leftPercent)
        assertEquals(100, report.rightPercent)
        assertEquals(71, report.casePercent)

        val report2 = HonorX5sSppCodec.parseBatteryFrame(
            hex("5A 00 10 00 01 08 01 01 46 02 03 64 64 46 03 03 64 64 00 74 D4"),
        )!!
        assertEquals(100, report2.leftPercent)
        assertEquals(100, report2.rightPercent)
        assertEquals(70, report2.casePercent)
    }

    @Test
    fun zeroPercentComponentsDecodeAsNotConnected() {
        val report = HonorX5sSppCodec.parseBatteryFrame(
            hex("5A 00 10 00 01 27 01 01 47 02 03 00 64 47 03 03 64 64 00 EF 9F"),
        )!!
        assertEquals(null, report.leftPercent)
        assertEquals(100, report.rightPercent)
        assertEquals(71, report.casePercent)

        val at = HonorX5sSppCodec.parseHuaweiBattery(
            "AT+HUAWEIBATTERY=6,2,0,3,0,4,100,5,0,6,71,7,0",
        )!!
        assertEquals(null, at.leftPercent)
        assertEquals(100, at.rightPercent)
        assertEquals(71, at.casePercent)
    }

    @Test
    fun chargingFramesDecodeCaseChargingFlag() {
        val charging = HonorX5sSppCodec.parseBatteryFrame(
            hex("5A 00 10 00 01 27 01 01 49 02 03 64 64 49 03 03 64 64 01 2C 67"),
        )!!
        assertEquals(73, charging.casePercent)
        assertTrue(charging.caseCharging)

        val idle = HonorX5sSppCodec.parseBatteryFrame(
            hex("5A 00 10 00 01 27 01 01 47 02 03 64 64 47 03 03 64 64 00 EF 9F"),
        )!!
        assertEquals(71, idle.casePercent)
        assertFalse(idle.caseCharging)
    }

    @Test
    fun stateFramesDecodeDepthAndMode() {
        assertEquals(
            HonorX5sSppCodec.State(HonorX5sSppCodec.NoiseMode.ANC, HonorX5sSppCodec.AncDepth.SMART),
            HonorX5sSppCodec.stateFromFrame(hex("5A 00 07 00 2B 2A 01 02 01 01 36 21")),
        )
        assertEquals(
            HonorX5sSppCodec.State(HonorX5sSppCodec.NoiseMode.ANC, HonorX5sSppCodec.AncDepth.LIGHT),
            HonorX5sSppCodec.stateFromFrame(hex("5A 00 07 00 2B 2A 01 02 01 02 63 72")),
        )
        assertEquals(
            HonorX5sSppCodec.State(HonorX5sSppCodec.NoiseMode.TRANSPARENCY, null),
            HonorX5sSppCodec.stateFromFrame(hex("5A 00 07 00 2B 2A 01 02 00 02 35 73")),
        )
        assertEquals(
            HonorX5sSppCodec.State(HonorX5sSppCodec.NoiseMode.OFF, null),
            HonorX5sSppCodec.stateFromFrame(hex("5A 00 07 00 2B 2A 01 02 00 00 15 31")),
        )
        // Connect-init frame (0x00, 0x01) decodes as deep ANC.
        assertEquals(
            HonorX5sSppCodec.State(HonorX5sSppCodec.NoiseMode.ANC, HonorX5sSppCodec.AncDepth.DEEP),
            HonorX5sSppCodec.stateFromFrame(hex("5A 00 07 00 2B 2A 01 02 00 01 05 10")),
        )
    }

    @Test
    fun inCaseStateFrameDecodesAsOff() {
        // Captured 09:46 with both earbuds in the case (default off): (0x01, 0x00) is off even
        // under the two-eardrum mapping.
        assertEquals(
            HonorX5sSppCodec.State(HonorX5sSppCodec.NoiseMode.OFF, null),
            HonorX5sSppCodec.stateFromFrame(hex("5A 00 07 00 2B 2A 01 02 01 00 26 00")),
        )
    }

    @Test
    fun singleEarbudStateFramesUseAlternateEncoding() {
        assertEquals(
            HonorX5sSppCodec.State(HonorX5sSppCodec.NoiseMode.OFF, null),
            HonorX5sSppCodec.stateFromFrame(
                hex("5A 00 07 00 2B 2A 01 02 01 00 26 00"),
                singleEarbud = true,
            ),
        )
        assertEquals(
            HonorX5sSppCodec.State(HonorX5sSppCodec.NoiseMode.ANC, null),
            HonorX5sSppCodec.stateFromFrame(
                hex("5A 00 07 00 2B 2A 01 02 01 01 36 21"),
                singleEarbud = true,
            ),
        )
        assertEquals(
            HonorX5sSppCodec.State(HonorX5sSppCodec.NoiseMode.ANC, null),
            HonorX5sSppCodec.stateFromFrame(
                hex("5A 00 07 00 2B 2A 01 02 00 01 05 10"),
                singleEarbud = true,
            ),
        )
    }

    @Test
    fun heartbeatAndUnknownFrames() {
        assertTrue(HonorX5sSppCodec.isHeartbeat(hex("5A 00 05 00 2B 79 01 00 45 E0")))
        assertFalse(HonorX5sSppCodec.isHeartbeat(hex("5A 00 07 00 2B 04 01 02 01 00 E1 1C")))
        assertTrue(HonorX5sSppCodec.isKnownFrame(hex("5A 00 05 00 2B 79 01 00 45 E0")))
        assertTrue(HonorX5sSppCodec.isKnownFrame(hex("5A 00 10 00 01 27 01 01 47 02 03 64 64 47 03 03 64 64 00 EF 9F")))
        assertFalse(HonorX5sSppCodec.isKnownFrame(hex("5A 00 09 00 01 3A 01 10 02 0E 03 04 00 00 00 03 04")))
    }

    @Test
    fun batteryQueryMatchesCapturedVendorFrame() {
        assertEquals(
            "5A 00 09 00 01 08 01 00 02 00 03 00 FB B9",
            HonorX5sSppCodec.queryBattery.hex(),
        )
    }

    private fun hex(value: String): ByteArray {
        val compact = value.filterNot(Char::isWhitespace)
        return ByteArray(compact.length / 2) { index ->
            compact.substring(index * 2, index * 2 + 2).toInt(16).toByte()
        }
    }

    private fun ByteArray.hex(): String = joinToString(" ") {
        (it.toInt() and 0xFF).toString(16).padStart(2, '0').uppercase()
    }
}
