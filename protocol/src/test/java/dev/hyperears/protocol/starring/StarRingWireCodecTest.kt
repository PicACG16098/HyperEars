package dev.hyperears.protocol.starring

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StarRingWireCodecTest {
    @Test
    fun batteryQueryMatchesCapturedOfficialAppWrite() {
        assertArrayEquals(
            StarRingWireCodec.hex("08 EE 00 00 00 01 01 0A 00 02"),
            StarRingWireCodec.queryBattery,
        )
    }

    @Test
    fun noiseCommandsMatchAllFourCapturedOneHotModes() {
        val expected = mapOf(
            StarRingWireCodec.NoiseMode.NORMAL to
                "08 EE 00 00 00 06 82 0E 00 00 01 00 00 8D",
            StarRingWireCodec.NoiseMode.TRANSPARENCY to
                "08 EE 00 00 00 06 82 0E 00 00 00 00 01 8D",
            StarRingWireCodec.NoiseMode.WIND to
                "08 EE 00 00 00 06 82 0E 00 00 00 01 00 8D",
            StarRingWireCodec.NoiseMode.ANC to
                "08 EE 00 00 00 06 82 0E 00 01 00 00 00 8D",
        )

        expected.forEach { (mode, frame) ->
            assertArrayEquals(
                StarRingWireCodec.hex(frame),
                StarRingWireCodec.setNoiseMode(mode),
            )
        }
    }

    @Test
    fun parsesCapturedWindModeReport() {
        val frame = StarRingWireCodec.Decoder().offer(
            StarRingWireCodec.hex(
                "09 FF 00 00 01 06 02 0E 00 00 00 01 00 20",
            ),
        ).single()

        assertEquals(
            StarRingWireCodec.NoiseMode.WIND,
            StarRingWireCodec.parseNoiseState(frame)?.mode,
        )
    }

    @Test
    fun parsesCapturedLeftRightAndUnavailableCaseBattery() {
        val decoder = StarRingWireCodec.Decoder()
        val frames = decoder.offer(
            StarRingWireCodec.hex(
                "09 FF 00 00 01 01 01 12 00 00 01 5E 5F 00 00 FF 00 DA",
            ),
        )

        assertEquals(1, frames.size)
        val battery = StarRingWireCodec.parseBatteryState(frames.single())
        assertEquals(94, battery?.leftPercent)
        assertEquals(95, battery?.rightPercent)
        assertNull(battery?.casePercent)
    }

    @Test
    fun decoderHandlesGarbageFragmentationAndCoalescedFrames() {
        val decoder = StarRingWireCodec.Decoder()
        val frame = StarRingWireCodec.hex(
            "09 FF 00 00 01 01 01 12 00 00 01 5E 5F 00 00 FF 00 DA",
        )

        assertTrue(decoder.offer(byteArrayOf(0x55, 0x09) + frame.copyOfRange(0, 5)).isEmpty())
        val frames = decoder.offer(frame.copyOfRange(5, frame.size) + frame)

        assertEquals(2, frames.size)
    }

    @Test
    fun rejectsCorruptedChecksum() {
        val corrupted = StarRingWireCodec.hex(
            "09 FF 00 00 01 01 01 12 00 00 01 5E 5F 00 00 FF 00 DB",
        )
        assertTrue(StarRingWireCodec.Decoder().offer(corrupted).isEmpty())
    }
}
