package dev.hyperears.protocol.rose

/**
 * Business-frame codec used by the ROSESELSA EARFREE i5.
 *
 * BLE/GATT framing is intentionally absent here. The decoder accepts arbitrary notification
 * chunks so the same protocol remains testable without Android Bluetooth classes.
 */
object RoseEarfreeI5WireCodec {
    enum class NoiseMode(val bits: ByteArray) {
        ANC(byteArrayOf(1, 0, 0, 0)),
        OFF(byteArrayOf(0, 1, 0, 0)),
        WIND(byteArrayOf(0, 0, 1, 0)),
        TRANSPARENCY(byteArrayOf(0, 0, 0, 1)),
    }

    data class Frame(
        val group: Int,
        val command: Int,
        val payload: ByteArray,
    )

    data class BatteryState(
        val leftPercent: Int?,
        val rightPercent: Int?,
        val casePercent: Int?,
        val leftCharging: Boolean,
        val rightCharging: Boolean,
    )

    val queryBattery: ByteArray = hex("08 EE 00 00 00 01 01 0A 00 02")
    val queryNoiseMode: ByteArray = hex("08 EE 00 00 00 06 02 0A 00 08")

    fun setNoiseMode(mode: NoiseMode): ByteArray {
        val body = hex("08 EE 00 00 00 06 82 0E 00") + mode.bits
        return body + byteArrayOf(body.checksum())
    }

    fun parseBattery(frame: Frame): BatteryState? {
        if (frame.group != BATTERY_GROUP || frame.command != BATTERY_COMMAND) return null
        if (frame.payload.size < BATTERY_PAYLOAD_SIZE) return null
        return BatteryState(
            leftPercent = frame.payload[LEFT_OFFSET].batteryPercent(),
            rightPercent = frame.payload[RIGHT_OFFSET].batteryPercent(),
            casePercent = frame.payload[CASE_OFFSET].batteryPercent(),
            leftCharging = frame.payload[LEFT_CHARGING_OFFSET].unsigned() == 1,
            rightCharging = frame.payload[RIGHT_CHARGING_OFFSET].unsigned() == 1,
        )
    }

    fun parseNoiseMode(frame: Frame): NoiseMode? {
        if (frame.group != NOISE_GROUP || frame.command != NOISE_COMMAND) return null
        if (frame.payload.size < NOISE_PAYLOAD_SIZE) return null
        val bits = frame.payload.copyOfRange(0, NOISE_PAYLOAD_SIZE)
        return NoiseMode.entries.firstOrNull { it.bits.contentEquals(bits) }
    }

    class Decoder {
        private var pending = ByteArray(0)

        fun offer(bytes: ByteArray): List<Frame> {
            if (bytes.isEmpty()) return emptyList()
            pending += bytes
            val result = mutableListOf<Frame>()

            while (pending.size >= MIN_FRAME_SIZE) {
                val marker = pending.indexOfMarker()
                if (marker < 0) {
                    pending = pending.takeLast(RESPONSE_MARKER.size - 1).toByteArray()
                    break
                }
                if (marker > 0) pending = pending.copyOfRange(marker, pending.size)
                if (pending.size <= LENGTH_OFFSET) break

                val frameSize = pending[LENGTH_OFFSET].unsigned()
                if (frameSize !in MIN_FRAME_SIZE..MAX_FRAME_SIZE) {
                    pending = pending.copyOfRange(1, pending.size)
                    continue
                }
                if (pending.size < frameSize) break

                val candidate = pending.copyOfRange(0, frameSize)
                pending = pending.copyOfRange(frameSize, pending.size)
                if (!candidate.hasValidChecksum()) continue

                result += Frame(
                    group = candidate[GROUP_OFFSET].unsigned(),
                    command = candidate[COMMAND_OFFSET].unsigned(),
                    payload = candidate.copyOfRange(PAYLOAD_OFFSET, candidate.lastIndex),
                )
            }
            return result
        }

        fun reset() {
            pending = ByteArray(0)
        }
    }

    private fun ByteArray.indexOfMarker(): Int {
        if (size < RESPONSE_MARKER.size) return -1
        return (0..size - RESPONSE_MARKER.size).firstOrNull { offset ->
            RESPONSE_MARKER.indices.all { this[offset + it] == RESPONSE_MARKER[it] }
        } ?: -1
    }

    private fun ByteArray.hasValidChecksum(): Boolean =
        isNotEmpty() && dropLast(1).sumOf { it.unsigned() }.and(0xFF) == last().unsigned()

    private fun ByteArray.checksum(): Byte = sumOf { it.unsigned() }.and(0xFF).toByte()
    private fun Byte.batteryPercent(): Int? = unsigned().takeIf { it in 0..100 }
    private fun Byte.unsigned(): Int = toInt() and 0xFF

    private fun hex(value: String): ByteArray {
        val compact = value.filterNot(Char::isWhitespace)
        return ByteArray(compact.length / 2) { index ->
            compact.substring(index * 2, index * 2 + 2).toInt(16).toByte()
        }
    }

    private const val GROUP_OFFSET = 5
    private const val COMMAND_OFFSET = 6
    private const val LENGTH_OFFSET = 7
    private const val PAYLOAD_OFFSET = 9
    private const val MIN_FRAME_SIZE = 10
    private const val MAX_FRAME_SIZE = 255
    private const val BATTERY_GROUP = 0x01
    private const val BATTERY_COMMAND = 0x01
    private const val BATTERY_PAYLOAD_SIZE = 7
    private const val LEFT_OFFSET = 2
    private const val RIGHT_OFFSET = 3
    private const val LEFT_CHARGING_OFFSET = 4
    private const val RIGHT_CHARGING_OFFSET = 5
    private const val CASE_OFFSET = 6
    private const val NOISE_GROUP = 0x06
    private const val NOISE_COMMAND = 0x02
    private const val NOISE_PAYLOAD_SIZE = 4
    private val RESPONSE_MARKER = byteArrayOf(0x09, 0xFF.toByte())
}
