package dev.hyperears.protocol.edifier

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class EdifierWireCodecTest {

    // ── Real captured frames from Edifier W860NB PRO (via protocol-test) ──

    /** Battery response: BB EC D0 00 01 99 11 -> payload[0]=0x99 ^ 0xA5 = 0x3C = 60% */
    @Test
    fun `parse battery response 0x99 gives 60 percent`() {
        val bytes = byteArrayOf(
            0xBB.toByte(), 0xEC.toByte(), 0xD0.toByte(), 0x00.toByte(), 0x01.toByte(),
            0x99.toByte(), 0x11.toByte(),
        )
        val frames = EdifierWireCodec.Decoder().offer(bytes)
        assertEquals(1, frames.size)
        val battery = EdifierWireCodec.parseBatteryState(frames[0])
        assertNotNull(battery)
        assertEquals(60, battery!!.wholeUnit)
    }

    /** ANC response for NC off: BB EC CC 00 02 B5 A0 CA -> payload B5 A0 -> 10 05 */
    @Test
    fun `parse ANC response B5 A0 gives ancIndex 16 ancValue 5`() {
        val bytes = byteArrayOf(
            0xBB.toByte(), 0xEC.toByte(), 0xCC.toByte(), 0x00.toByte(), 0x02.toByte(),
            0xB5.toByte(), 0xA0.toByte(), 0xCA.toByte(),
        )
        val frames = EdifierWireCodec.Decoder().offer(bytes)
        assertEquals(1, frames.size)
        val anc = EdifierWireCodec.parseAncState(frames[0])
        assertNotNull(anc)
        assertEquals(0x10, anc!!.mode) // ancIndex 16
        assertEquals(5, anc.level) // ancValue 5 = NC off
    }

    /** ANC response for comfort NC: BB EC CC 00 02 B5 A7 D1 -> 10 02 */
    @Test
    fun `parse ANC response B5 A7 gives ancValue 2 comfort`() {
        val bytes = byteArrayOf(
            0xBB.toByte(), 0xEC.toByte(), 0xCC.toByte(), 0x00.toByte(), 0x02.toByte(),
            0xB5.toByte(), 0xA7.toByte(), 0xD1.toByte(),
        )
        val frames = EdifierWireCodec.Decoder().offer(bytes)
        assertEquals(1, frames.size)
        val anc = EdifierWireCodec.parseAncState(frames[0])
        assertEquals(2, anc!!.level)
    }

    // ── Send framing ──

    /** Battery query: AA EC D0 00 00 66 */
    @Test
    fun `battery query frame matches real capture`() {
        val expected = byteArrayOf(
            0xAA.toByte(), 0xEC.toByte(), 0xD0.toByte(), 0x00.toByte(), 0x00.toByte(), 0x66.toByte(),
        )
        val actual = EdifierWireCodec.queryBattery
        assertEquals(expected.toHex(), actual.toHex())
    }

    /**
     * ANC set (deep NC): payload 10 01 -> encrypted B5 A4. raw = AA EC C1 00 02 B5 A4 B2
     */
    @Test
    fun `set ANC deep produces expected frame`() {
        val expected = byteArrayOf(
            0xAA.toByte(), 0xEC.toByte(), 0xC1.toByte(), 0x00.toByte(), 0x02.toByte(),
            0xB5.toByte(), 0xA4.toByte(), 0xB2.toByte(),
        )
        val actual = EdifierWireCodec.setAnc(EdifierWireCodec.ANC_VALUE_DEEP)
        assertEquals(expected.toHex(), actual.toHex())
    }

    /** ANC set (NC off): payload 10 05 -> encrypted B5 A0. raw = AA EC C1 00 02 B5 A0 AE */
    @Test
    fun `set ANC off produces expected frame`() {
        val expected = byteArrayOf(
            0xAA.toByte(), 0xEC.toByte(), 0xC1.toByte(), 0x00.toByte(), 0x02.toByte(),
            0xB5.toByte(), 0xA0.toByte(), 0xAE.toByte(),
        )
        val actual = EdifierWireCodec.setAnc(EdifierWireCodec.ANC_VALUE_OFF)
        assertEquals(expected.toHex(), actual.toHex())
    }

    @Test
    fun `garbage input is discarded`() {
        val bytes = byteArrayOf(
            0x01, 0x02, 0x03, 0xAA.toByte(), 0xEC.toByte(), 0xD0.toByte(), 0x00.toByte(),
            0x00.toByte(), 0x66.toByte(),
        )
        val frames = EdifierWireCodec.Decoder().offer(bytes)
        assertEquals(1, frames.size)
    }

    @Test
    fun `null battery on empty payload`() {
        val frame = EdifierWireCodec.Frame(
            header = EdifierWireCodec.RECEIVE_HEADER,
            appCode = EdifierWireCodec.APP_CODE,
            commandIndex = EdifierWireCodec.CMD_BATTERY_QUERY,
            payload = byteArrayOf(),
            bytes = byteArrayOf(),
        )
        assertNull(EdifierWireCodec.parseBatteryState(frame))
    }

    @Test
    fun `outbound echo cannot establish battery or ANC evidence`() {
        val batteryEcho = EdifierWireCodec.Decoder().offer(EdifierWireCodec.queryBattery).single()
        val ancEcho = EdifierWireCodec.Decoder().offer(
            EdifierWireCodec.setAnc(EdifierWireCodec.ANC_VALUE_DEEP),
        ).single()

        assertNull(EdifierWireCodec.parseBatteryState(batteryEcho))
        assertNull(EdifierWireCodec.parseAncState(ancEcho))
    }

    private fun ByteArray.toHex(): String =
        joinToString(" ") { "%02X".format(it.toInt() and 0xFF) }
}
