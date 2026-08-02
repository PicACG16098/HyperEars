package dev.hyperears.protocol.nicehck

import org.junit.Assert.assertEquals
import org.junit.Test

class NiceHckWireCodecTest {
    @Test
    fun commandsAndFragmentedResponsesUseLittleEndianLengthAndOpcode() {
        assertEquals("4E 03 00 00 05 00", NiceHckWireCodec.queryBattery.hex())
        assertEquals("4E 05 00 00 01 02 11 00", NiceHckWireCodec.setNoiseMode(NiceHckWireCodec.NoiseMode.WIND).hex())

        val battery = NiceHckWireCodec.command(0x0005, byteArrayOf(90, 82, 0))
        val noise = NiceHckWireCodec.command(0x0101, byteArrayOf(0x11))
        val decoder = NiceHckWireCodec.Decoder()
        assertEquals(emptyList<NiceHckWireCodec.Frame>(), decoder.offer(battery.take(4).toByteArray()))

        val frames = decoder.offer(battery.drop(4).toByteArray() + noise)
        assertEquals(2, frames.size)
        assertEquals(
            NiceHckWireCodec.BatteryState(90, 82, null),
            NiceHckWireCodec.parseBattery(frames[0]),
        )
        assertEquals(
            NiceHckWireCodec.NoiseMode.WIND,
            NiceHckWireCodec.parseNoiseMode(frames[1]),
        )
    }

    private fun ByteArray.hex(): String =
        joinToString(" ") { "%02X".format(it.toInt() and 0xFF) }
}
