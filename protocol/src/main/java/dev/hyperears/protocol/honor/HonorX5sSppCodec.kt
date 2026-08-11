package dev.hyperears.protocol.honor

/**
 * Honor X5s Pro (BTV-ME10) SPP wire codec.
 *
 * The device speaks a `5A 00` framed protocol over RFCOMM SPP channel 1. Every frame starts with
 * the two-byte marker `5A 00`, followed by a length byte, then the payload. Known payloads:
 *
 * - Battery query (sent by the app): `01 08 01 00 02 00 03 00`
 * - Battery report (pushed as `01 27`, answered as `01 08`):
 *   `01 <27|08> 01 01 <case> 02 03 <left> <right> <case> 03 03 <left> <right> <charging> <crc>`
 *   A zero percent means that component is not connected; the trailing byte is 0x01 while the
 *   case is charging.
 * - Mode command: `2B 04 01 02 <mode> <depth> <crc>` (01=ANC, 02=transparency, 00=off)
 * - Mode state report: `2B 2A 01 02 <x> <y> <crc>`; with both earbuds worn x=0x01 marks ANC
 *   active with y carrying the depth; a single worn earbud changes the encoding: (0x01, 0x00)
 *   is off and any y=0x01 report is ANC.
 * - Heartbeat: `2B 79 01 00` (every ~3 s)
 *
 * Checksums are opaque and not validated; field values are treated as authoritative.
 */
object HonorX5sSppCodec {
    enum class NoiseMode {
        ANC,
        TRANSPARENCY,
        OFF,
    }

    /** ANC strength captured from the vendor app: smart / light / medium / deep (00 = deepest). */
    enum class AncDepth(val wire: Int) {
        DEEP(0x00),
        SMART(0x01),
        LIGHT(0x02),
        MEDIUM(0x03),
    }

    data class BatteryState(
        val leftPercent: Int?,
        val rightPercent: Int?,
        val casePercent: Int?,
        val caseCharging: Boolean = false,
    )

    /** Decoded mode-state frame content; depth is absent for transparency and off. */
    data class State(
        val mode: NoiseMode,
        val depth: AncDepth?,
    )

    /** Captured vendor battery query sent on the SPP channel. */
    val queryBattery: ByteArray = hex("5A 00 09 00 01 08 01 00 02 00 03 00 FB B9")

    /**
     * Captured vendor connect-init sequence (16:25 log, stable across captures). The vendor app
     * sends these once after SPP establishes; the state query (`2B 2A 01 00`) is what makes the
     * earphone reply with a state report immediately. Without it a single-eardrum connection
     * may wait ~14s for the pushed report, leaving noise-mode capabilities unconfirmed.
     */
    val initSequence: List<ByteArray> = listOf(
        hex("5A 00 23 00 01 07 01 00 02 00 03 00 04 00 05 00 06 00 07 00 08 00 09 00 0A 00 0B 00 0C 00 0F 00 11 00 19 00 22 00 A0 81"),
        hex("5A 00 05 00 2B 68 01 00 31 B3"),
        hex("5A 00 15 00 01 3A 01 10 02 0E 03 04 00 00 00 03 04 04 00 00 00 05 05 00 99 38"),
        hex("5A 00 05 00 2B 2A 01 00 42 7E"),
        hex("5A 00 06 00 01 02 01 01 2B 94 AB"),
        hex("5A 00 0D 00 01 05 01 04 6A 75 96 62 02 02 08 00 1F 09"),
    )

    /**
     * Streaming frame splitter for the `5A 00` protocol.
     *
     * RFCOMM delivers a continuous byte stream: one read may carry a partial frame, several
     * complete frames, or leading noise. The decoder buffers bytes, locates the marker, cuts
     * frames by their length field and performs bounded resync on malformed lengths.
     */
    class Decoder {
        private var pending = ByteArray(0)

