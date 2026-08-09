package dev.hyperears.protocol.moondrop

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MoondropRobinWireCodecTest {
    @Test
    fun commandsUseDistinctQueryAndWriteValues() {
        assertEquals("FF 01 00 00 00 0A 03 00", MoondropRobinWireCodec.handshake.hex())
        assertEquals("FF 04 00 00 00 1D 1A 01", MoondropRobinWireCodec.queryBattery.hex())
        assertEquals("FF 04 00 00 00 1D 10 03", MoondropRobinWireCodec.queryNoiseMode.hex())
        assertEquals(
            "FF 04 00 01 00 1D 10 04 02",
            MoondropRobinWireCodec.setNoiseMode(MoondropRobinWireCodec.NoiseMode.ANC).hex(),
        )
        assertEquals(
            "FF 04 00 01 00 1D 10 04 04",
            MoondropRobinWireCodec
                .setNoiseMode(MoondropRobinWireCodec.NoiseMode.TRANSPARENCY)
                .hex(),
        )
    }

    @Test
    fun decoderHandlesFragmentationAndCoalescedFrames() {
        val decoder = MoondropRobinWireCodec.Decoder()
        val battery = MoondropRobinWireCodec.frame(
            command = 0x1D,
            subcommand = 0x1B,
            opcode = 0x01,
            parameters = byteArrayOf(1, 88, 2, 77),
        )
        val noise = MoondropRobinWireCodec.frame(
            command = 0x1D,
            subcommand = 0x11,
            opcode = 0x03,
            parameters = byteArrayOf(2, 1, 0, 0),
        )
        assertTrue(decoder.offer(battery.copyOfRange(0, 3)).isEmpty())
        val frames = decoder.offer(battery.copyOfRange(3, battery.size) + noise)
        assertEquals(2, frames.size)
        assertEquals(MoondropRobinWireCodec.BatteryState(88, 77), MoondropRobinWireCodec.parseBattery(frames[0]))
        assertEquals(MoondropRobinWireCodec.NoiseMode.TRANSPARENCY, MoondropRobinWireCodec.parseNoiseMode(frames[1]))
    }

    @Test
    fun malformedFramesAreRejected() {
        val decoder = MoondropRobinWireCodec.Decoder()
        val invalid = MoondropRobinWireCodec.frame(
            command = 0x1D,
            subcommand = 0x1B,
            opcode = 0x01,
            parameters = byteArrayOf(1, 101, 2, 20),
        )
        val frame = decoder.offer(invalid).single()
        assertNull(MoondropRobinWireCodec.parseBattery(frame))
        assertFalse(MoondropRobinWireCodec.parseHandshake(frame))
    }

    @Test
    fun handshakeRequiresExpectedResponsePayload() {
        val accepted = MoondropRobinWireCodec.Frame(0x0A, 0x83, 0x00, byteArrayOf(0, 4, 3, 1))
        val rejected = accepted.copy(parameters = byteArrayOf(0, 4, 3, 2))
        assertTrue(MoondropRobinWireCodec.parseHandshake(accepted))
        assertFalse(MoondropRobinWireCodec.parseHandshake(rejected))
    }

    private fun ByteArray.hex(): String = joinToString(" ") { "%02X".format(it.toInt() and 0xFF) }
}
