package dev.hyperears.protocol.bose

/**
 * Bose Music Application Protocol (BMAP) byte codec.
 *
 * BMAP is carried directly by the device's private RFCOMM stream. A frame is
 * `[functionBlock, function, flags, payloadLength, payload...]`; the low nibble of [flags]
 * contains the operator.
 */
object BoseBmapWireCodec {
    enum class Operator(val value: Int) {
        SET(0),
        GET(1),
        SET_GET(2),
        STATUS(3),
        ERROR(4),
        START(5),
        RESULT(6),
        PROCESSING(7),
    }

    enum class BatteryComponent(val id: Int) {
        OVERALL(0),
        LEFT(1),
        RIGHT(2),
        CASE(3),
        SYSTEM(4),
        UNKNOWN(-1),
        ;

        companion object {
            fun fromId(id: Int): BatteryComponent =
                entries.firstOrNull { it.id == id } ?: UNKNOWN
        }
    }

    data class Frame(
        val functionBlock: Int,
        val function: Int,
        val flags: Int,
        val payload: ByteArray,
        val bytes: ByteArray,
    ) {
        val operator: Operator?
            get() = Operator.entries.firstOrNull { it.value == flags and OPERATOR_MASK }
    }

    data class ProductIdentity(
        val productId: Int,
        val variant: Int,
    )

    data class ComponentBattery(
        val component: BatteryComponent,
        val percent: Int?,
        val remainingPlayTimeMinutes: Int?,
    )

    data class BatteryState(
        val overallPercent: Int?,
        val leftPercent: Int?,
        val rightPercent: Int?,
        val casePercent: Int?,
        val components: List<ComponentBattery>,
        val rawPayload: ByteArray,
    )

    data class ModeConfig(
        val index: Int,
        val prompt: Int,
        val name: String,
        val rawCnc: Int,
        val autoCnc: Boolean,
        val spatial: Int,
        val wind: Boolean,
    )

    data class AnrState(
        val level: Int,
        val capabilities: Int?,
    )

    data class CncState(
        val steps: Int,
        val rawLevel: Int,
        val enabled: Boolean,
    ) {
        val maximumRawLevel: Int = (steps - 1).coerceAtLeast(0)
    }

    /** Product-specific offsets for AudioModes ModeConfig STATUS payloads. */
    data class ModeConfigLayout(
        val minimumPayloadSize: Int,
        val nameOffset: Int,
        val nameSize: Int,
        val cncOffset: Int,
        val autoCncOffset: Int,
        val spatialOffset: Int,
        val windOffset: Int,
    ) {
        init {
            require(minimumPayloadSize > 0)
            require(nameOffset >= 0 && nameSize > 0)
            require(
                listOf(cncOffset, autoCncOffset, spatialOffset, windOffset)
                    .all { it in 0 until minimumPayloadSize },
            )
        }
    }

    val queryFunctionBlockInfo: ByteArray =
        packet(PRODUCT_INFO_BLOCK, FUNCTION_BLOCK_INFO, Operator.GET)

    val queryProductIdentity: ByteArray =
        packet(PRODUCT_INFO_BLOCK, PRODUCT_ID_VARIANTS, Operator.GET)

    val queryBattery: ByteArray =
        packet(STATUS_BLOCK, BATTERY_LEVEL, Operator.GET)

    val queryCurrentMode: ByteArray =
        packet(AUDIO_MODES_BLOCK, CURRENT_MODE, Operator.GET)

    val queryModeConfigs: ByteArray =
        packet(AUDIO_MODES_BLOCK, MODE_CONFIG, Operator.START)

    val queryAnr: ByteArray =
        packet(SETTINGS_BLOCK, ANR, Operator.GET)

    val queryCnc: ByteArray =
        packet(SETTINGS_BLOCK, CNC, Operator.GET)

    fun setAnr(level: Int): ByteArray {
        require(level in 0..0xFF)
        return packet(
            SETTINGS_BLOCK,
            ANR,
            Operator.SET_GET,
            byteArrayOf(level.toByte()),
        )
    }

    fun setCnc(rawLevel: Int, enabled: Boolean): ByteArray {
        require(rawLevel in 0..0xFF)
        return packet(
            SETTINGS_BLOCK,
            CNC,
            Operator.SET_GET,
            byteArrayOf(rawLevel.toByte(), if (enabled) 1 else 0),
        )
    }

