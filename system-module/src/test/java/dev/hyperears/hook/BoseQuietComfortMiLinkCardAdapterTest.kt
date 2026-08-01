package dev.hyperears.hook

import dev.hyperears.integration.NoiseMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BoseQuietComfortMiLinkCardAdapterTest {
    @Test
    fun windOccupiesTheThirdNativeSlotInsteadOfTheAncSlot() {
        assertEquals(
            NoiseMode.OFF,
            BoseQuietComfortMiLinkCardAdapter.projectNativeNoiseMode(NoiseMode.WIND),
        )
        assertEquals(
            NoiseMode.ANC,
            BoseQuietComfortMiLinkCardAdapter.projectNativeNoiseMode(NoiseMode.ANC),
        )
    }

    @Test
    fun quietAwareAndWindKeepMutuallyExclusiveNativeSelections() {
        val presentedModes = listOf(
            NoiseMode.TRANSPARENCY,
            NoiseMode.ANC,
            NoiseMode.WIND,
        )
        presentedModes.forEach { current ->
            val selected = presentedModes.filter { candidate ->
                BoseQuietComfortMiLinkCardAdapter.isModeSelected(candidate, current)
            }
            assertTrue(selected == listOf(current))
        }
    }

    @Test
    fun unsupportedOffDoesNotSelectAnyPresentedMode() {
        listOf(null, NoiseMode.OFF).forEach { unsupported ->
            assertFalse(
                BoseQuietComfortMiLinkCardAdapter.isModeSelected(
                    NoiseMode.TRANSPARENCY,
                    unsupported,
                ),
            )
            assertFalse(
                BoseQuietComfortMiLinkCardAdapter.isModeSelected(
                    NoiseMode.ANC,
                    unsupported,
                ),
            )
            assertFalse(
                BoseQuietComfortMiLinkCardAdapter.isModeSelected(
                    NoiseMode.WIND,
                    unsupported,
                ),
            )
        }
    }
}
