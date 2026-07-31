package dev.hyperears.hook

import dev.hyperears.integration.NoiseMode
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BoseQuietComfortMiLinkCardAdapterTest {
    @Test
    fun mapsOnlyDeviceConfirmedQuietComfortModesToNativeSelections() {
        assertTrue(
            BoseQuietComfortMiLinkCardAdapter.isTransparencySelected(
                NoiseMode.TRANSPARENCY,
            ),
        )
        assertFalse(
            BoseQuietComfortMiLinkCardAdapter.isNoiseCancellationSelected(
                NoiseMode.TRANSPARENCY,
            ),
        )

        assertTrue(
            BoseQuietComfortMiLinkCardAdapter.isNoiseCancellationSelected(
                NoiseMode.ANC,
            ),
        )
        assertFalse(
            BoseQuietComfortMiLinkCardAdapter.isTransparencySelected(NoiseMode.ANC),
        )

        listOf(null, NoiseMode.OFF, NoiseMode.WIND).forEach { unsupported ->
            assertFalse(
                BoseQuietComfortMiLinkCardAdapter.isTransparencySelected(unsupported),
            )
            assertFalse(
                BoseQuietComfortMiLinkCardAdapter.isNoiseCancellationSelected(unsupported),
            )
        }
    }
}
