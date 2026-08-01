package dev.hyperears.hook

import dev.hyperears.integration.BoseQuietComfortHeadphonesAdapter
import dev.hyperears.integration.EdifierW860NBProAdapter
import dev.hyperears.integration.MiLinkCardPresentationId
import dev.hyperears.integration.NoiseMode
import dev.hyperears.integration.StarRingUltraAdapter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

class MiLinkCardAdapterRegistryTest {
    @Test
    fun defaultExtendedModeRemainsInTheNativeAncBranch() {
        assertEquals(
            NoiseMode.ANC,
            StarRingUltraMiLinkCardAdapter.projectNativeNoiseMode(NoiseMode.WIND),
        )
    }

    @Test
    fun resolvesOnlyRegisteredConcreteModelPresentations() {
        assertSame(
            StarRingUltraMiLinkCardAdapter,
            MiLinkCardAdapterRegistry.resolve(StarRingUltraAdapter.PRESENTATION_ID),
        )
        assertSame(
            BoseQuietComfortMiLinkCardAdapter,
            MiLinkCardAdapterRegistry.resolve(
                BoseQuietComfortHeadphonesAdapter.PRESENTATION_ID,
            ),
        )
        assertSame(
            EdifierW860NBProMiLinkCardAdapter,
            MiLinkCardAdapterRegistry.resolve(
                EdifierW860NBProAdapter.PRESENTATION_ID,
            ),
        )
        assertNull(
            MiLinkCardAdapterRegistry.resolve(
                MiLinkCardPresentationId("unknown-model"),
            ),
        )
    }
}