    fun switchMode(index: Int, voicePrompt: Boolean = false): ByteArray {
        require(index in 0..0xFF)
        return packet(
            AUDIO_MODES_BLOCK,
            CURRENT_MODE,
            Operator.START,
            byteArrayOf(index.toByte(), if (voicePrompt) 1 else 0),
        )
    }

    fun packet(
        functionBlock: Int,
        function: Int,
        operator: Operator,
        payload: ByteArray = byteArrayOf(),
    ): ByteArray {
        require(functionBlock in 0..0xFF)
        require(function in 0..0xFF)
        require(payload.size <= MAX_PAYLOAD_SIZE)
        return byteArrayOf(
            functionBlock.toByte(),
            function.toByte(),
            operator.value.toByte(),
            payload.size.toByte(),
        ) + payload
    }

    fun parseProductIdentity(frame: Frame): ProductIdentity? {
        if (!frame.isStatus(PRODUCT_INFO_BLOCK, PRODUCT_ID_VARIANTS)) return null
        if (frame.payload.size < PRODUCT_IDENTITY_SIZE) return null
        return ProductIdentity(
            productId = (frame.payload[0].unsigned() shl 8) or frame.payload[1].unsigned(),
            variant = frame.payload[2].unsigned(),
        )
    }

    fun isFunctionBlockInfo(frame: Frame): Boolean =
        frame.isStatus(PRODUCT_INFO_BLOCK, FUNCTION_BLOCK_INFO)

    /**
     * Parses the official repeating four-byte battery layout:
     * `[percent, remainingMinutesHi, remainingMinutesLo, componentId]`.
     */
    fun parseBatteryState(frame: Frame): BatteryState? {
        if (!frame.isStatus(STATUS_BLOCK, BATTERY_LEVEL)) return null
        if (frame.payload.isEmpty() || frame.payload.size % BATTERY_GROUP_SIZE != 0) return null

        val components = frame.payload.asList()
            .chunked(BATTERY_GROUP_SIZE)
            .map { group ->
                val minutes = (group[1].unsigned() shl 8) or group[2].unsigned()
                ComponentBattery(
                    component = BatteryComponent.fromId(group[3].unsigned()),
                    percent = group[0].unsigned().takeIf { it in 0..100 },
                    remainingPlayTimeMinutes = minutes.takeUnless { it == UNKNOWN_MINUTES },
                )
            }

        fun percentFor(vararg candidates: BatteryComponent): Int? =
            components.firstOrNull { it.component in candidates }?.percent

        return BatteryState(
            overallPercent = percentFor(BatteryComponent.OVERALL, BatteryComponent.SYSTEM),
            leftPercent = percentFor(BatteryComponent.LEFT),
            rightPercent = percentFor(BatteryComponent.RIGHT),
            casePercent = percentFor(BatteryComponent.CASE),
            components = components,
            rawPayload = frame.payload.copyOf(),
        )
    }

    fun parseCurrentMode(frame: Frame): Int? {
        if (!frame.isStatus(AUDIO_MODES_BLOCK, CURRENT_MODE)) return null
        return frame.payload.firstOrNull()?.unsigned()
    }

    /**
     * Parses the 47-byte `prince` ModeConfig status layout captured from the device.
     *
     * The corresponding write layout is shorter and deliberately remains outside this codec
     * until HyperEars exposes editable Bose mode parameters.
     */
    fun parseModeConfig(
        frame: Frame,
        layout: ModeConfigLayout = PRINCE_MODE_CONFIG_LAYOUT,
    ): ModeConfig? {
        if (!frame.isStatus(AUDIO_MODES_BLOCK, MODE_CONFIG)) return null
        if (frame.payload.size < layout.minimumPayloadSize) return null
        val nameEndExclusive = layout.nameOffset + layout.nameSize
        val nameEnd = (layout.nameOffset until nameEndExclusive)
            .firstOrNull { frame.payload[it] == 0.toByte() }
            ?: nameEndExclusive
        return ModeConfig(
            index = frame.payload[0].unsigned(),
            prompt = (frame.payload[1].unsigned() shl 8) or frame.payload[2].unsigned(),
            name = frame.payload
                .copyOfRange(layout.nameOffset, nameEnd)
                .toString(Charsets.UTF_8),
            rawCnc = frame.payload[layout.cncOffset].unsigned(),
            autoCnc = frame.payload[layout.autoCncOffset] != 0.toByte(),
            spatial = frame.payload[layout.spatialOffset].unsigned(),
            wind = frame.payload[layout.windOffset] != 0.toByte(),
        )
    }

