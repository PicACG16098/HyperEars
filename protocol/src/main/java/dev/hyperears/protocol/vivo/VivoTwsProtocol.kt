package dev.hyperears.protocol.vivo

/**
 * vivo TWS uses a GAIA-shaped frame over Bluetooth Classic RFCOMM.
 *
 * The command IDs are stable across the captures currently available, while the
 * GAIA version and trailing command parameters vary between earbud generations.
 */
object VivoTwsProtocol {
    const val VIVO_VENDOR = 0x001B
    const val GAIA_VENDOR = 0x000A

    const val SET_NOISE_MODE = 0x0130
    const val QUERY_NOISE_MODE = 0x0230
    const val ACK_NOISE_MODE = 0x8130
    const val REPORT_NOISE_MODE = 0x8230
    const val QUERY_BATTERY = 0x0207
    const val REPORT_BATTERY = 0x8207
    const val HANDSHAKE = 0x0300
    const val HANDSHAKE_RESPONSE = 0x8300

    private const val PREAMBLE = 0xFF
    private const val FLAG_CHECKSUM = 0x01
    private const val FLAG_LENGTH_EXTENSION = 0x02
    private const val COMMAND_BYTES = 4

    enum class NoiseMode(val wireValue: Int, val label: String) {
        ANC(0, "降噪"),
        OFF(1, "关闭"),
        TRANSPARENCY(2, "通透");

        companion object {
            fun fromWire(value: Int): NoiseMode? = entries.firstOrNull { it.wireValue == value }
        }
    }

    /**
     * Immutable wire configuration selected by a concrete model adapter.
     *
     * Configurations describe byte-level differences only. They deliberately do not contain retail
     * name matching, capabilities or transport policy.
     */
    enum class WireConfig(
        val label: String,
        val note: String,
        internal val gaiaVersion: Int,
        internal val noiseQueryPayload: ByteArray,
        internal val noiseSetSuffix: ByteArray,
    ) {
        AIR3_PRO_CAPTURED(
            label = "Air3 Pro 抓包 v3",
            note = "当前项目实机确认：设置载荷 mode 04 00",
            gaiaVersion = 3,
            noiseQueryPayload = byteArrayOf(),
            noiseSetSuffix = byteArrayOf(4, 0),
        ),
        FAMILY_DEFAULT_V4(
            label = "vivo 家族默认 v4",
            note = "Star-ZER0 公共画像：具体型号未注明，作为家族默认兼容参数",
            gaiaVersion = 4,
            noiseQueryPayload = byteArrayOf(0),
            noiseSetSuffix = byteArrayOf(3, 1),
        ),
        TWS_3E_V3(
            label = "TWS 3e 参考 v3",
            note = "ScrewVivoTWS：设置载荷 mode 03",
            gaiaVersion = 3,
            noiseQueryPayload = byteArrayOf(),
            noiseSetSuffix = byteArrayOf(3),
        ),
    }

    data class Frame(
        val version: Int,
        val flags: Int,
        val vendor: Int,
        val command: Int,
        val payload: ByteArray,
        val raw: ByteArray,
    )

    data class NoiseState(
        val mode: NoiseMode,
        val noiseEffect: Int?,
        val transparencyEffect: Int?,
        val acknowledged: Boolean,
        val version: Int,
    )

    data class BatteryState(
        val leftPercent: Int?,
        val rightPercent: Int?,
        val casePercent: Int?,
        val leftCharging: Boolean,
        val rightCharging: Boolean,
        val caseCharging: Boolean,
        val version: Int,
    )

    data class HandshakeState(
        val accepted: Boolean,
        val payload: ByteArray,
        val version: Int,
    )

    fun handshake(): ByteArray = frame(
        version = 4,
        vendor = GAIA_VENDOR,
        command = HANDSHAKE,
    )

    fun queryNoiseMode(configuration: WireConfig): ByteArray = frame(
        version = configuration.gaiaVersion,
        vendor = VIVO_VENDOR,
        command = QUERY_NOISE_MODE,
        payload = configuration.noiseQueryPayload.copyOf(),
    )

    fun setNoiseMode(mode: NoiseMode, configuration: WireConfig): ByteArray = frame(
        version = configuration.gaiaVersion,
        vendor = VIVO_VENDOR,
        command = SET_NOISE_MODE,
        payload = byteArrayOf(mode.wireValue.toByte()) + configuration.noiseSetSuffix,
    )

    /**
     * Read-only command documented by the public handmade capture.
     * Its documented request uses GAIA version 4 even though the response uses v3.
     */
    fun queryBattery(): ByteArray = frame(
        version = 4,
        vendor = VIVO_VENDOR,
        command = QUERY_BATTERY,
    )

    fun parseNoiseState(frame: Frame): NoiseState? {
        if (frame.vendor != VIVO_VENDOR) return null
        if (frame.command != ACK_NOISE_MODE && frame.command != REPORT_NOISE_MODE) return null
        if (frame.payload.size < 2 || frame.payload[0].unsigned() != 0) return null
        val mode = NoiseMode.fromWire(frame.payload[1].unsigned()) ?: return null
        return NoiseState(
            mode = mode,
            noiseEffect = frame.payload.getOrNull(2)?.unsigned(),
            transparencyEffect = frame.payload.getOrNull(3)?.unsigned(),
            acknowledged = frame.command == ACK_NOISE_MODE,
            version = frame.version,
        )
    }

