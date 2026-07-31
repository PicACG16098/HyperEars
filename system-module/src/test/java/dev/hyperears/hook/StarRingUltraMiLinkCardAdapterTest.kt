package dev.hyperears.hook

import dev.hyperears.integration.NoiseMode
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StarRingUltraMiLinkCardAdapterTest {
    @Test
    fun windIsAnIndependentFourthModeRatherThanAncProjection() {
        assertTrue(
            StarRingUltraMiLinkCardAdapter.isModeSelected(
                NoiseMode.WIND,
                NoiseMode.WIND,
            ),
        )
        assertFalse(
            StarRingUltraMiLinkCardAdapter.isModeSelected(
                NoiseMode.ANC,
                NoiseMode.WIND,
            ),
        )
    }

    @Test
    fun eachNativeModeKeepsMutuallyExclusiveSelection() {
        listOf(
            NoiseMode.TRANSPARENCY,
            NoiseMode.ANC,
            NoiseMode.OFF,
            NoiseMode.WIND,
        ).forEach { current ->
            val selected = NoiseMode.entries.filter { candidate ->
                StarRingUltraMiLinkCardAdapter.isModeSelected(candidate, current)
            }
            assertTrue(selected == listOf(current))
        }
    }
}
