package dev.hyperears.protocol.bose

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BoseBmapWireCodecTest {
    @Test
    fun encodesCapturedReadOnlyQueries() {
        assertArrayEquals(
            BoseBmapWireCodec.hex("00 03 01 00"),
            BoseBmapWireCodec.queryProductIdentity,
        )
        assertArrayEquals(
            BoseBmapWireCodec.hex("02 02 01 00"),
            BoseBmapWireCodec.queryBattery,
        )
        assertArrayEquals(
            BoseBmapWireCodec.hex("1F 03 01 00"),
            BoseBmapWireCodec.queryCurrentMode,
        )
        assertArrayEquals(
            BoseBmapWireCodec.hex("1F 06 05 00"),
            BoseBmapWireCodec.queryModeConfigs,
        )
        assertArrayEquals(
            BoseBmapWireCodec.hex("1F 03 05 02 01 00"),
            BoseBmapWireCodec.switchMode(index = 1),
        )
    }

    @Test
    fun decodesPrinceProductIdentityIncrementally() {
        val decoder = BoseBmapWireCodec.Decoder()
        val bytes = BoseBmapWireCodec.hex("00 03 03 03 40 75 02")

        assertEquals(emptyList<BoseBmapWireCodec.Frame>(), decoder.offer(bytes.copyOfRange(0, 3)))
        val identity = BoseBmapWireCodec.parseProductIdentity(
            decoder.offer(bytes.copyOfRange(3, bytes.size)).single(),
        )

        assertEquals(0x4075, identity?.productId)
        assertEquals(2, identity?.variant)
    }

    @Test
    fun parsesSingleBatteryHeadphoneCapture() {
        val frame = BoseBmapWireCodec.Decoder().offer(
            BoseBmapWireCodec.hex("02 02 03 04 50 FF FF 00"),
        ).single()

        val battery = BoseBmapWireCodec.parseBatteryState(frame)

        assertEquals(80, battery?.overallPercent)
        assertNull(battery?.leftPercent)
        assertNull(battery?.rightPercent)
        assertNull(battery?.casePercent)
        assertNull(battery?.components?.single()?.remainingPlayTimeMinutes)
    }

    @Test
    fun parsesPerComponentEarbudBatteryGroups() {
        val frame = BoseBmapWireCodec.Decoder().offer(
            BoseBmapWireCodec.hex(
                "02 02 03 10 " +
                    "64 FF FF 01 52 FF FF 02 32 FF FF 03 64 FF FF 04",
            ),
        ).single()

        val battery = BoseBmapWireCodec.parseBatteryState(frame)

        assertEquals(100, battery?.overallPercent)
        assertEquals(100, battery?.leftPercent)
        assertEquals(82, battery?.rightPercent)
        assertEquals(50, battery?.casePercent)
    }

    @Test
    fun rejectsMalformedBatteryPayload() {
        val frame = BoseBmapWireCodec.Decoder().offer(
            BoseBmapWireCodec.hex("02 02 03 03 5A FF FF"),
        ).single()

        assertNull(BoseBmapWireCodec.parseBatteryState(frame))
    }

    @Test
    fun parsesCapturedPrinceModeConfigLayout() {
        val payload = ByteArray(47).apply {
            this[0] = 3
            this[1] = 0
            this[2] = 12
            "Music".toByteArray().copyInto(this, destinationOffset = 6)
            this[42] = 7
            this[43] = 0
            this[44] = 0
            this[46] = 1
        }
        val frame = BoseBmapWireCodec.Decoder().offer(
            BoseBmapWireCodec.packet(
                functionBlock = 0x1F,
                function = 0x06,
                operator = BoseBmapWireCodec.Operator.STATUS,
                payload = payload,
            ),
        ).single()

        val config = BoseBmapWireCodec.parseModeConfig(frame)

        assertEquals(3, config?.index)
        assertEquals(12, config?.prompt)
        assertEquals("Music", config?.name)
        assertEquals(7, config?.rawCnc)
        assertEquals(false, config?.autoCnc)
        assertEquals(0, config?.spatial)
        assertEquals(true, config?.wind)
    }

    @Test
    fun parsesCurrentModeStatus() {
        val frame = BoseBmapWireCodec.Decoder().offer(
            BoseBmapWireCodec.hex("1F 03 03 01 01"),
        ).single()

        assertEquals(1, BoseBmapWireCodec.parseCurrentMode(frame))
    }
}
