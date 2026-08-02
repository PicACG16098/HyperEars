package dev.hyperears.protocol.rose

/** Checksum-framed RFCOMM codec used by ROSE BudsFeel MK2. */
object RoseBudsFeelMk2WireCodec {
    enum class NoiseMode(val value: Int) {
        ANC(1),
        OFF(2),
        TRANSPARENCY(3),
        WIND(4),
    }

    sealed interface State {
        data class Battery(
            val leftPercent: Int?,
            val rightPercent: Int?,
            val casePercent: Int?,
        ) : State

        data class Noise(val mode: NoiseMode) : State
    }

    fun queryStatus(sequence: Int): ByteArray =
        command(sequence, STATUS_COMMAND, STATUS_QUERY_PAYLOAD)

    fun setNoiseMode(sequence: Int, mode: NoiseMode): ByteArray =
        command(sequence, SET_COMMAND, byteArrayOf(NOISE_TYPE.toByte(), mode.value.toByte()))

    fun command(sequence: Int, command: Int, payload: ByteArray): ByteArray {
        val body = byteArrayOf(REQUEST_MARKER, sequence.toByte(), command.toByte()) + payload
        return body + byteArrayOf(body.checksum(), TERMINATOR)
    }

    class Decoder {
        private var pending = ByteArray(0)

        fun offer(bytes: ByteArray): List<State> {
            if (bytes.isEmpty()) return emptyList()
            pending += bytes
            val states = mutableListOf<State>()

            while (pending.size >= MIN_FRAME_SIZE) {
                val marker = pending.indexOf(RESPONSE_MARKER)
                if (marker < 0) {
                    pending = ByteArray(0)
                    break
                }
                if (marker > 0) pending = pending.copyOfRange(marker, pending.size)

                val end = pending.validFrameEnd()
                if (end == INCOMPLETE_FRAME) break
                if (end == INVALID_FRAME) {
                    pending = pending.copyOfRange(1, pending.size)
                    continue
                }

                val frame = pending.copyOfRange(0, end + 1)
                pending = pending.copyOfRange(end + 1, pending.size)
                states += parseFrame(frame)
            }
            return states
        }

        fun reset() {
            pending = ByteArray(0)
        }
    }

    private fun ByteArray.validFrameEnd(): Int {
        var sawTerminator = false
        for (index in MIN_FRAME_SIZE - 1 until size) {
            if (this[index] != TERMINATOR) continue
            sawTerminator = true
            val checksumIndex = index - 1
            val expected = copyOfRange(0, checksumIndex).checksum()
            if (this[checksumIndex] == expected) return index
        }
        return when {
            !sawTerminator && size < MAX_FRAME_SIZE -> INCOMPLETE_FRAME
            else -> INVALID_FRAME
        }
    }

    private fun parseFrame(frame: ByteArray): List<State> = when (frame[2].unsigned()) {
        STATUS_RESPONSE -> parseTlvBlock(frame, 3, frame.size - 2)
        UNSOLICITED_RESPONSE -> parseUnsolicited(frame)
        else -> emptyList()
    }

    private fun parseUnsolicited(frame: ByteArray): List<State> {
        if (frame.size < 7) return emptyList()
        return when (frame[3].unsigned()) {
            NOISE_TYPE -> frame[4].toNoiseMode()?.let { listOf(State.Noise(it)) }.orEmpty()
            else -> emptyList()
        }
    }

    private fun parseTlvBlock(data: ByteArray, start: Int, end: Int): List<State> {
        val result = mutableListOf<State>()
        var index = start
        while (index + 1 < end) {
            val length = data[index].unsigned()
            val entryEnd = index + length + 1
            if (length < 2 || entryEnd > end) {
                index += 1
                continue
            }

            when (data[index + 1].unsigned()) {
                NOISE_TYPE -> data[index + 2].toNoiseMode()?.let {
                    result += State.Noise(it)
                }

                BATTERY_TYPE -> if (length >= 4) {
                    result += State.Battery(
                        leftPercent = data[index + 2].batteryPercent(),
                        rightPercent = data[index + 3].batteryPercent(),
                        casePercent = data[index + 4].batteryPercent(),
                    )
                }
            }

            val nestedStart = index + 2
            if (entryEnd - nestedStart >= 3) {
                result += parseTlvBlock(data, nestedStart, entryEnd)
            }
            index = entryEnd
        }
        return result.distinct()
    }

    private fun Byte.toNoiseMode(): NoiseMode? =
        NoiseMode.entries.firstOrNull { it.value == unsigned() }

    private fun Byte.batteryPercent(): Int? = unsigned().takeIf { it in 0..100 }
    private fun ByteArray.checksum(): Byte = sumOf { it.unsigned() }.and(0xFF).toByte()
    private fun Byte.unsigned(): Int = toInt() and 0xFF

    private const val STATUS_COMMAND = 0x1E
    private const val SET_COMMAND = 0x02
    private const val STATUS_RESPONSE = 0x15
    private const val UNSOLICITED_RESPONSE = 0x02
    private const val NOISE_TYPE = 0x09
    private const val BATTERY_TYPE = 0x0C
    private const val MIN_FRAME_SIZE = 5
    private const val MAX_FRAME_SIZE = 512
    private const val INCOMPLETE_FRAME = -1
    private const val INVALID_FRAME = -2
    private const val REQUEST_MARKER: Byte = 0xFF.toByte()
    private const val RESPONSE_MARKER: Byte = 0xDD.toByte()
    private const val TERMINATOR: Byte = 0xAA.toByte()

    private val STATUS_QUERY_PAYLOAD = byteArrayOf(
        0xFA.toByte(), 0x01,
        0x07, 0x08, 0x09, 0x0C, 0x0D, 0x0E, 0x12,
        0x2A, 0x2B, 0x2C, 0x2D, 0x2E, 0x2F,
        0x31, 0x32, 0x33, 0x36, 0x37, 0x38, 0x39, 0x3A, 0x3B, 0x3C, 0x3D,
        0x3F, 0x45, 0x46, 0x49,
    )
}
