package dev.hyperears.protocol.moondrop

/**
 * MOONDROP Robin's Earphones command framing observed on the Classic SPP channel.
 *
 * The SPP UUID is intentionally not part of identity matching: it is the Bluetooth SIG
 * standard SPP service and is used only by the integration transport to open RFCOMM.
 */
object MoondropRobinWireCodec {
    enum class NoiseMode(val queryValue: Int, val writeValue: Int) {
        OFF(queryValue = 0x00, writeValue = 0x01),
        ANC(queryValue = 0x01, writeValue = 0x02),
        TRANSPARENCY(queryValue = 0x02, writeValue = 0x04),
    }

    data class Frame(
        val command: Int,
        val subcommand: Int,
        val opcode: Int,
        val parameters: ByteArray,
    )

    data class BatteryState(val leftPercent: Int, val rightPercent: Int)

    val handshake: ByteArray = frame(
        type = 0x01,
        command = 0x0A,
        subcommand = 0x03,
        opcode = 0x00,
        parameters = byteArrayOf(),
    )
    val queryNoiseMode: ByteArray = frame(
        command = 0x1D,
        subcommand = 0x10,
        opcode = 0x03,
        parameters = byteArrayOf(),
    )
    val queryBattery: ByteArray = frame(
        command = 0x1D,
        subcommand = 0x1A,
        opcode = 0x01,
        parameters = byteArrayOf(),
    )

    fun setNoiseMode(mode: NoiseMode): ByteArray =
        frame(
            command = 0x1D,
            subcommand = 0x10,
            opcode = 0x04,
            parameters = byteArrayOf(mode.writeValue.toByte()),
        )

    fun frame(
        type: Int = 0x04,
        command: Int,
        subcommand: Int,
        opcode: Int,
        parameters: ByteArray,
    ): ByteArray {
        require(
            type in 0..0xFF &&
                command in 0..0xFF &&
                subcommand in 0..0xFF &&
                opcode in 0..0xFF,
        )
        require(parameters.size <= MAX_PAYLOAD_SIZE)
        val length = parameters.size
        return byteArrayOf(
            MARKER,
            type.toByte(),
            0,
            length.toByte(),
            (length ushr 8).toByte(),
            command.toByte(),
            subcommand.toByte(),
            opcode.toByte(),
        ) + parameters
    }

    fun parseHandshake(frame: Frame): Boolean =
        frame.command == 0x0A &&
            frame.subcommand == 0x83 &&
            frame.opcode == 0x00 &&
            frame.parameters.contentEquals(byteArrayOf(0x00, 0x04, 0x03, 0x01))

    fun parseNoiseMode(frame: Frame): NoiseMode? {
        if (frame.command != 0x1D || frame.subcommand != 0x11 ||
            frame.opcode != 0x03 || frame.parameters.size != 4
        ) {
            return null
        }
        if (frame.parameters[1].unsigned() != 0x01 ||
            frame.parameters[2].unsigned() != 0x00 ||
            frame.parameters[3].unsigned() != 0x00
        ) return null
        val value = frame.parameters[0].unsigned()
        return NoiseMode.entries.firstOrNull { it.queryValue == value }
    }

    fun parseBattery(frame: Frame): BatteryState? {
        if (frame.command != 0x1D || frame.subcommand != 0x1B ||
            frame.opcode != 0x01 || frame.parameters.size != 4
        ) {
            return null
        }
        if (frame.parameters[0].unsigned() != 0x01 || frame.parameters[2].unsigned() != 0x02) {
            return null
        }
        val left = frame.parameters[1].unsigned()
        val right = frame.parameters[3].unsigned()
        if (left !in 0..100 || right !in 0..100) return null
        return BatteryState(left, right)
    }

    class Decoder {
        private var pending = ByteArray(0)

        fun offer(bytes: ByteArray): List<Frame> {
            if (bytes.isEmpty()) return emptyList()
            pending += bytes
            val result = mutableListOf<Frame>()
            while (pending.size >= MIN_FRAME_SIZE) {
                val marker = pending.indexOf(MARKER)
                if (marker < 0) {
                    pending = ByteArray(0)
                    break
                }
                if (marker > 0) pending = pending.copyOfRange(marker, pending.size)
                if (pending.size < MIN_FRAME_SIZE) break
                if (pending[1].unsigned() != RESPONSE_TYPE || pending[2].unsigned() != 0) {
                    pending = pending.copyOfRange(1, pending.size)
                    continue
                }
                val payloadLength = pending[3].unsigned() or (pending[4].unsigned() shl 8)
                val frameSize = HEADER_SIZE + payloadLength
                if (payloadLength > MAX_PAYLOAD_SIZE || frameSize > MAX_FRAME_SIZE) {
                    pending = pending.copyOfRange(1, pending.size)
                    continue
                }
                if (pending.size < frameSize) break
                result += Frame(
                    command = pending[5].unsigned(),
                    subcommand = pending[6].unsigned(),
                    opcode = pending[7].unsigned(),
                    parameters = pending.copyOfRange(HEADER_SIZE, frameSize),
                )
                pending = pending.copyOfRange(frameSize, pending.size)
            }
            return result
        }

        fun reset() {
            pending = ByteArray(0)
        }
    }

    private fun Byte.unsigned(): Int = toInt() and 0xFF

    private const val MARKER: Byte = 0xFF.toByte()
    private const val RESPONSE_TYPE = 0x04
    private const val HEADER_SIZE = 8
    private const val MIN_FRAME_SIZE = HEADER_SIZE
    private const val MAX_PAYLOAD_SIZE = 256
    private const val MAX_FRAME_SIZE = HEADER_SIZE + MAX_PAYLOAD_SIZE
}
