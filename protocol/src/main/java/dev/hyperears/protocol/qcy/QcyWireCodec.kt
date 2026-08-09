package dev.hyperears.protocol.qcy

/**
 * Codec for QCY's public `0xFF` command stream.
 *
 * The codec deliberately contains no BLE or Android behavior. It accepts arbitrary byte chunks,
 * validates every nested command length, and exposes only the battery and three-state noise
 * controls consumed by HyperEars.
 */
object QcyWireCodec {
    data class Frame(
        val command: Int,
        val parameters: ByteArray,
    )

    data class BatteryCell(
        val percent: Int?,
        val charging: Boolean,
    )

    data class BatteryState(
        val left: BatteryCell,
        val right: BatteryCell,
        val case: BatteryCell,
    )

    enum class NoiseMode(val value: Int) {
        OFF(0x00),
        ANC(0x01),
        OUTDOOR(0x02),
        TRANSPARENCY(0x03),
    }

    val queryBattery: ByteArray = request(COMMAND_BATTERY)
    val queryNoiseMode: ByteArray = request(COMMAND_NOISE_MODE)

    fun setNoiseMode(mode: NoiseMode): ByteArray =
        packet(COMMAND_NOISE_MODE, byteArrayOf(mode.value.toByte()))

    fun parseBattery(frame: Frame): BatteryState? {
        if (frame.command != COMMAND_BATTERY || frame.parameters.size < BATTERY_COMPONENTS) {
            return null
        }
        val cells = frame.parameters
            .take(BATTERY_COMPONENTS)
            .map(::parseBatteryCell)
        if (cells.none { it.percent != null }) return null
        return BatteryState(
            left = cells[0],
            right = cells[1],
            case = cells[2],
        )
    }

    fun parseNoiseMode(frame: Frame): NoiseMode? {
        if (frame.command != COMMAND_NOISE_MODE || frame.parameters.size != 1) return null
        val value = frame.parameters.single().unsigned()
        return NoiseMode.entries.firstOrNull { it.value == value }
    }

    class Decoder {
        private var pending = ByteArray(0)

        fun offer(bytes: ByteArray): List<Frame> {
            if (bytes.isEmpty()) return emptyList()
            pending += bytes
            val result = mutableListOf<Frame>()

            while (pending.size >= HEADER_SIZE) {
                val marker = pending.indexOf(FRAME_MARKER)
                if (marker < 0) {
                    pending = ByteArray(0)
                    break
                }
                if (marker > 0) pending = pending.copyOfRange(marker, pending.size)
                if (pending.size < HEADER_SIZE) break

                val bodyLength = pending[BODY_LENGTH_OFFSET].unsigned()
                if (bodyLength < MIN_COMMAND_SIZE) {
                    pending = pending.copyOfRange(1, pending.size)
                    continue
                }
                val frameLength = HEADER_SIZE + bodyLength
                if (pending.size < frameLength) break

                val candidate = pending.copyOfRange(0, frameLength)
                pending = pending.copyOfRange(frameLength, pending.size)
                decodeCommands(candidate)?.let(result::addAll)
            }
            return result
        }

        fun reset() {
            pending = ByteArray(0)
        }

        private fun decodeCommands(packet: ByteArray): List<Frame>? {
            val bodyEnd = packet.size
            val frames = mutableListOf<Frame>()
            var index = HEADER_SIZE
            while (index < bodyEnd) {
                if (index + 1 >= bodyEnd) return null
                val command = packet[index].unsigned()
                val parameterLength = packet[index + 1].unsigned()
                val parameterStart = index + MIN_COMMAND_SIZE
                val parameterEnd = parameterStart + parameterLength
                if (parameterEnd > bodyEnd) return null
                frames += Frame(
                    command = command,
                    parameters = packet.copyOfRange(parameterStart, parameterEnd),
                )
                index = parameterEnd
            }
            return frames.takeIf { it.isNotEmpty() }
        }
    }

    private fun request(command: Int): ByteArray =
        packet(COMMAND_REQUEST_DATA, byteArrayOf(command.toByte()))

    private fun packet(command: Int, parameters: ByteArray): ByteArray {
        val bodyLength = MIN_COMMAND_SIZE + parameters.size
        require(bodyLength <= 0xFF)
        return byteArrayOf(
            FRAME_MARKER,
            bodyLength.toByte(),
            command.toByte(),
            parameters.size.toByte(),
        ) + parameters
    }

    private fun parseBatteryCell(raw: Byte): BatteryCell {
        val value = raw.unsigned()
        return BatteryCell(
            percent = (value and BATTERY_LEVEL_MASK).takeIf { it in 0..100 },
            charging = value and BATTERY_CHARGING_MASK != 0,
        )
    }

    private fun Byte.unsigned(): Int = toInt() and 0xFF

    private const val HEADER_SIZE = 2
    private const val BODY_LENGTH_OFFSET = 1
    private const val MIN_COMMAND_SIZE = 2
    private const val BATTERY_COMPONENTS = 3
    private const val BATTERY_LEVEL_MASK = 0x7F
    private const val BATTERY_CHARGING_MASK = 0x80
    private const val COMMAND_NOISE_MODE = 0x0C
    private const val COMMAND_BATTERY = 0x2F
    private const val COMMAND_REQUEST_DATA = 0xFE
    private const val FRAME_MARKER: Byte = 0xFF.toByte()
}
