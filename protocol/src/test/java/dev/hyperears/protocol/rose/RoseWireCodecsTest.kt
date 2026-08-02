package dev.hyperears.protocol.rose

import org.junit.Assert.assertEquals
import org.junit.Test

class RoseWireCodecsTest {
    @Test
    fun earfreeI5DecodesFragmentedBatteryAndNoiseFrames() {
        val battery = response(
            group = 0x01,
            command = 0x01,
            payload = byteArrayOf(0, 0, 91, 82, 1, 0, 67),
        )
        val noise = response(
            group = 0x06,
            command = 0x02,
            payload = byteArrayOf(0, 0, 1, 0),
        )
        val decoder = RoseEarfreeI5WireCodec.Decoder()

        assertEquals(emptyList<RoseEarfreeI5WireCodec.Frame>(), decoder.offer(battery.take(6).toByteArray()))
        val frames = decoder.offer(battery.drop(6).toByteArray() + noise)

        assertEquals(2, frames.size)
        assertEquals(
            RoseEarfreeI5WireCodec.BatteryState(91, 82, 67, true, false),
            RoseEarfreeI5WireCodec.parseBattery(frames[0]),
        )
        assertEquals(
            RoseEarfreeI5WireCodec.NoiseMode.WIND,
            RoseEarfreeI5WireCodec.parseNoiseMode(frames[1]),
        )
        assertEquals(
            "08 EE 00 00 00 06 82 0E 00 00 00 01 00 8D",
            RoseEarfreeI5WireCodec
                .setNoiseMode(RoseEarfreeI5WireCodec.NoiseMode.WIND)
                .hex(),
        )
    }

    @Test
    fun budsFeelBuildsSequencedCommandsAndDecodesTlvStatus() {
        assertEquals(
            "FF 2A 02 09 04 38 AA",
            RoseBudsFeelMk2WireCodec
                .setNoiseMode(0x2A, RoseBudsFeelMk2WireCodec.NoiseMode.WIND)
                .hex(),
        )

        val body = byteArrayOf(
            0xDD.toByte(), 0x2A, 0x15,
            0x04, 0x0C, 90, 81, 55,
            0x02, 0x09, 0x04,
        )
        val response = body + byteArrayOf(body.checksum(), 0xAA.toByte())
        val decoder = RoseBudsFeelMk2WireCodec.Decoder()
        val split = response.size / 2

        assertEquals(emptyList<RoseBudsFeelMk2WireCodec.State>(), decoder.offer(response.copyOfRange(0, split)))
        assertEquals(
            listOf(
                RoseBudsFeelMk2WireCodec.State.Battery(90, 81, 55),
                RoseBudsFeelMk2WireCodec.State.Noise(RoseBudsFeelMk2WireCodec.NoiseMode.WIND),
            ),
            decoder.offer(response.copyOfRange(split, response.size)),
        )
    }

    private fun response(group: Int, command: Int, payload: ByteArray): ByteArray {
        val size = 10 + payload.size
        val body = byteArrayOf(
            0x09,
            0xFF.toByte(),
            0,
            0,
            1,
            group.toByte(),
            command.toByte(),
            size.toByte(),
            0,
        ) + payload
        return body + byteArrayOf(body.checksum())
    }

    private fun ByteArray.checksum(): Byte =
        sumOf { it.toInt() and 0xFF }.and(0xFF).toByte()

    private fun ByteArray.hex(): String =
        joinToString(" ") { "%02X".format(it.toInt() and 0xFF) }
}