        fun offer(bytes: ByteArray): List<ByteArray> {
            if (bytes.isEmpty()) return emptyList()
            pending += bytes
            val frames = mutableListOf<ByteArray>()
            while (pending.size >= MIN_FRAME_SIZE) {
                val marker = pending.indexOfMarker()
                if (marker < 0) {
                    pending = ByteArray(0)
                    break
                }
                if (marker > 0) pending = pending.copyOfRange(marker, pending.size)
                if (pending.size < MIN_FRAME_SIZE) break

                val length = pending[LENGTH_OFFSET].unsigned()
                // The length field counts the payload minus one trailing byte.
                val frameSize = HEADER_SIZE + length + 1
                if (length < MIN_PAYLOAD_LENGTH || frameSize > MAX_FRAME_SIZE) {
                    pending = pending.copyOfRange(1, pending.size)
                    continue
                }
                if (pending.size < frameSize) break

                frames += pending.copyOfRange(0, frameSize)
                pending = pending.copyOfRange(frameSize, pending.size)
            }
            return frames
        }

        private fun ByteArray.indexOfMarker(): Int {
            for (index in 0 until size - 1) {
                if (this[index] == MARKER_FIRST && this[index + 1] == MARKER_SECOND) return index
            }
            return -1
        }

        fun reset() {
            pending = ByteArray(0)
        }

        private companion object {
            const val MARKER_FIRST = 0x5A.toByte()
            const val MARKER_SECOND = 0x00.toByte()
            const val HEADER_SIZE = 4
            const val LENGTH_OFFSET = 2
            const val MIN_FRAME_SIZE = 8
            const val MIN_PAYLOAD_LENGTH = 4
            const val MAX_FRAME_SIZE = 128
        }
    }

    /**
     * Decodes an SPP battery report frame
     * `5A 00 10 00 01 27/08 01 01 <case> 02 03 <left> <right> <case> 03 03 <left> <right> 00 <crc>`.
     * The frame is both pushed by the earphone (command 0x27) and returned for the query (0x08);
     * the checksum is opaque and not validated.
     */
    fun parseBatteryFrame(bytes: ByteArray): BatteryState? {
        if (bytes.size != BATTERY_FRAME_SIZE) return null
        if (bytes[0] != 0x5A.toByte() || bytes[1] != 0x00.toByte()) return null
        if (bytes[4] != 0x01.toByte()) return null
        val command = bytes[5].unsigned()
        if (command != 0x27 && command != 0x08) return null
        if (bytes[6] != 0x01.toByte() || bytes[7] != 0x01.toByte()) return null
        if (bytes[9] != 0x02.toByte() || bytes[10] != 0x03.toByte()) return null
        // The case level lives in the second case field (byte 13). Byte 8 carries the cased
        // earbud's level (or mirrors the case while no earbud is cased); it is observation
        // only and never used as the case level (8/9 capture: byte 8 = right earbud 69% while
        // the case was 96%).
        val case = bytes[CASE_LEVEL_OFFSET].unsigned().percentOrNull()
        val left = bytes[11].unsigned().percentOrNull()
        val right = bytes[12].unsigned().percentOrNull()
        if (left == null && right == null && case == null) return null
        // Trailing flag byte is 0x01 while the case is charging and 0x00 otherwise.
        return BatteryState(left, right, case, caseCharging = bytes[CHARGING_FLAG_OFFSET] == 0x01.toByte())
    }

    /**
     * Captured vendor ANC command frame: `2B 04 01 02 <mode> <depth> <crc>`, where the
     * depth byte applies to ANC and stays 0x00 for transparency and off. Defaults to
     * [AncDepth.DEEP] (the "deep" level), matching the vendor app's default.
     */
    fun modeCommand(mode: NoiseMode, depth: AncDepth = AncDepth.DEEP): ByteArray = when (mode) {
        NoiseMode.ANC -> hex("5A 00 07 00 2B 04 01 02 01 ${depth.wire.toString(16).padStart(2, '0')}")
            .let { prefix -> prefix + requireNotNull(COMMAND_CRC[1 to depth]) }
        NoiseMode.TRANSPARENCY -> hex("5A 00 07 00 2B 04 01 02 02 00 B4 4F")
        NoiseMode.OFF -> hex("5A 00 07 00 2B 04 01 02 00 00 D2 2D")
    }

