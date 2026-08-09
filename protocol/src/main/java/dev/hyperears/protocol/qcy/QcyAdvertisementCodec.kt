package dev.hyperears.protocol.qcy

/** Pure-byte parser for the public QCY manufacturer-data layout. */
object QcyAdvertisementCodec {
    data class Advertisement(
        val controlAddress: String?,
        val otherAddress: String?,
    )

    fun parse(manufacturerData: ByteArray): Advertisement? {
        if (manufacturerData.size < MIN_PAYLOAD_SIZE) return null
        return Advertisement(
            controlAddress = manufacturerData.parseAddress(CONTROL_ADDRESS_INDICES),
            otherAddress = manufacturerData.parseAddress(OTHER_ADDRESS_INDICES)
                ?.takeUnless { it == ZERO_ADDRESS },
        )
    }

    private fun ByteArray.parseAddress(indices: IntArray): String? {
        if (indices.any { it !in this.indices }) return null
        return indices.joinToString(":") { index ->
            "%02X".format(this[index].unsigned())
        }
    }

    private fun Byte.unsigned(): Int = toInt() and 0xFF

    private const val MIN_PAYLOAD_SIZE = 8
    private const val ZERO_ADDRESS = "00:00:00:00:00:00"
    private val CONTROL_ADDRESS_INDICES = intArrayOf(12, 11, 13, 16, 15, 14)
    private val OTHER_ADDRESS_INDICES = intArrayOf(19, 18, 20, 23, 22, 21)
}
