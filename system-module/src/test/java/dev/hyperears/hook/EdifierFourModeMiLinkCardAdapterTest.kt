package dev.hyperears.hook

import dev.hyperears.integration.EarbudState
import dev.hyperears.integration.DeviceLifecycle
import dev.hyperears.integration.NoiseMode
import dev.hyperears.integration.PrivateTransportState
import dev.hyperears.integration.ProtocolHandshakeState
import dev.hyperears.integration.SystemProfileState
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
    fun windItemTogglesBetweenWindAndDeepAnc() {
        assertEquals(
            NoiseMode.WIND,
            EdifierFourModeControlPolicy.request(connected.copy(noiseMode = NoiseMode.ANC)),
        )
        assertEquals(
            NoiseMode.ANC,
            EdifierFourModeControlPolicy.request(connected.copy(noiseMode = NoiseMode.WIND)),
        )
    }

    @Test
    fun windItemCanBeEnteredFromEveryNativeMode() {
        listOf(NoiseMode.TRANSPARENCY, NoiseMode.OFF, null).forEach { mode ->
            assertEquals(
                NoiseMode.WIND,
                EdifierFourModeControlPolicy.request(connected.copy(noiseMode = mode)),
            )
        }
    }

    @Test
    fun windItemRejectsRequestsWithoutALiveConnection() {
        assertNull(
            EdifierFourModeControlPolicy.request(
                connected.copy(lifecycle = DeviceLifecycle(), noiseMode = NoiseMode.ANC),
            ),
        )
        assertNull(
            EdifierFourModeControlPolicy.request(
                connected.copy(lifecycle = DeviceLifecycle(), noiseMode = NoiseMode.WIND),
            ),
        )
    }
}