    /**
     * Decodes the earphone's state frame `2B 2A 01 02 <x> <y> <crc>`. With both earbuds worn,
     * x=0x01 marks ANC active with y carrying the depth; otherwise y carries the mode. A single
     * worn earbud changes the encoding: (0x01, 0x00) is off and any y=0x01 report is ANC, with
     * depth not reported. The trailing checksum is opaque and not validated.
     */
    fun stateFromFrame(bytes: ByteArray, singleEarbud: Boolean = false): State? {
        if (bytes.size != STATE_FRAME_SIZE) return null
        if (!bytes.copyOfRange(0, STATE_FRAME_PREFIX_SIZE).contentEquals(STATE_FRAME_PREFIX)) {
            return null
        }
        val x = bytes[STATE_FRAME_X_OFFSET].unsigned()
        val y = bytes[STATE_FRAME_Y_OFFSET].unsigned()
        if (singleEarbud) {
            return when {
                x == ANC_MARKER && y == 0x00 -> State(NoiseMode.OFF, null)
                y == 0x01 -> State(NoiseMode.ANC, null)
                y == 0x02 -> State(NoiseMode.TRANSPARENCY, null)
                x == 0x00 && y == 0x00 -> State(NoiseMode.OFF, null)
                else -> null
            }
        }
        if (x == ANC_MARKER) {
            // (0x01, 0x00) is off: captured with one earbud worn and with both earbuds in the case.
            if (y == 0x00) return State(NoiseMode.OFF, null)
            val depth = AncDepth.entries.firstOrNull { it.wire == y } ?: return null
            return State(NoiseMode.ANC, depth)
        }
        return when (y) {
            // Captured on connect-init and after a deep-level ANC command: the earphone reports
            // ANC at its deepest level as (0x00, 0x01), mirroring the wire order of ANC commands.
            0x01 -> State(NoiseMode.ANC, AncDepth.DEEP)
            0x02 -> State(NoiseMode.TRANSPARENCY, null)
            0x00 -> State(NoiseMode.OFF, null)
            else -> null
        }
    }

    /** 3-second vendor keepalive; must not be surfaced as an unknown frame. */
    fun isHeartbeat(bytes: ByteArray): Boolean = bytes.contentEquals(HEARTBEAT_FRAME)

    private fun Byte.unsigned(): Int = toInt() and 0xFF

    // Zero percent means the component is not connected.
    private fun Int.percentOrNull(): Int? = takeIf { it in 1..100 }

    private fun hex(value: String): ByteArray {
        val compact = value.filterNot(Char::isWhitespace)
        return ByteArray(compact.length / 2) { index ->
            compact.substring(index * 2, index * 2 + 2).toInt(16).toByte()
        }
    }

    private const val STATE_FRAME_SIZE = 12
    private const val STATE_FRAME_PREFIX_SIZE = 8
    private const val STATE_FRAME_X_OFFSET = 8
    private const val STATE_FRAME_Y_OFFSET = 9
    private const val ANC_MARKER = 0x01
    private const val BATTERY_FRAME_SIZE = 21
    private const val CASE_LEVEL_OFFSET = 13
    private const val CHARGING_FLAG_OFFSET = 18

    private val STATE_FRAME_PREFIX = hex("5A 00 07 00 2B 2A 01 02")
    private val HEARTBEAT_FRAME = hex("5A 00 05 00 2B 79 01 00 45 E0")

    private val COMMAND_CRC = mapOf(
        (1 to AncDepth.DEEP) to hex("E1 1C"),
        (1 to AncDepth.SMART) to hex("F1 3D"),
        (1 to AncDepth.LIGHT) to hex("C1 5E"),
        (1 to AncDepth.MEDIUM) to hex("D1 7F"),
    )
}
