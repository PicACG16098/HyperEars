package dev.hyperears.protocol.qcy

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class QcyWireCodecTest {
    @Test
    fun buildsOnlyBatteryAndThreeStateNoiseCommands() {
        assertEquals("FF 03 FE 01 2F", QcyWireCodec.queryBattery.hex())
        assertEquals("FF 03 FE 01 0C", QcyWireCodec.queryNoiseMode.hex())
        assertEquals(
            "FF 03 0C 01 00",
            QcyWireCodec.setNoiseMode(QcyWireCodec.NoiseMode.OFF).hex(),
        )
        assertEquals(
            "FF 03 0C 01 01",
            QcyWireCodec.setNoiseMode(QcyWireCodec.NoiseMode.ANC).hex(),
        )
        assertEquals(
            "FF 03 0C 01 03",
            QcyWireCodec.setNoiseMode(QcyWireCodec.NoiseMode.TRANSPARENCY).hex(),
        )
    }

    @Test
    fun decodesFragmentedPacketContainingBatteryAndNoiseState() {
        val packet = hex("FF 08 2F 03 D7 4C 40 0C 01 03")
        val decoder = QcyWireCodec.Decoder()

        assertEquals(emptyList<QcyWireCodec.Frame>(), decoder.offer(packet.copyOfRange(0, 4)))
        val frames = decoder.offer(packet.copyOfRange(4, packet.size))

        assertEquals(2, frames.size)
        assertEquals(
            QcyWireCodec.BatteryState(
                left = QcyWireCodec.BatteryCell(percent = 87, charging = true),
                right = QcyWireCodec.BatteryCell(percent = 76, charging = false),
                case = QcyWireCodec.BatteryCell(percent = 64, charging = false),
            ),
            QcyWireCodec.parseBattery(frames[0]),
        )
        assertEquals(
            QcyWireCodec.NoiseMode.TRANSPARENCY,
            QcyWireCodec.parseNoiseMode(frames[1]),
        )
    }

    @Test
    fun rejectsMalformedCommandAndResynchronizesAtNextFrame() {
        val malformed = hex("FF 03 2F 03 64")
        val valid = hex("FF 03 0C 01 02")
        val frames = QcyWireCodec.Decoder().offer(byteArrayOf(0x01, 0x02) + malformed + valid)

        assertEquals(1, frames.size)
        assertEquals(QcyWireCodec.NoiseMode.OUTDOOR, QcyWireCodec.parseNoiseMode(frames.single()))
    }

    @Test
    fun rejectsBatteryFrameWhenEveryComponentIsOutsidePercentageRange() {
        val frame = QcyWireCodec.Decoder()
            .offer(hex("FF 05 2F 03 7F 7E FD"))
            .single()

        assertNull(QcyWireCodec.parseBattery(frame))
    }

    @Test
    fun parsesPublicAdvertisementIdentityLayout() {
        val payload = ByteArray(24).apply {
            this[0] = 0x4D
            this[1] = 0x55
            this[12] = 0xAA.toByte()
            this[11] = 0xBB.toByte()
            this[13] = 0xCC.toByte()
            this[16] = 0xDD.toByte()
            this[15] = 0xEE.toByte()
            this[14] = 0xFF.toByte()
            this[19] = 0x11
            this[18] = 0x22
            this[20] = 0x33
            this[23] = 0x44
            this[22] = 0x55
            this[21] = 0x66
        }

        assertEquals(
            QcyAdvertisementCodec.Advertisement(
                controlAddress = "AA:BB:CC:DD:EE:FF",
                otherAddress = "11:22:33:44:55:66",
            ),
            QcyAdvertisementCodec.parse(payload),
        )
    }

    private fun hex(value: String): ByteArray = value
        .trim()
        .split(Regex("\\s+"))
        .map { it.toInt(16).toByte() }
        .toByteArray()

    private fun ByteArray.hex(): String =
        joinToString(" ") { "%02X".format(it.toInt() and 0xFF) }
}
