package dev.hyperears.hook

import dev.hyperears.integration.EarbudState
import dev.hyperears.integration.DeviceLifecycle
import dev.hyperears.integration.NoiseMode
import dev.hyperears.integration.PrivateTransportState
import dev.hyperears.integration.ProtocolHandshakeState
import dev.hyperears.integration.SystemProfileState
import dev.hyperears.integration.withNoiseMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StarRingUltraMiLinkCardAdapterTest {
    private val connected = EarbudState(
        lifecycle = DeviceLifecycle(
            SystemProfileState.CONNECTED,
            PrivateTransportState.NOT_REQUIRED,
            ProtocolHandshakeState.NOT_REQUIRED,
        ),
    )

    @Test
    fun windIsAnAncBranchSwitchRatherThanAFourthPeerButton() {
        val anc = StarRingWindControlPolicy.render(connected.withNoiseMode(NoiseMode.ANC))
        val wind = StarRingWindControlPolicy.render(connected.withNoiseMode(NoiseMode.WIND))

        assertTrue(anc.enabled)
        assertFalse(anc.checked)
        assertTrue(wind.enabled)
        assertTrue(wind.checked)
    }

    @Test
    fun toggleTransitionsOnlyBetweenAncAndWind() {
        val anc = connected.withNoiseMode(NoiseMode.ANC)
        val wind = connected.withNoiseMode(NoiseMode.WIND)

        assertEquals(NoiseMode.WIND, StarRingWindControlPolicy.request(anc, checked = true))
        assertEquals(NoiseMode.ANC, StarRingWindControlPolicy.request(wind, checked = false))
        assertNull(StarRingWindControlPolicy.request(anc, checked = false))
        assertNull(StarRingWindControlPolicy.request(wind, checked = true))
    }

    @Test
    fun switchIsDisabledOutsideAncBranchOrWithoutLiveSession() {
        listOf(NoiseMode.TRANSPARENCY, NoiseMode.OFF, null).forEach { mode ->
            val state = connected.withNoiseMode(mode)
            assertFalse(StarRingWindControlPolicy.render(state).enabled)
            assertNull(StarRingWindControlPolicy.request(state, checked = true))
        }

        val disconnected = connected.copy(lifecycle = DeviceLifecycle())
            .withNoiseMode(NoiseMode.ANC)
        assertFalse(StarRingWindControlPolicy.render(disconnected).enabled)
        assertNull(StarRingWindControlPolicy.request(disconnected, checked = true))
    }
}
