package dev.hyperears.hook

import dev.hyperears.integration.EarbudState
import dev.hyperears.integration.DeviceLifecycle
import dev.hyperears.integration.NoiseMode
import dev.hyperears.integration.PrivateTransportState
import dev.hyperears.integration.ProtocolHandshakeState
import dev.hyperears.integration.SystemProfileState
import dev.hyperears.integration.withNoiseMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class EdifierFourModeMiLinkCardAdapterTest {
    private val connected = EarbudState(
        lifecycle = DeviceLifecycle(
            SystemProfileState.CONNECTED,
            PrivateTransportState.NOT_REQUIRED,
            ProtocolHandshakeState.NOT_REQUIRED,
        ),
    )

    @Test
    fun eachPeerModeRequestsExactlyItself() {
        assertEquals(
            NoiseMode.WIND,
            EdifierFourModeControlPolicy.request(
                connected.withNoiseMode(NoiseMode.ANC),
                NoiseMode.WIND,
            ),
        )
        assertEquals(
            NoiseMode.ANC,
            EdifierFourModeControlPolicy.request(
                connected.withNoiseMode(NoiseMode.WIND),
                NoiseMode.ANC,
            ),
        )
        listOf(NoiseMode.ANC, NoiseMode.WIND, NoiseMode.TRANSPARENCY, NoiseMode.OFF)
            .zipWithNext()
            .forEach { (current, requested) ->
            assertEquals(
                requested,
                EdifierFourModeControlPolicy.request(
                    connected.withNoiseMode(current),
                    requested,
                ),
            )
        }
    }

    @Test
    fun selectedModeDoesNotDispatchAgain() {
        assertNull(
            EdifierFourModeControlPolicy.request(
                connected.withNoiseMode(NoiseMode.WIND),
                NoiseMode.WIND,
            ),
        )
    }

    @Test
    fun windItemRejectsRequestsWithoutALiveConnection() {
        assertNull(
            EdifierFourModeControlPolicy.request(
                connected.copy(lifecycle = DeviceLifecycle()).withNoiseMode(NoiseMode.ANC),
                NoiseMode.WIND,
            ),
        )
        assertNull(
            EdifierFourModeControlPolicy.request(
                connected.copy(lifecycle = DeviceLifecycle()).withNoiseMode(NoiseMode.WIND),
                NoiseMode.ANC,
            ),
        )
    }
}
