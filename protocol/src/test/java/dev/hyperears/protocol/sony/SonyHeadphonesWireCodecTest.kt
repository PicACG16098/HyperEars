package dev.hyperears.protocol.sony

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SonyHeadphonesWireCodecTest {
    @Test
    fun encodesKnownInitFrame() {
        assertArrayEquals(
            bytes("3e 0c 00 00 00 00 02 00 00 0e 3c"),
            SonyHeadphonesWireCodec.encode(
                type = SonyHeadphonesWireCodec.MessageType.COMMAND_1,
                sequence = 0,
                payload = byteArrayOf(0x00, 0x00),
            ),
        )
    }

    @Test
    fun escapesReservedBytesAndDecodesAcrossChunks() {
        val encoded = SonyHeadphonesWireCodec.encode(
            type = SonyHeadphonesWireCodec.MessageType.COMMAND_1,
            sequence = 1,
            payload = bytes("3e 3d 3c"),
        )
        assertTrue(encoded.toHex().contains("3d 2e 3d 2d 3d 2c"))

        val decoder = SonyHeadphonesWireCodec.Decoder()
        assertTrue(decoder.offer(encoded.copyOfRange(0, 5)).isEmpty())
        val frames = decoder.offer(encoded.copyOfRange(5, encoded.size))

        assertEquals(1, frames.size)
        assertEquals(SonyHeadphonesWireCodec.MessageType.COMMAND_1, frames.single().type)
        assertEquals(1, frames.single().sequence)
        assertArrayEquals(bytes("3e 3d 3c"), frames.single().payload)
    }

    @Test
    fun rejectsCorruptChecksumAndResynchronizesAtNextHeader() {
        val valid = SonyHeadphonesWireCodec.encode(
            type = SonyHeadphonesWireCodec.MessageType.ACK,
            sequence = 1,
        )
        val corrupt = valid.copyOf().also { it[it.lastIndex - 1] = 0x7F }
        val decoder = SonyHeadphonesWireCodec.Decoder()

        val frames = decoder.offer(corrupt + byteArrayOf(0x55) + valid)

        assertEquals(1, frames.size)
        assertEquals(SonyHeadphonesWireCodec.MessageType.ACK, frames.single().type)
    }

    private fun bytes(hex: String): ByteArray = hex
        .trim()
        .split(Regex("\\s+"))
        .map { it.toInt(16).toByte() }
        .toByteArray()

    private fun ByteArray.toHex(): String =
        joinToString(" ") { "%02x".format(it.toInt() and 0xFF) }
}