    fun parseBatteryState(frame: Frame): BatteryState? {
        if (frame.vendor != VIVO_VENDOR || frame.command != REPORT_BATTERY) return null
        if (frame.payload.size < 5 || frame.payload[0].unsigned() != 0) return null
        val charging = frame.payload[4].unsigned()
        return BatteryState(
            leftPercent = frame.payload[1].batteryPercent(),
            rightPercent = frame.payload[2].batteryPercent(),
            casePercent = frame.payload[3].batteryPercent(),
            leftCharging = charging and 0x01 != 0,
            rightCharging = charging and 0x02 != 0,
            caseCharging = charging and 0x04 != 0,
            version = frame.version,
        )
    }

    fun parseHandshakeState(frame: Frame): HandshakeState? {
        if (frame.vendor != GAIA_VENDOR || frame.command != HANDSHAKE_RESPONSE) return null
        return HandshakeState(
            accepted = frame.payload.firstOrNull()?.unsigned() == 0,
            payload = frame.payload,
            version = frame.version,
        )
    }

    fun frame(
        version: Int,
        vendor: Int,
        command: Int,
        payload: ByteArray = byteArrayOf(),
    ): ByteArray {
        require(version in 0..255)
        require(vendor in 0..0xFFFF)
        require(command in 0..0xFFFF)
        require(payload.size <= 254) { "Compact GAIA payload is limited to 254 bytes" }
        return ByteArray(4 + COMMAND_BYTES + payload.size).apply {
            this[0] = PREAMBLE.toByte()
            this[1] = version.toByte()
            this[2] = 0
            this[3] = payload.size.toByte()
            writeShort(vendor, 4)
            writeShort(command, 6)
            payload.copyInto(this, destinationOffset = 8)
        }
    }

    fun ByteArray.hex(separator: String = " "): String =
        joinToString(separator) { "%02X".format(it.unsigned()) }

    class Decoder(initialCapacity: Int = 256) {
        private var bytes = ByteArray(initialCapacity.coerceAtLeast(16))
        private var size = 0

        fun offer(chunk: ByteArray): List<Frame> {
            append(chunk)
            val frames = mutableListOf<Frame>()
            while (true) {
                discardNoise()
                if (size < 4) return frames

                val flags = peek(2)
                val extended = flags and FLAG_LENGTH_EXTENSION != 0
                val headerSize = if (extended) 5 else 4
                if (size < headerSize) return frames

                val payloadLength = if (extended) {
                    (peek(3) shl 8) or peek(4)
                } else {
                    peek(3)
                }
                val checksumBytes = if (flags and FLAG_CHECKSUM != 0) 1 else 0
                val totalLength = headerSize + COMMAND_BYTES + payloadLength + checksumBytes
                if (totalLength > MAX_FRAME_BYTES) {
                    discard(1)
                    continue
                }
                if (size < totalLength) return frames

                val raw = take(totalLength)
                if (checksumBytes == 1 && checksum(raw, raw.lastIndex) != raw.last()) continue
                val contentOffset = headerSize
                val payloadOffset = contentOffset + COMMAND_BYTES
                frames += Frame(
                    version = raw[1].unsigned(),
                    flags = raw[2].unsigned(),
                    vendor = raw.readShort(contentOffset),
                    command = raw.readShort(contentOffset + 2),
                    payload = raw.copyOfRange(payloadOffset, payloadOffset + payloadLength),
                    raw = raw,
                )
            }
        }

        fun reset() {
            size = 0
        }

        private fun append(chunk: ByteArray) {
            ensureCapacity(size + chunk.size)
            chunk.copyInto(bytes, destinationOffset = size)
            size += chunk.size
        }

        private fun discardNoise() {
            var count = 0
            while (count < size && peek(count) != PREAMBLE) count++
            if (count > 0) discard(count)
        }

        private fun peek(index: Int): Int = bytes[index].unsigned()

        private fun take(count: Int): ByteArray =
            bytes.copyOfRange(0, count).also { discard(count) }

        private fun discard(count: Int) {
            if (count >= size) {
                size = 0
                return
            }
            bytes.copyInto(bytes, destinationOffset = 0, startIndex = count, endIndex = size)
            size -= count
        }

        private fun ensureCapacity(required: Int) {
            if (required <= bytes.size) return
            var capacity = bytes.size
            while (capacity < required) capacity *= 2
            bytes = bytes.copyOf(capacity)
        }
    }

    private fun ByteArray.writeShort(value: Int, offset: Int) {
        this[offset] = (value ushr 8).toByte()
        this[offset + 1] = value.toByte()
    }

    private fun ByteArray.readShort(offset: Int): Int =
        (this[offset].unsigned() shl 8) or this[offset + 1].unsigned()

    private fun Byte.batteryPercent(): Int? = unsigned().takeIf { it in 0..100 }

    private fun Byte.unsigned(): Int = toInt() and 0xFF

    private fun checksum(bytes: ByteArray, count: Int): Byte {
        var value = 0
        repeat(count) { value = value xor bytes[it].unsigned() }
        return value.toByte()
    }

    private const val MAX_FRAME_BYTES = 65_544
}
