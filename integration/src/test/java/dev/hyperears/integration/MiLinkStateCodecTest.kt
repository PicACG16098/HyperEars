package dev.hyperears.integration

import org.junit.Assert.assertEquals
import org.junit.Test

class MiLinkStateCodecTest {
    @Test
    fun nativeAncStateKeepsUnavailableDistinctFromOff() {
        assertEquals(
            MiLinkStateCodec.ANC_STATE_UNAVAILABLE,
            MiLinkStateCodec.ancState(EarbudState()),
        )
        assertEquals(
            0,
            MiLinkStateCodec.ancState(
                EarbudState().withFeature(NoiseModeFeatureState(NoiseMode.OFF)),
            ),
        )
    }

    @Test
    fun nativeAncStatePreservesSupportedModes() {
        val expected = mapOf(
            NoiseMode.ANC to 1,
            NoiseMode.WIND to 1,
            NoiseMode.TRANSPARENCY to 2,
            NoiseMode.OFF to 0,
        )

        expected.forEach { (mode, value) ->
            assertEquals(
                value,
                MiLinkStateCodec.ancState(
                    EarbudState().withFeature(NoiseModeFeatureState(mode)),
                ),
            )
        }
    }
}
