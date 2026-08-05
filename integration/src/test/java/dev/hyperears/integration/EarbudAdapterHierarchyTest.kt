package dev.hyperears.integration

import dev.hyperears.protocol.edifier.EdifierWireCodec
import dev.hyperears.protocol.oppo.OppoWireCodec
import dev.hyperears.protocol.vivo.VivoTwsProtocol
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class EarbudAdapterHierarchyTest {
    @Test
    fun registryCreatesOneIndependentAdapterAggregatePerPhysicalSession() {
        val identity = identity("vivo TWS Air3 Pro")
        val first = requireNotNull(EarbudAdapterRegistry.resolve(identity))
        val second = requireNotNull(EarbudAdapterRegistry.resolve(identity))

        assertTrue(first is VivoTwsAir3ProAdapter)
        assertTrue(second is VivoTwsAir3ProAdapter)
        assertNotSame(first, second)
        assertNotSame(first.protocolSession, second.protocolSession)
        assertEquals(AdapterResolution.EXACT_MATCH, first.snapshot().resolution)
    }

    @Test
    fun registryOrdersExactThenFamilyThenStandardAdapters() {
        assertTrue(resolve("vivo TWS Air3 Pro") is VivoTwsAir3ProAdapter)
        assertTrue(resolve("vivo TWS Air2") is VivoEarbudAdapter)
        assertTrue(resolve("StarRing Ultra") is StarRingUltraAdapter)
        assertTrue(resolve("StarRing Future") is StarRingEarbudAdapter)
        assertTrue(resolve("OPPO Enco Air2 Pro", standard = true) is OppoEncoAir2ProAdapter)
        assertTrue(resolve("OPPO Enco Buds2", standard = true) is OppoEarbudAdapter)
        assertTrue(resolve("漫步者・花再 Evo Pro", standard = true) is EdifierEvoProAdapter)
        assertTrue(resolve("Unknown headset", standard = true) is StandardEarbudAdapter)
    }

    @Test
    fun appleIdentityFallsBackToStandardAdapterWhenAppleIntegrationIsDisabled() {
        val adapter = requireNotNull(
            EarbudAdapterRegistry.resolve(
                EarbudIdentity(
                    deviceName = "AirPods Pro",
                    standardHeadset = true,
                    serviceUuids = setOf(AppleAirPodsAdapter.AAP_SERVICE_UUID),
                ),
            ),
        )

        assertTrue(adapter is StandardEarbudAdapter)
        assertEquals(StandardEarbudAdapter.ID, adapter.id)
    }

    @Test
    fun unconfirmedFamilyDoesNotPublishPrivateNoiseControls() {
        val vivo = VivoEarbudAdapter()
        val oppo = OppoEarbudAdapter()
        val bose = BoseEarbudAdapter()
        val edifier = EdifierEarbudAdapter()

        assertFalse(vivo.snapshot().capabilities.battery)
        assertFalse(vivo.snapshot().capabilities.noiseControl)
        assertTrue(vivo.snapshot().supportedNoiseModes.isEmpty())
        assertFalse(oppo.snapshot().capabilities.battery)
        assertFalse(oppo.snapshot().capabilities.noiseControl)
        assertTrue(oppo.snapshot().supportedNoiseModes.isEmpty())
        assertFalse(bose.snapshot().capabilities.battery)
        assertFalse(bose.snapshot().capabilities.noiseControl)
        assertFalse(edifier.snapshot().capabilities.battery)
        assertFalse(edifier.snapshot().capabilities.noiseControl)
    }

    @Test
    fun vivoFamilyPublishesControlsOnlyAfterProtocolEvidence() {
        val adapter = VivoEarbudAdapter()
        val handshake = VivoTwsProtocol.frame(
            version = 4,
            vendor = VivoTwsProtocol.GAIA_VENDOR,
            command = VivoTwsProtocol.HANDSHAKE_RESPONSE,
            payload = byteArrayOf(0),
        )

        val result = adapter.receive(handshake)

        assertEquals(HandshakeResult.Ready, result.handshake)
        assertTrue(result.stateChanged)
        assertTrue(adapter.snapshot().capabilities.noiseControl)
        assertEquals(
            setOf(NoiseMode.ANC, NoiseMode.OFF, NoiseMode.TRANSPARENCY),
            adapter.snapshot().supportedNoiseModes,
        )
    }

    @Test
    fun oppoFamilyPublishesControlsOnlyAfterAValidProtocolResponse() {
        val adapter = OppoEarbudAdapter()
        val notificationSupport = OppoWireCodec.packet(
            command = OppoWireCodec.NOTIFICATION_SUPPORT_RESPONSE,
            payload = hex("00 04 01 02 03 F1"),
        )

        val result = adapter.receive(notificationSupport)

        assertEquals(HandshakeResult.Ready, result.handshake)
        assertTrue(adapter.snapshot().capabilities.noiseControl)
        assertEquals(
            setOf(NoiseMode.ANC, NoiseMode.OFF, NoiseMode.TRANSPARENCY),
            adapter.snapshot().supportedNoiseModes,
        )
    }

    @Test
    fun oppoAncStateCanConfirmTheFamilyWithoutNotificationDiscovery() {
        val adapter = OppoEarbudAdapter()
        val anc = OppoWireCodec.packet(
            command = OppoWireCodec.ANC_RESPONSE,
            payload = hex("00 01 01 00 08"),
        )

        val result = adapter.receive(anc)

        assertEquals(HandshakeResult.Ready, result.handshake)
        assertTrue(adapter.snapshot().capabilities.noiseControl)
        assertEquals(NoiseMode.ANC, adapter.runtimeState().noiseMode)
    }

    @Test
    fun edifierValidAncStateConfirmsOnlyTheObservedProtocolCapabilities() {
        val adapter = EdifierEarbudAdapter()

        val result = adapter.receive(hex("BB EC CC 00 02 B5 A0 CA"))

        assertEquals(HandshakeResult.Ready, result.handshake)
        assertTrue(adapter.snapshot().capabilities.noiseControl)
        assertEquals(NoiseMode.OFF, adapter.runtimeState().noiseMode)
    }

    @Test
    fun edifierFunctionReplyConfirmsTransportWithoutInventingDeviceCapabilities() {
        val adapter = EdifierEarbudAdapter()

        val result = adapter.receive(hex("BB EC D8 00 00 7F"))

        assertEquals(HandshakeResult.Ready, result.handshake)
        assertFalse(adapter.snapshot().capabilities.battery)
        assertFalse(adapter.snapshot().capabilities.noiseControl)
    }

    @Test
    fun edifierFamilyUsesTheKnownDialectObservedDuringReadOnlyProbe() {
        val adapter = EdifierEarbudAdapter()
        adapter.receive(hex("BB EC CC 00 02 B5 A0 CA"))

        val result = adapter.executeControl(ControlRequest.SetNoiseMode(NoiseMode.ANC))

        assertTrue(result.accepted)
        assertEquals(
            EdifierWireCodec.setAnc(
                ancValue = EdifierWireCodec.ANC_VALUE_DEEP,
                ancIndex = 0x10,
            ).toList(),
            result.commands.single().toList(),
        )
    }

    @Test
    fun edifierEvoProUsesItsVerifiedDialectAndAggregateTwsBattery() {
        val adapter = EdifierEvoProAdapter()

        assertEquals(AdapterResolution.EXACT_MATCH, adapter.snapshot().resolution)
        assertEquals(HeadsetFormFactor.TWS, adapter.snapshot().formFactor)
        assertEquals(
            EdifierMiLinkPresentationIds.FOUR_MODE,
            adapter.snapshot().presentationId,
        )
        assertEquals(
            listOf(
                EdifierWireCodec.queryDeviceState.toList(),
                EdifierWireCodec.queryAnc.toList(),
                EdifierWireCodec.queryFunction.toList(),
            ),
            adapter.beginHandshake().commands.map(ByteArray::toList),
        )

        adapter.receive(hex("BB EC F2 00 06 A6 C1 C7 A5 A6 B4 CC"))
        val battery = adapter.runtimeState().battery
        assertEquals(3, battery.left.percent)
        assertEquals(3, battery.right.percent)
        assertEquals(3, battery.overall.percent)
        assertEquals(null, battery.case.percent)

        val state = adapter.receive(hex("BB EC CC 00 02 BE A3 D6"))
        assertTrue(state.stateChanged)
        assertEquals(NoiseMode.OFF, adapter.runtimeState().noiseMode)

        mapOf(
            NoiseMode.ANC to 1,
            NoiseMode.WIND to 4,
            NoiseMode.TRANSPARENCY to 5,
            NoiseMode.OFF to 6,
        ).forEach { (mode, value) ->
            val control = adapter.executeControl(ControlRequest.SetNoiseMode(mode))
            assertTrue(control.accepted)
            assertEquals(
                EdifierWireCodec.setAnc(value, ancIndex = 0x1B).toList(),
                control.commands.single().toList(),
            )
        }
    }

    @Test
    fun edifierFamilySelectsTheEvoDialectFromReadOnlyAncEvidence() {
        val adapter = EdifierEarbudAdapter()

        val evidence = adapter.receive(hex("BB EC CC 00 02 BE A3 D6"))
        assertEquals(HandshakeResult.Ready, evidence.handshake)
        assertEquals(NoiseMode.OFF, adapter.runtimeState().noiseMode)
        assertEquals(
            setOf(NoiseMode.ANC, NoiseMode.OFF, NoiseMode.TRANSPARENCY, NoiseMode.WIND),
            adapter.snapshot().supportedNoiseModes,
        )

        val control = adapter.executeControl(ControlRequest.SetNoiseMode(NoiseMode.WIND))
        assertEquals(
            EdifierWireCodec.setAnc(ancValue = 4, ancIndex = 0x1B).toList(),
            control.commands.single().toList(),
        )
    }

    @Test
    fun boseProductIdentityAtomicallyReplacesAdapterAndReusesProtocolSession() {
        val family = BoseHeadphonesAdapter()
        val protocolSession = family.protocolSession

        val result = family.receive(
            hex("00 03 03 03 40 75 02 02 02 03 04 50 FF FF 00"),
        )
        val replacement = result.handshake as HandshakeResult.Replace
        val adapter = replacement.adapter

        assertEquals(AdapterActivation.KEEP_CHANNEL_READY, replacement.activation)
        assertEquals(BoseQuietComfortHeadphonesAdapter.ID, adapter.id)
        assertEquals(AdapterResolution.PROTOCOL_CONFIRMED, adapter.resolution)
        assertEquals(HeadsetFormFactor.HEADPHONES, adapter.formFactor)
        assertSame(protocolSession, adapter.protocolSession)
        assertEquals(80, adapter.runtimeState().battery.overall.percent)
        assertTrue(adapter.snapshot().capabilities.noiseControl)
    }

    @Test
    fun unknownBoseIdentityKeepsFamilyUntilAReadOnlyDialectProbeSucceeds() {
        val family = BoseEarbudAdapter()
        val protocolSession = family.protocolSession

        val identity = family.receive(hex("00 03 03 03 12 34 00"))
        assertEquals(HandshakeResult.Ready, identity.handshake)
        assertFalse(family.snapshot().capabilities.noiseControl)

        val evidence = family.receive(hex("1F 03 03 01 01"))
        val replacement = evidence.handshake as HandshakeResult.Replace

        assertSame(protocolSession, replacement.adapter.protocolSession)
        assertEquals(AdapterResolution.PROTOCOL_CONFIRMED, replacement.adapter.resolution)
        assertEquals(NoiseMode.TRANSPARENCY, replacement.adapter.runtimeState().noiseMode)
        assertEquals(
            setOf(NoiseMode.ANC, NoiseMode.TRANSPARENCY),
            replacement.adapter.snapshot().supportedNoiseModes,
        )

        val repeatedEvidence = replacement.adapter.receive(hex("1F 03 03 01 01"))
        assertFalse(repeatedEvidence.handshake is HandshakeResult.Replace)
    }

    @Test
    fun edifierServiceEvidenceSelectsConservativeFamilyProbe() {
        val adapter = requireNotNull(EarbudAdapterRegistry.resolve(
            EarbudIdentity(
                deviceName = "Wireless Audio",
                standardHeadset = true,
                serviceUuids = setOf(EdifierEarbudAdapter.EDF_SPP_UUID.lowercase()),
            ),
        ))

        assertTrue(adapter is EdifierEarbudAdapter)
        assertFalse(adapter.snapshot().capabilities.battery)
        assertFalse(adapter.snapshot().capabilities.noiseControl)
    }

    @Test
    fun standardAdapterProjectsSystemBatteryWithoutInventingCaseTelemetry() {
        val adapter = StandardEarbudAdapter()

        assertTrue(adapter.onSystemBatteryChanged(73))
        assertEquals(73, adapter.runtimeState().battery.left.percent)
        assertEquals(73, adapter.runtimeState().battery.right.percent)
        assertEquals(null, adapter.runtimeState().battery.case.percent)
        assertFalse(adapter.privateProtocolRequired)
    }

    @Test
    fun roseProtocolFamilyConfirmsCapabilitiesInsideTheSameStatefulAdapter() {
        val adapter = TestRoseEarfreeProtocolFamilyAdapter()

        assertEquals(BatterySource.SYSTEM_AGGREGATE, adapter.snapshot().batterySource)
        assertFalse(adapter.snapshot().capabilities.noiseControl)

        val result = adapter.confirm(
            battery = true,
            noiseModes = setOf(
                NoiseMode.ANC,
                NoiseMode.OFF,
                NoiseMode.TRANSPARENCY,
                NoiseMode.WIND,
            ),
        )

        assertEquals(null, result)
        assertEquals(BatterySource.PRIVATE_PROTOCOL, adapter.snapshot().batterySource)
        assertTrue(adapter.snapshot().capabilities.noiseControl)
        assertTrue(adapter.snapshot().capabilities.windNoiseControl)
    }

    @Test
    fun boseProductCatalogNeverReturnsSharedAdapterInstances() {
        val first = BoseBmapModelRegistry.adapters.first()
        val second = BoseBmapModelRegistry.adapters.first()

        assertNotSame(first, second)
        assertNotSame(first.protocolSession, second.protocolSession)
    }

    private fun resolve(name: String, standard: Boolean = false): EarbudAdapter =
        requireNotNull(EarbudAdapterRegistry.resolve(identity(name, standard)))

    private fun identity(name: String, standard: Boolean = false): EarbudIdentity =
        EarbudIdentity(
            deviceName = name,
            standardHeadset = standard,
        )

    private fun hex(value: String): ByteArray = value
        .trim()
        .split(Regex("\\s+"))
        .map { it.toInt(16).toByte() }
        .toByteArray()

    private class TestRoseEarfreeProtocolFamilyAdapter :
        RoseEarfreeProtocolFamilyAdapter() {
        fun confirm(battery: Boolean, noiseModes: Set<NoiseMode>): HandshakeResult? =
            onCapabilitiesIdentified(battery, noiseModes)
    }
}
