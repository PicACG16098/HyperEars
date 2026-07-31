package dev.hyperears.protocol.oppo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class OppoWireCodecTest {
    @Test
    fun buildsReferenceReadAndWritePackets() {
        assertEquals(
            "AA 07 00 00 06 01 F0 00 00",
            OppoWireCodec.queryBattery.hex(),
        )
        assertEquals(
            "AA 09 00 00 0C 01 F0 02 00 01 01",
            OppoWireCodec.queryAnc.hex(),
        )
        assertEquals(
            "AA 07 00 00 00 02 F0 00 00",
            OppoWireCodec.queryNotificationSupport.hex(),
        )
        assertEquals(
            "AA 0A 00 00 04 04 F0 03 00 01 01 02",
            OppoWireCodec.setAnc(primary = 0x02).hex(),
        )
        assertEquals(
            "AA 0B 00 00 04 04 F0 04 00 01 01 00 08",
            OppoWireCodec.setAnc(primary = 0x00, secondary = 0x08).hex(),
        )
    }

    @Test
    fun parsesAndRegistersAdvertisedNotificationIds() {
        val response = decode(
            packet(
                command = OppoWireCodec.NOTIFICATION_SUPPORT_RESPONSE,
                payload = hex("00 04 01 02 03 F1"),
            ),
        )
        val ids = requireNotNull(OppoWireCodec.parseNotificationSupport(response))

        assertEquals("01 02 03 F1", ids.hex())
        assertEquals(
            "AA 0C 00 00 05 02 F0 05 00 04 01 02 03 F1",
            OppoWireCodec.registerNotifications(ids).hex(),
        )
    }

    @Test
    fun decoderHandlesFragmentationNoiseAndConcatenatedFrames() {
        val decoder = OppoWireCodec.Decoder()
        val battery = packet(
            command = OppoWireCodec.BATTERY_RESPONSE,
            payload = hex("01 4B 02 CA 03 64"),
        )
        val anc = packet(
            command = OppoWireCodec.ANC_RESPONSE,
            payload = hex("01 01 04 00"),
        )

        assertEquals(emptyList<OppoWireCodec.Frame>(), decoder.offer(byteArrayOf(0x55) + battery.take(4)))
        val frames = decoder.offer(battery.drop(4).toByteArray() + anc)

        assertEquals(2, frames.size)
        assertEquals(OppoWireCodec.BATTERY_RESPONSE, frames[0].command)
        assertEquals(OppoWireCodec.ANC_RESPONSE, frames[1].command)
        assertEquals("01 01 04 00", frames[1].payload.hex())
    }

    @Test
    fun parsesBatteryComponentsAndChargingBit() {
        val frame = decode(
            packet(
                command = OppoWireCodec.BATTERY_RESPONSE,
                payload = hex("01 4B 02 CA 03 64"),
            ),
        )

        assertEquals(
            OppoWireCodec.BatteryState(
                left = OppoWireCodec.BatteryReading(75, false),
                right = OppoWireCodec.BatteryReading(74, true),
                case = OppoWireCodec.BatteryReading(100, false),
            ),
            OppoWireCodec.parseBatteryState(frame),
        )
    }

    @Test
    fun parsesActiveBatteryReportsWithoutTreatingThemAsAnc() {
        val frame = decode(
            packet(
                command = OppoWireCodec.ACTIVE_REPORT,
                payload = hex("01 02 01 5A 02 52"),
            ),
        )

        assertEquals(
            OppoWireCodec.BatteryState(
                left = OppoWireCodec.BatteryReading(90, false),
                right = OppoWireCodec.BatteryReading(82, false),
                case = null,
            ),
            OppoWireCodec.parseBatteryState(frame),
        )
        assertNull(OppoWireCodec.parseAncState(frame))
    }

    @Test
    fun parsesAncResponseAndIgnoresWearReport() {
        assertEquals(
            OppoWireCodec.AncState(primary = 0x00, secondary = 0x08),
            OppoWireCodec.parseAncState(
                decode(
                    packet(
                        command = OppoWireCodec.ANC_RESPONSE,
                        payload = hex("00 01 01 00 08"),
                    ),
                ),
            ),
        )
        assertNull(
            OppoWireCodec.parseAncState(
                decode(
                    packet(
                        command = OppoWireCodec.ACTIVE_REPORT,
                        payload = hex("02 01 01 01"),
                    ),
                ),
            ),
        )
    }

    private fun packet(command: Int, payload: ByteArray): ByteArray =
        OppoWireCodec.packet(command = command, payload = payload)

    private fun decode(bytes: ByteArray): OppoWireCodec.Frame =
        OppoWireCodec.Decoder().offer(bytes).single()

    private fun hex(value: String): ByteArray {
        val compact = value.filter { it.isDigit() || it.lowercaseChar() in 'a'..'f' }
        return ByteArray(compact.length / 2) { index ->
            compact.substring(index * 2, index * 2 + 2).toInt(16).toByte()
        }
    }

    private fun ByteArray.hex(): String =
        joinToString(" ") { "%02X".format(it.toInt() and 0xFF) }
}
