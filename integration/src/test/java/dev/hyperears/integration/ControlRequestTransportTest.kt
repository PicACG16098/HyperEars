package dev.hyperears.integration

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ControlRequestTransportTest {
    @Test
    fun standardRequestsRoundTripWithStableDiscriminators() {
        val requests = listOf(
            StandardControlRequest.Refresh,
            StandardControlRequest.SetNoiseMode(NoiseMode.ANC),
            StandardControlRequest.SetNoiseMode(NoiseMode.OFF),
            StandardControlRequest.SetNoiseMode(NoiseMode.TRANSPARENCY),
            StandardControlRequest.SetNoiseMode(NoiseMode.WIND),
        )

        requests.forEach { request ->
            assertEquals(request, ControlRequestTransport.decode(ControlRequestTransport.encode(request)))
        }

        val encoded = ControlRequestTransport.encode(
            StandardControlRequest.SetNoiseMode(NoiseMode.ANC),
        )
        assertTrue(encoded.contains("\"command\":\"standard.set_noise_mode\""))
        assertTrue(encoded.contains("\"mode\":\"anc\""))
    }

    @Test
    fun malformedUnknownAndOversizedEnvelopesAreRejected() {
        assertNull(
            ControlRequestTransport.decode(
                "{\"schemaVersion\":99,\"request\":{\"command\":\"standard.refresh\"}}",
            ),
        )
        assertNull(
            ControlRequestTransport.decode(
                "{\"schemaVersion\":1,\"request\":{\"command\":\"future.unknown\"}}",
            ),
        )
        assertNull(
            ControlRequestTransport.decode(
                "{\"schemaVersion\":1,\"request\":{\"command\":\"standard.refresh\",\"extra\":true}}",
            ),
        )
        assertNull(ControlRequestTransport.decode("not-json"))
        assertNull(ControlRequestTransport.decode("x".repeat(4 * 1024 + 1)))
    }

    @Test
    fun standardContractUsesEffectiveAdapterCapabilities() {
        val standard = StandardEarbudAdapter()
        val vivo = VivoTwsAir3ProAdapter()

        assertTrue(standard.supportsControl(StandardControlRequest.Refresh))
        assertFalse(
            standard.supportsControl(StandardControlRequest.SetNoiseMode(NoiseMode.ANC)),
        )
        assertTrue(vivo.supportsControl(StandardControlRequest.SetNoiseMode(NoiseMode.ANC)))
        assertFalse(vivo.supportsControl(StandardControlRequest.SetNoiseMode(NoiseMode.WIND)))
    }
}
