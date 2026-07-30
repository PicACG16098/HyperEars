package dev.hyperears.protocol.vivo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class VivoFastPairAdvertisementParserTest {
    @Test
    fun parsesV2ByteModelFromNewUuidManufacturerData() {
        val bytes = ByteArray(40)
        bytes[0] = 39
        bytes[1] = 0xFF.toByte()
        bytes[2] = 0x37
        bytes[3] = 0x08
        bytes[4] = 0x08
        bytes[5] = 0x02
        bytes[17] = 72

        val identity = VivoFastPairAdvertisementParser.parse(bytes)

        requireNotNull(identity)
        assertEquals(VivoFastPairIdentity.Layout.V2, identity.layout)
        assertEquals(0x0837, identity.uuid)
        assertEquals(72, identity.modelId)
        assertEquals(VivoFastPairIdentity.ModelEncoding.BYTE, identity.modelEncoding)
    }

    @Test
    fun parsesAdvertiseLayoutAndExtendedModel() {
        val bytes = ByteArray(45)
        bytes[0] = 44
        bytes[1] = 0xFF.toByte()
        bytes[2] = 0x37
        bytes[3] = 0x08
        bytes[4] = 0x08
        bytes[5] = 0x02
        bytes[17] = 0xFF.toByte()
        bytes[20] = 0x34
        bytes[21] = 0x12
        bytes[38] = 0xFF.toByte()

        val identity = VivoFastPairAdvertisementParser.parse(bytes)

        requireNotNull(identity)
        assertEquals(VivoFastPairIdentity.Layout.ADVERTISE, identity.layout)
        assertEquals(0x1234, identity.modelId)
        assertEquals(
            VivoFastPairIdentity.ModelEncoding.EXTENDED_LITTLE_ENDIAN,
            identity.modelEncoding,
        )
    }

    @Test
    fun parsesV1FromLegacyUuidManufacturerData() {
        val bytes = ByteArray(24)
        bytes[0] = 23
        bytes[1] = 0xFF.toByte()
        bytes[2] = 0x86.toByte()
        bytes[3] = 0x84.toByte()
        bytes[4] = 0x08
        bytes[5] = 0x01
        bytes[17] = 60

        val identity = VivoFastPairAdvertisementParser.parse(bytes)

        requireNotNull(identity)
        assertEquals(VivoFastPairIdentity.Layout.V1, identity.layout)
        assertEquals(0x8486, identity.uuid)
        assertEquals(60, identity.modelId)
    }

    @Test
    fun parsesLegacyV0Layout() {
        val bytes = ByteArray(22)
        bytes[0] = 21
        bytes[1] = 0x03
        bytes[2] = 0x37
        bytes[3] = 0x08
        bytes[20] = 32

        val identity = VivoFastPairAdvertisementParser.parse(bytes)

        requireNotNull(identity)
        assertEquals(VivoFastPairIdentity.Layout.V0, identity.layout)
        assertEquals(32, identity.modelId)
    }

    @Test
    fun rejectsMarkerWithoutSupportedVivoLayout() {
        val bytes = byteArrayOf(
            8,
            0xFF.toByte(),
            0x37,
            0x08,
            0x07,
            0x02,
            0,
            0,
            0,
        )

        assertNull(VivoFastPairAdvertisementParser.parse(bytes))
    }

    @Test
    fun rejectsTruncatedExtendedModel() {
        val bytes = ByteArray(20)
        bytes[0] = 19
        bytes[1] = 0xFF.toByte()
        bytes[2] = 0x37
        bytes[3] = 0x08
        bytes[4] = 0x08
        bytes[5] = 0x02
        bytes[17] = 0xFF.toByte()

        assertNull(VivoFastPairAdvertisementParser.parse(bytes))
    }
}
