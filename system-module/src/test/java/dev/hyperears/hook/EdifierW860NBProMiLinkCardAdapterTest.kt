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

class EdifierW860NBProMiLinkCardAdapterTest {
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
            EdifierWindControlPolicy.request(connected.copy(noiseMode = NoiseMode.ANC)),
        )
        assertEquals(
            NoiseMode.ANC,
            EdifierWindControlPolicy.request(connected.copy(noiseMode = NoiseMode.WIND)),
        )
    }

    @Test
    fun windItemCanBeEnteredFromEveryNativeMode() {
        listOf(NoiseMode.TRANSPARENCY, NoiseMode.OFF, null).forEach { mode ->
            assertEquals(
                NoiseMode.WIND,
                EdifierWindControlPolicy.request(connected.copy(noiseMode = mode)),
            )
        }
    }

    @Test
    fun windItemRejectsRequestsWithoutALiveConnection() {
        assertNull(
            EdifierWindControlPolicy.request(
                connected.copy(lifecycle = DeviceLifecycle(), noiseMode = NoiseMode.ANC),
            ),
        )
        assertNull(
            EdifierWindControlPolicy.request(
                connected.copy(lifecycle = DeviceLifecycle(), noiseMode = NoiseMode.WIND),
            ),
        )
    }
}
