package dev.hyperears.protocol.starring

/**
 * StarRing Ultra's checksum-framed business protocol.
 *
 * The official app carries these same bytes over BLE GATT; captured firmware also accepts them
 * over private RFCOMM transports. Link-layer ATT/RFCOMM headers are never part of these frames.
 */
object StarRingWireCodec {
    enum class NoiseMode(
        val bits: ByteArray,
        val label: String,
    ) {
        NORMAL(byteArrayOf(0, 1, 0, 0), "正常"),
        TRANSPARENCY(byteArrayOf(0, 0, 0, 1), "通透"),
        WIND(byteArrayOf(0, 0, 1, 0), "抗风噪"),
        ANC(byteArrayOf(1, 0, 0, 0), "降噪"),
    }

    data class Frame(
        val group: Int,
        val command: Int,
        val payload: ByteArray,
        val bytes: ByteArray,
    )

    data class BatteryState(
        val leftPercent: Int?,
        val rightPercent: Int?,
        val casePercent: Int?,
        val rawPayload: ByteArray,
    )

    data class NoiseState(
        val mode: NoiseMode,
        val rawPayload: ByteArray,
    )

    val queryBattery: ByteArray = hex("08 EE 00 00 00 01 01 0A 00 02")
    val queryNoiseMode: ByteArray = hex("08 EE 00 00 00 06 02 0A 00 08")

    fun setNoiseMode(mode: NoiseMode): ByteArray {
        val body = hex("08 EE 00 00 00 06 82 0E 00") + mode.bits
        return body + byteArrayOf(body.checksum())
    }

    fun parseBatteryState(frame: Frame): BatteryState? {
        if (frame.group != BATTERY_GROUP || frame.command != BATTERY_COMMAND) return null
        if (frame.payload.size < BATTERY_PAYLOAD_SIZE) return null
        return BatteryState(
            leftPercent = frame.payload[LEFT_OFFSET].toBatteryPercent(),
            rightPercent = frame.payload[RIGHT_OFFSET].toBatteryPercent(),
            casePercent = frame.payload[CASE_OFFSET].toBatteryPercent(),
            rawPayload = frame.payload.copyOf(),
        )
    }

    fun parseNoiseState(frame: Frame): NoiseState? {
        if (frame.group != NOISE_GROUP || frame.command != NOISE_COMMAND) return null
        if (frame.payload.size < NOISE_PAYLOAD_SIZE) return null
        val bits = frame.payload.copyOfRange(0, NOISE_PAYLOAD_SIZE)
        val mode = NoiseMode.entries.firstOrNull { it.bits.contentEquals(bits) } ?: return null
        return NoiseState(
            mode = mode,
            rawPayload = frame.payload.copyOf(),
        )
    }

    class Decoder {
        private var pending = ByteArray(0)

        fun offer(bytes: ByteArray): List<Frame> {
            if (bytes.isEmpty()) return emptyList()
            pending += bytes
            val frames = mutableListOf<Frame>()

            while (pending.size >= MIN_FRAME_SIZE) {
                val marker = pending.indexOfResponseMarker()
                if (marker < 0) {
                    pending = pending.takeLast(RESPONSE_MARKER.size - 1).toByteArray()
                    break
                }
                if (marker > 0) pending = pending.copyOfRange(marker, pending.size)
                if (pending.size < LENGTH_OFFSET + 1) break

                val frameSize = pending[LENGTH_OFFSET].unsigned()
                if (frameSize !in MIN_FRAME_SIZE..MAX_FRAME_SIZE) {
                    pending = pending.copyOfRange(1, pending.size)
                    continue
                }
                if (pending.size < frameSize) break

                val candidate = pending.copyOfRange(0, frameSize)
                pending = pending.copyOfRange(frameSize, pending.size)
                if (!candidate.hasValidChecksum()) continue

                frames += Frame(
                    group = candidate[GROUP_OFFSET].unsigned(),
                    command = candidate[COMMAND_OFFSET].unsigned(),
                    payload = candidate.copyOfRange(PAYLOAD_OFFSET, candidate.lastIndex),
                    bytes = candidate,
                )
            }
            return frames
        }

        fun reset() {
            pending = ByteArray(0)
        }
    }

    fun ByteArray.hex(): String =
        joinToString(" ") { "%02X".format(it.unsigned()) }

    internal fun hex(value: String): ByteArray {
        val compact = value.filter { it.isDigit() || it.lowercaseChar() in 'a'..'f' }
        require(compact.length % 2 == 0)
        return ByteArray(compact.length / 2) { index ->
            compact.substring(index * 2, index * 2 + 2).toInt(16).toByte()
        }
    }

    private fun ByteArray.indexOfResponseMarker(): Int {
        if (size < RESPONSE_MARKER.size) return -1
        for (offset in 0..size - RESPONSE_MARKER.size) {
            if (RESPONSE_MARKER.indices.all { this[offset + it] == RESPONSE_MARKER[it] }) {
                return offset
            }
        }
        return -1
    }

    private fun ByteArray.hasValidChecksum(): Boolean =
        isNotEmpty() &&
            dropLast(1).fold(0) { sum, byte -> (sum + byte.unsigned()) and 0xFF } ==
            last().unsigned()

    private fun ByteArray.checksum(): Byte =
        fold(0) { sum, byte -> (sum + byte.unsigned()) and 0xFF }.toByte()

    private fun Byte.toBatteryPercent(): Int? =
        unsigned().takeIf { it in 0..100 }

    private fun Byte.unsigned(): Int = toInt() and 0xFF

    private const val GROUP_OFFSET = 5
    private const val COMMAND_OFFSET = 6
    private const val LENGTH_OFFSET = 7
    private const val PAYLOAD_OFFSET = 9
    private const val MIN_FRAME_SIZE = 10
    private const val MAX_FRAME_SIZE = 255

    private const val BATTERY_GROUP = 0x01
    private const val BATTERY_COMMAND = 0x01
    private const val BATTERY_PAYLOAD_SIZE = 8
    private const val LEFT_OFFSET = 2
    private const val RIGHT_OFFSET = 3
    private const val CASE_OFFSET = 6
    private const val NOISE_GROUP = 0x06
    private const val NOISE_COMMAND = 0x02
    private const val NOISE_PAYLOAD_SIZE = 4

    private val RESPONSE_MARKER = byteArrayOf(0x09, 0xFF.toByte())
}
