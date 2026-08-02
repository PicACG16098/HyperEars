package dev.hyperears.protocol.sony

/**
 * Sony Headphones Connect RFCOMM framing.
 *
 * This codec owns only stream framing, escaping, length validation and checksums. Product
 * capabilities and request sequencing belong to the integration-layer protocol session.
 */
object SonyHeadphonesWireCodec {
    const val RFCOMM_SERVICE_V1 = "96cc203e-5068-46ad-b32d-e316f5e069ba"
    const val RFCOMM_SERVICE_V2 = "956c7b26-d49a-4ba8-b03f-b17d393cb6e2"

    enum class MessageType(val code: Int) {
        ACK(0x01),
        COMMAND_1(0x0C),
        COMMAND_2(0x0E),
        ;

        companion object {
            fun fromCode(code: Int): MessageType? = entries.firstOrNull { it.code == code }
        }
    }

    data class Frame(
        val type: MessageType,
        val sequence: Int,
        val payload: ByteArray,
    )

    fun encode(
        type: MessageType,
        sequence: Int,
        payload: ByteArray = byteArrayOf(),
    ): ByteArray {
        require(sequence in 0..0xFF)
        val body = ByteArray(HEADER_SIZE + payload.size + CHECKSUM_SIZE)
        body[0] = type.code.toByte()
        body[1] = sequence.toByte()
        writeIntBigEndian(body, 2, payload.size)
        payload.copyInto(body, destinationOffset = HEADER_SIZE)
        body[body.lastIndex] = checksum(body, body.lastIndex).toByte()

        return buildList<Byte> {
            add(FRAME_HEADER.toByte())
            body.forEach { value ->
                val unsigned = value.toInt() and 0xFF
                if (unsigned == FRAME_HEADER || unsigned == FRAME_ESCAPE || unsigned == FRAME_TRAILER) {
                    add(FRAME_ESCAPE.toByte())
                    add((unsigned and ESCAPE_MASK).toByte())
                } else {
                    add(value)
                }
            }
            add(FRAME_TRAILER.toByte())
        }.toByteArray()
    }

    class Decoder {
        private val body = ArrayList<Byte>()
        private var collecting = false
        private var escaped = false

        fun offer(bytes: ByteArray): List<Frame> = buildList {
            bytes.forEach { value ->
                val unsigned = value.toInt() and 0xFF
                when {
                    !collecting && unsigned == FRAME_HEADER -> startFrame()
                    !collecting -> Unit
                    escaped -> {
                        body += (unsigned or ESCAPE_RESTORE_BIT).toByte()
                        escaped = false
                    }
                    unsigned == FRAME_ESCAPE -> escaped = true
                    unsigned == FRAME_HEADER -> startFrame()
                    unsigned == FRAME_TRAILER -> {
                        decodeBody()?.let(::add)
                        reset()
                    }
                    body.size >= MAX_UNESCAPED_FRAME_SIZE -> reset()
                    else -> body += value
                }
            }
        }

        fun reset() {
            body.clear()
            collecting = false
            escaped = false
        }

        private fun startFrame() {
            body.clear()
            collecting = true
            escaped = false
        }

        private fun decodeBody(): Frame? {
            if (escaped || body.size < HEADER_SIZE + CHECKSUM_SIZE) return null
            val raw = body.toByteArray()
            val payloadSize = readIntBigEndian(raw, 2)
            if (payloadSize < 0 || raw.size != HEADER_SIZE + payloadSize + CHECKSUM_SIZE) return null
            if (checksum(raw, raw.lastIndex) != (raw.last().toInt() and 0xFF)) return null
            val type = MessageType.fromCode(raw[0].toInt() and 0xFF) ?: return null
            return Frame(
                type = type,
                sequence = raw[1].toInt() and 0xFF,
                payload = raw.copyOfRange(HEADER_SIZE, HEADER_SIZE + payloadSize),
            )
        }
    }

    private fun checksum(bytes: ByteArray, endExclusive: Int): Int {
        var result = 0
        for (index in 0 until endExclusive) {
            result = (result + (bytes[index].toInt() and 0xFF)) and 0xFF
        }
        return result
    }

    private fun writeIntBigEndian(target: ByteArray, offset: Int, value: Int) {
        target[offset] = (value ushr 24).toByte()
        target[offset + 1] = (value ushr 16).toByte()
        target[offset + 2] = (value ushr 8).toByte()
        target[offset + 3] = value.toByte()
    }

    private fun readIntBigEndian(source: ByteArray, offset: Int): Int =
        ((source[offset].toInt() and 0xFF) shl 24) or
            ((source[offset + 1].toInt() and 0xFF) shl 16) or
            ((source[offset + 2].toInt() and 0xFF) shl 8) or
            (source[offset + 3].toInt() and 0xFF)

    private const val FRAME_HEADER = 0x3E
    private const val FRAME_ESCAPE = 0x3D
    private const val FRAME_TRAILER = 0x3C
    private const val ESCAPE_MASK = 0xEF
    private const val ESCAPE_RESTORE_BIT = 0x10
    private const val HEADER_SIZE = 6
    private const val CHECKSUM_SIZE = 1
    private const val MAX_UNESCAPED_FRAME_SIZE = 16 * 1024
}
