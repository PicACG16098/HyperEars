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

class WindNoiseToggleMiLinkCardAdapterTest {
    private val connected = EarbudState(
        lifecycle = DeviceLifecycle(
            SystemProfileState.CONNECTED,
            PrivateTransportState.NOT_REQUIRED,
            ProtocolHandshakeState.NOT_REQUIRED,
        ),
    )

    @Test
    fun windIsAnAncBranchSwitchRatherThanAFourthPeerButton() {
        val anc = WindNoiseToggleControlPolicy.render(connected.withNoiseMode(NoiseMode.ANC))
        val wind = WindNoiseToggleControlPolicy.render(connected.withNoiseMode(NoiseMode.WIND))

        assertTrue(anc.enabled)
        assertFalse(anc.checked)
        assertTrue(wind.enabled)
        assertTrue(wind.checked)
    }

    @Test
    fun toggleTransitionsOnlyBetweenAncAndWind() {
        val anc = connected.withNoiseMode(NoiseMode.ANC)
        val wind = connected.withNoiseMode(NoiseMode.WIND)

        assertEquals(NoiseMode.WIND, WindNoiseToggleControlPolicy.request(anc, checked = true))
        assertEquals(NoiseMode.ANC, WindNoiseToggleControlPolicy.request(wind, checked = false))
        assertNull(WindNoiseToggleControlPolicy.request(anc, checked = false))
        assertNull(WindNoiseToggleControlPolicy.request(wind, checked = true))
    }

    @Test
    fun switchIsDisabledOutsideAncBranchOrWithoutLiveSession() {
        listOf(NoiseMode.TRANSPARENCY, NoiseMode.OFF, null).forEach { mode ->
            val state = connected.withNoiseMode(mode)
            assertFalse(WindNoiseToggleControlPolicy.render(state).enabled)
            assertNull(WindNoiseToggleControlPolicy.request(state, checked = true))
        }

        val disconnected = connected.copy(lifecycle = DeviceLifecycle())
            .withNoiseMode(NoiseMode.ANC)
        assertFalse(WindNoiseToggleControlPolicy.render(disconnected).enabled)
        assertNull(WindNoiseToggleControlPolicy.request(disconnected, checked = true))
    }
}