    fun parseAnrState(frame: Frame): AnrState? {
        if (!frame.isStatus(SETTINGS_BLOCK, ANR)) return null
        val level = frame.payload.firstOrNull()?.unsigned() ?: return null
        return AnrState(
            level = level,
            capabilities = frame.payload.getOrNull(1)?.unsigned(),
        )
    }

    fun parseCncState(frame: Frame): CncState? {
        if (!frame.isStatus(SETTINGS_BLOCK, CNC)) return null
        if (frame.payload.size < CNC_STATUS_SIZE) return null
        return CncState(
            steps = frame.payload[0].unsigned(),
            rawLevel = frame.payload[1].unsigned(),
            enabled = frame.payload[2] != 0.toByte(),
        )
    }

    class Decoder {
        private var pending = ByteArray(0)

        fun offer(bytes: ByteArray): List<Frame> {
            if (bytes.isEmpty()) return emptyList()
            pending += bytes
            val frames = mutableListOf<Frame>()

            while (pending.size >= HEADER_SIZE) {
                val operator = pending[FLAGS_OFFSET].unsigned() and OPERATOR_MASK
                if (Operator.entries.none { it.value == operator }) {
                    pending = pending.copyOfRange(1, pending.size)
                    continue
                }

                val payloadSize = pending[LENGTH_OFFSET].unsigned()
                val frameSize = HEADER_SIZE + payloadSize
                if (pending.size < frameSize) break

                val candidate = pending.copyOfRange(0, frameSize)
                pending = pending.copyOfRange(frameSize, pending.size)
                frames += Frame(
                    functionBlock = candidate[0].unsigned(),
                    function = candidate[1].unsigned(),
                    flags = candidate[2].unsigned(),
                    payload = candidate.copyOfRange(HEADER_SIZE, candidate.size),
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

    private fun Frame.isStatus(expectedBlock: Int, expectedFunction: Int): Boolean =
        functionBlock == expectedBlock &&
            function == expectedFunction &&
            operator == Operator.STATUS

    private fun Byte.unsigned(): Int = toInt() and 0xFF

    private const val PRODUCT_INFO_BLOCK = 0x00
    private const val FUNCTION_BLOCK_INFO = 0x01
    private const val PRODUCT_ID_VARIANTS = 0x03
    private const val SETTINGS_BLOCK = 0x01
    private const val CNC = 0x05
    private const val ANR = 0x06
    private const val STATUS_BLOCK = 0x02
    private const val BATTERY_LEVEL = 0x02
    private const val AUDIO_MODES_BLOCK = 0x1F
    private const val CURRENT_MODE = 0x03
    private const val MODE_CONFIG = 0x06

    private const val HEADER_SIZE = 4
    private const val FLAGS_OFFSET = 2
    private const val LENGTH_OFFSET = 3
    private const val OPERATOR_MASK = 0x0F
    private const val MAX_PAYLOAD_SIZE = 0xFF
    private const val PRODUCT_IDENTITY_SIZE = 3
    private const val BATTERY_GROUP_SIZE = 4
    private const val UNKNOWN_MINUTES = 0xFFFF
    private const val CNC_STATUS_SIZE = 3

    val PRINCE_MODE_CONFIG_LAYOUT = ModeConfigLayout(
        minimumPayloadSize = 47,
        nameOffset = 6,
        nameSize = 32,
        cncOffset = 42,
        autoCncOffset = 43,
        spatialOffset = 44,
        windOffset = 46,
    )

    val ULTRA_2_MODE_CONFIG_LAYOUT = ModeConfigLayout(
        minimumPayloadSize = 48,
        nameOffset = 6,
        nameSize = 32,
        cncOffset = 42,
        autoCncOffset = 43,
        spatialOffset = 44,
        windOffset = 45,
    )
}
