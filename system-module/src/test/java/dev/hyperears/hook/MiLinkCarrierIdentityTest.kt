package dev.hyperears.hook

import dev.hyperears.integration.BoseQuietComfortHeadphonesAdapter
import dev.hyperears.integration.MiLinkCardPresentationId
import dev.hyperears.integration.StandardEarbudAdapter
import dev.hyperears.integration.StarRingUltraAdapter
import dev.hyperears.integration.VivoTwsAir3ProAdapter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MiLinkCarrierIdentityTest {
    @Test
    fun mapsAllAdaptersOntoOnlyTheTwoStockFormCarriers() {
        assertEquals(
            MiLinkCarrierIdentity.TWS_DEVICE_ID,
            MiLinkCarrierIdentity.deviceId(StandardEarbudAdapter().snapshot()),
        )
        assertEquals(
            MiLinkCarrierIdentity.TWS_DEVICE_ID,
            MiLinkCarrierIdentity.deviceId(VivoTwsAir3ProAdapter().snapshot()),
        )
        assertEquals(
            MiLinkCarrierIdentity.TWS_DEVICE_ID,
            MiLinkCarrierIdentity.deviceId(StarRingUltraAdapter().snapshot()),
        )
        assertEquals(
            MiLinkCarrierIdentity.HEADPHONES_DEVICE_ID,
            MiLinkCarrierIdentity.deviceId(BoseQuietComfortHeadphonesAdapter().snapshot()),
        )
    }

    @Test
    fun decodesOnlyVersionedNonBlankPresentationMetadata() {
        val id = MiLinkCardPresentationId("bose-qc-headphones")

        assertEquals(
            id,
            MiLinkPresentationContract.decode(
                MiLinkPresentationContract.SCHEMA_VERSION,
                id.value,
            ),
        )
        assertNull(
            MiLinkPresentationContract.decode(
                MiLinkPresentationContract.SCHEMA_VERSION + 1,
                id.value,
            ),
        )
        assertNull(
            MiLinkPresentationContract.decode(
                MiLinkPresentationContract.SCHEMA_VERSION,
                "",
            ),
        )
        assertNull(
            MiLinkPresentationContract.decode(
                MiLinkPresentationContract.SCHEMA_VERSION,
                null,
            ),
        )
    }
}
