package dev.hyperears.protocol.vivo

/**
 * Identity fields carried by vivo's BLE fast-pair advertisement.
 *
 * The offsets and parser selection mirror the parser shipped in vivo's earbud application.
 * A hit identifies the vivo protocol family and exposes its numeric model id; mapping that id
 * to a retail name is deliberately kept separate.
 */
data class VivoFastPairIdentity(
    val uuid: Int,
    val modelId: Int,
    val layout: Layout,
    val advertisementType: Int,
    val bleType: Int?,
    val protocolVersion: Int?,
    val modelEncoding: ModelEncoding,
    val packetOffset: Int,
) {
    enum class Layout(val label: String) {
        V0("V0"),
        V1("V1"),
        V2("V2"),
        ADVERTISE("Advertise"),
    }

    enum class ModelEncoding {
        BYTE,
        EXTENDED_LITTLE_ENDIAN,
    }

    val uuidLabel: String
        get() = when (uuid) {
            VivoFastPairAdvertisementParser.UUID_NEW -> "0x0837（新标记）"
            VivoFastPairAdvertisementParser.UUID_LEGACY -> "0x8486（旧标记）"
            else -> "0x${uuid.toString(16).uppercase().padStart(4, '0')}"
        }
}

/**
 * Minimal catalog copied from the public constants present in vivo's official application.
 *
 * These are internal family/model constants, not inferred retail product names.
 */
object VivoEarbudModelCatalog {
    private val labels = mapOf(
        1 to "TWS1_BASE",
        2 to "TWS1_BLACK / TWS1_TOP",
        16 to "TWS_NEO_BASE",
        17 to "TWS_NEO_BLUE",
        19 to "TWS_NEO_TOP",
        28 to "TWS2_BASE",
        29 to "TWS2_BLUE",
        31 to "TWS2_TOP",
        32 to "TWS2E_BASE",
        33 to "TWS2E_BLUE",
        35 to "TWS2E_TOP",
        48 to "DPD2135A",
        49 to "DPD2135A_BLUE",
        60 to "TWS3_BASE",
        72 to "DPD2220_BASE",
        156 to "DPD2430_BASE",
        176 to "DPD2430F_VIVO_WHITE",
        177 to "DPD2430F_VIVO_BLUE",
        180 to "DPD2430F_IQOO_BLACK",
        184 to "DPD2430F_JOVI_WHITE",
        185 to "DPD2430F_JOVI_BLUE",
        192 to "DPD2523_BASE",
        203 to "DPD2523_TOP",
    )

    fun label(modelId: Int): String? = labels[modelId]
}

object VivoFastPairAdvertisementParser {
    const val UUID_NEW = 0x0837
    const val UUID_LEGACY = 0x8486

    private const val AD_TYPE_SERVICE_UUID_16 = 0x03
    private const val AD_TYPE_MANUFACTURER = 0xFF
    private const val TWS_BLE_TYPE = 0x08
    private const val VERSION_V1 = 0x01
    private const val VERSION_V2 = 0x02
    private const val EXTENDED_MODEL_MARKER = 0xFF

    /**
     * Returns null unless [bytes] contains a complete vivo fast-pair identity structure.
     */
    fun parse(bytes: ByteArray): VivoFastPairIdentity? {
        val markerIndex = findMarker(bytes) ?: return null
        val packetOffset = markerIndex - 1
        if (packetOffset < 0) return null

        val advertisementType = bytes.u8(packetOffset + 1) ?: return null
        val uuidLow = bytes.u8(packetOffset + 2) ?: return null
        val uuidHigh = bytes.u8(packetOffset + 3) ?: return null
        val uuid = uuidLow or (uuidHigh shl 8)

        return when (advertisementType) {
            AD_TYPE_SERVICE_UUID_16 -> parseV0(bytes, packetOffset, uuid)
            AD_TYPE_MANUFACTURER -> parseManufacturerLayout(bytes, packetOffset, uuid)
            else -> null
        }
    }

    private fun parseV0(
        bytes: ByteArray,
        packetOffset: Int,
        uuid: Int,
    ): VivoFastPairIdentity? {
        val advertisedLength = bytes.u8(packetOffset) ?: return null
        if (advertisedLength < 21 || packetOffset + 21 >= bytes.size) return null
        val modelId = bytes.u8(packetOffset + 20) ?: return null
        return VivoFastPairIdentity(
            uuid = uuid,
            modelId = modelId,
            layout = VivoFastPairIdentity.Layout.V0,
            advertisementType = AD_TYPE_SERVICE_UUID_16,
            bleType = null,
            protocolVersion = null,
            modelEncoding = VivoFastPairIdentity.ModelEncoding.BYTE,
            packetOffset = packetOffset,
        )
    }

    private fun parseManufacturerLayout(
        bytes: ByteArray,
        packetOffset: Int,
        uuid: Int,
    ): VivoFastPairIdentity? {
        val bleType = bytes.u8(packetOffset + 4) ?: return null
        val version = bytes.u8(packetOffset + 5) ?: return null
        if (bleType != TWS_BLE_TYPE || version !in VERSION_V1..VERSION_V2) return null

        val modelMarker = bytes.u8(packetOffset + 17) ?: return null
        val extended = version == VERSION_V2 && modelMarker == EXTENDED_MODEL_MARKER
        val modelId = if (extended) {
            val low = bytes.u8(packetOffset + 20) ?: return null
            val high = bytes.u8(packetOffset + 21) ?: return null
            low or (high shl 8)
        } else {
            modelMarker
        }
        val layout = when {
            version == VERSION_V1 -> VivoFastPairIdentity.Layout.V1
            bytes.u8(packetOffset + 38) == EXTENDED_MODEL_MARKER ->
                VivoFastPairIdentity.Layout.ADVERTISE
            else -> VivoFastPairIdentity.Layout.V2
        }

        return VivoFastPairIdentity(
            uuid = uuid,
            modelId = modelId,
            layout = layout,
            advertisementType = AD_TYPE_MANUFACTURER,
            bleType = bleType,
            protocolVersion = version,
            modelEncoding = if (extended) {
                VivoFastPairIdentity.ModelEncoding.EXTENDED_LITTLE_ENDIAN
            } else {
                VivoFastPairIdentity.ModelEncoding.BYTE
            },
            packetOffset = packetOffset,
        )
    }

    /**
     * vivo's parser checks new UUID before legacy UUID, and service UUID before manufacturer data.
     * Keeping the same order makes captures directly comparable with the official implementation.
     */
    private fun findMarker(bytes: ByteArray): Int? {
        val patterns = arrayOf(
            intArrayOf(AD_TYPE_SERVICE_UUID_16, 0x37, 0x08),
            intArrayOf(AD_TYPE_SERVICE_UUID_16, 0x86, 0x84),
            intArrayOf(AD_TYPE_MANUFACTURER, 0x37, 0x08),
            intArrayOf(AD_TYPE_MANUFACTURER, 0x86, 0x84),
        )
        for (pattern in patterns) {
            val index = bytes.indexOf(pattern)
            if (index >= 1) return index
        }
        return null
    }

    private fun ByteArray.indexOf(pattern: IntArray): Int {
        if (pattern.isEmpty() || size < pattern.size) return -1
        for (start in 0..size - pattern.size) {
            if (pattern.indices.all { offset -> this[start + offset].toInt() and 0xFF == pattern[offset] }) {
                return start
            }
        }
        return -1
    }

    private fun ByteArray.u8(index: Int): Int? =
        if (index in indices) this[index].toInt() and 0xFF else null
}
