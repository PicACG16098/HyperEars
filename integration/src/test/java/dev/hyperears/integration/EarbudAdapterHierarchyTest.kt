package dev.hyperears.integration

import dev.hyperears.protocol.bose.BoseBmapWireCodec
import dev.hyperears.protocol.oppo.OppoWireCodec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EarbudAdapterHierarchyTest {
    @Test
    fun adaptersDeclareOnlyTheirPlatformNeutralPhysicalForm() {
        assertEquals(HeadsetFormFactor.TWS, StandardEarbudAdapter().formFactor)
        assertEquals(HeadsetFormFactor.TWS, VivoEarbudAdapter().formFactor)
        assertEquals(HeadsetFormFactor.TWS, VivoTwsAir3ProAdapter.formFactor)
        assertEquals(HeadsetFormFactor.TWS, StarRingEarbudAdapter().formFactor)
        assertEquals(HeadsetFormFactor.TWS, StarRingUltraAdapter.formFactor)
        assertEquals(HeadsetFormFactor.TWS, OppoEarbudAdapter().formFactor)
        assertEquals(HeadsetFormFactor.TWS, OppoEncoAir2ProAdapter.formFactor)
        assertEquals(HeadsetFormFactor.TWS, BoseEarbudAdapter().formFactor)
        assertEquals(HeadsetFormFactor.HEADPHONES, BoseHeadphonesAdapter().formFactor)
        assertEquals(
            HeadsetFormFactor.HEADPHONES,
            BoseQuietComfortHeadphonesAdapter.formFactor,
        )
    }

    @Test
    fun registryResolvesFromConcreteModelToFamilyAndStandardFallback() {
        assertEquals(
            VivoTwsAir3ProAdapter,
            EarbudAdapterRegistry.resolve(identity("vivo TWS Air3 Pro")),
        )
        assertEquals(
            VivoTwsAir3ProAdapter,
            EarbudAdapterRegistry.resolve(identity("VIVO-TWS Air3 Pro")),
        )
        assertEquals(
            VivoTws3eAdapter,
            EarbudAdapterRegistry.resolve(identity("vivo TWS 3e")),
        )
        assertTrue(
            EarbudAdapterRegistry.resolve(identity("vivo TWS Air2")) is VivoEarbudAdapter,
        )
        assertTrue(
            EarbudAdapterRegistry.resolve(identity("iQOO TWS Air Pro")) is
                VivoEarbudAdapter,
        )
        assertEquals(
            StarRingUltraAdapter,
            EarbudAdapterRegistry.resolve(identity("StarRing Ultra")),
        )
        assertTrue(
            EarbudAdapterRegistry.resolve(identity("StarRing Future")) is
                StarRingEarbudAdapter,
        )
        assertEquals(
            OppoEncoAir2ProAdapter,
            EarbudAdapterRegistry.resolve(
                identity("OPPO Enco Air2 Pro", standardHeadset = true),
            ),
        )
        assertEquals(
            OppoEncoFree4Adapter,
            EarbudAdapterRegistry.resolve(
                identity("OPPO Enco Free4", standardHeadset = true),
            ),
        )
        assertEquals(
            OppoEncoX3Adapter,
            EarbudAdapterRegistry.resolve(
                identity("OPPO Enco X3", standardHeadset = true),
            ),
        )
        assertEquals(
            OppoEncoAir5Adapter,
            EarbudAdapterRegistry.resolve(
                identity("OPPO Enco Air5", standardHeadset = true),
            ),
        )
        assertTrue(
            EarbudAdapterRegistry.resolve(
                identity("OPPO Enco Buds2", standardHeadset = true),
            ) is OppoEarbudAdapter,
        )
        assertTrue(
            EarbudAdapterRegistry.resolve(
                identity("Bose QuietComfort Headphones", standardHeadset = true),
            ) is BoseHeadphonesAdapter,
        )
        assertEquals(
            BoseQuietComfortHeadphonesAdapter,
            EarbudAdapterRegistry.byId(BoseQuietComfortHeadphonesAdapter.id),
        )
        assertTrue(
            EarbudAdapterRegistry.resolve(
                identity(
                    name = "电音耳罩",
                    standardHeadset = true,
                    deviceAddress = "BC:87:FA:00:00:01",
                    bluetoothDeviceClass =
                        BoseHeadphonesAdapter.BLUETOOTH_DEVICE_CLASS_HEADPHONES,
                ),
            ) is BoseHeadphonesAdapter,
        )
        assertTrue(
            EarbudAdapterRegistry.resolve(
                identity("LE-Headset", standardHeadset = true),
            ) is StandardEarbudAdapter,
        )
        assertNull(EarbudAdapterRegistry.resolve(identity("Living Room Speaker")))
        assertNull(EarbudAdapterRegistry.resolve(identity(null)))
        assertNull(
            EarbudAdapterRegistry.resolve(
                identity(
                    "REDMI Buds 6 Pro",
                    standardHeadset = true,
                    nativeSystemEarbud = true,
                ),
            ),
        )
    }

    @Test
    fun onlyConcreteModelsDeclareUniqueMiLinkCardPresentations() {
        val presentationIds = EarbudAdapterRegistry.adapters
            .mapNotNull(EarbudAdapter::miLinkCardPresentationId)

        assertEquals(presentationIds.size, presentationIds.distinct().size)
        assertEquals(
            setOf(
                StarRingUltraAdapter.PRESENTATION_ID,
                BoseQuietComfortHeadphonesAdapter.PRESENTATION_ID,
            ),
            presentationIds.toSet(),
        )
        assertNull(VivoEarbudAdapter().miLinkCardPresentationId)
        assertNull(StarRingEarbudAdapter().miLinkCardPresentationId)
        assertNull(OppoEarbudAdapter().miLinkCardPresentationId)
        assertNull(OppoEncoAir2ProAdapter.miLinkCardPresentationId)
        assertNull(BoseEarbudAdapter().miLinkCardPresentationId)
        assertNull(BoseHeadphonesAdapter().miLinkCardPresentationId)
        assertEquals(
            BoseQuietComfortHeadphonesAdapter.PRESENTATION_ID,
            BoseQuietComfortHeadphonesAdapter.miLinkCardPresentationId,
        )
        assertNull(StandardEarbudAdapter().miLinkCardPresentationId)
    }

    @Test
    fun vivoFamilyOwnsCommonProtocolWhileStandardRemainsIdentityOnly() {
        assertEquals(
            VivoTwsAir3ProAdapter,
            EarbudAdapterRegistry.forIntegration(identity("vivo TWS Air3 Pro")),
        )

        val vivoFallback =
            EarbudAdapterRegistry.forIntegration(identity("vivo TWS Air2"))
        val standardFallback = EarbudAdapterRegistry.forIntegration(
            identity("LE-Headset", standardHeadset = true),
        )

        assertTrue(vivoFallback is VivoEarbudAdapter)
        assertTrue(requireNotNull(vivoFallback).privateProtocolRequired)
        assertEquals(BatterySource.PRIVATE_PROTOCOL, vivoFallback.batterySource)
        assertTrue(vivoFallback.capabilities.battery)
        assertTrue(vivoFallback.capabilities.noiseControl)
        assertTrue(vivoFallback.capabilities.audioHandoff)
        assertTrue(vivoFallback.createProtocol() != null)

        assertTrue(standardFallback is StandardEarbudAdapter)
        assertFalse(requireNotNull(standardFallback).privateProtocolRequired)
        assertEquals(BatterySource.SYSTEM_AGGREGATE, standardFallback.batterySource)
        assertTrue(standardFallback.capabilities.battery)
        assertTrue(standardFallback.capabilities.audioHandoff)
        assertNull(standardFallback.createProtocol())

        assertNull(EarbudAdapterRegistry.forIntegration(identity("Living Room Speaker")))
    }

    @Test
    fun air3ProInheritsStandardAndVivoFamilyBehavior() {
        assertEquals(
            VivoEarbudAdapter::class.java,
            VivoTwsAir3ProAdapter.javaClass.superclass,
        )
        assertEquals(
            StandardEarbudAdapter::class.java,
            VivoEarbudAdapter::class.java.superclass,
        )

        val first = VivoTwsAir3ProAdapter.transports.first() as RfcommEndpointSpec.ServiceUuid
        assertEquals(1, VivoTwsAir3ProAdapter.transports.size)
        assertEquals(VivoEarbudAdapter.VIVO_GAIA_UUID, first.uuid)
        assertEquals(BatterySource.PRIVATE_PROTOCOL, VivoTwsAir3ProAdapter.batterySource)
        assertTrue(VivoTwsAir3ProAdapter.capabilities.battery)
        assertTrue(VivoTwsAir3ProAdapter.capabilities.noiseControl)
        assertTrue(VivoTwsAir3ProAdapter.capabilities.audioHandoff)
        assertFalse(VivoTwsAir3ProAdapter.capabilities.spatialAudio)
        assertFalse(VivoTwsAir3ProAdapter.capabilities.wearDetection)
        assertFalse(VivoTwsAir3ProAdapter.capabilities.windNoiseControl)
    }

    @Test
    fun tws3eSelectsItsOwnWireProfileAndChannelFallback() {
        assertEquals(
            VivoEarbudAdapter::class.java,
            VivoTws3eAdapter.javaClass.superclass,
        )
        assertTrue(VivoTws3eAdapter.privateProtocolRequired)
        assertEquals(BatterySource.PRIVATE_PROTOCOL, VivoTws3eAdapter.batterySource)
        assertEquals(
            setOf(NoiseMode.ANC, NoiseMode.OFF, NoiseMode.TRANSPARENCY),
            VivoTws3eAdapter.supportedNoiseModes,
        )
        assertEquals(
            listOf("vivo-gaia-0837", "rfcomm-13"),
            VivoTws3eAdapter.transports.map(EarbudTransportSpec::id),
        )

        val protocol = requireNotNull(VivoTws3eAdapter.createProtocol())
        assertEquals(
            listOf(
                "FF 04 00 00 00 0A 03 00",
                "FF 03 00 00 00 1B 02 30",
                "FF 04 00 00 00 1B 02 07",
            ),
            protocol.initialReadCommands().map { it.hex() },
        )
        assertEquals(
            "FF 03 00 02 00 1B 01 30 00 03",
            protocol
                .encode(ControlRequest.SetNoiseMode(NoiseMode.ANC))
                .single()
                .hex(),
        )
        assertEquals(
            listOf(EarbudEvent.NoiseModeChanged(NoiseMode.ANC, acknowledged = true)),
            protocol.offer(hex("FF 03 00 03 00 1B 81 30 00 00 03")),
        )
    }

    @Test
    fun vivoRetailCatalogUsesFamilyProtocolDefaults() {
        assertEquals(
            "vivo TWS Air2",
            VivoRetailModelCatalog.find("vivo TWS Air200")?.canonicalName,
        )
        assertEquals(
            "iQOO TWS Air Pro",
            VivoRetailModelCatalog.find("IQOO-TWS Air Pro")?.canonicalName,
        )
        assertTrue(VivoRetailModelCatalog.isFamilyName("vivo TWS 5e"))
        assertTrue(VivoRetailModelCatalog.isFamilyName("iQOO TWS Future"))
        assertFalse(VivoRetailModelCatalog.isFamilyName("vivo WATCH 5"))

        val air2 = requireNotNull(
            EarbudAdapterRegistry.forIntegration(identity("vivo TWS Air2")),
        )
        val iqoo = requireNotNull(
            EarbudAdapterRegistry.forIntegration(identity("iQOO TWS Air Pro")),
        )
        listOf(air2, iqoo).forEach { adapter ->
            assertTrue(adapter.privateProtocolRequired)
            assertEquals(BatterySource.PRIVATE_PROTOCOL, adapter.batterySource)
            assertTrue(adapter.capabilities.battery)
            assertTrue(adapter.capabilities.audioHandoff)
            assertTrue(adapter.capabilities.noiseControl)
            assertEquals(
                setOf(NoiseMode.ANC, NoiseMode.OFF, NoiseMode.TRANSPARENCY),
                adapter.supportedNoiseModes,
            )

            val protocol = requireNotNull(adapter.createProtocol())
            assertEquals(
                listOf(
                    "FF 04 00 00 00 0A 03 00",
                    "FF 04 00 01 00 1B 02 30 00",
                    "FF 04 00 00 00 1B 02 07",
                ),
                protocol.initialReadCommands().map { it.hex() },
            )
            assertEquals(
                "FF 04 00 03 00 1B 01 30 00 03 01",
                protocol
                    .encode(ControlRequest.SetNoiseMode(NoiseMode.ANC))
                    .single()
                    .hex(),
            )
        }
    }

    @Test
    fun starRingUltraOwnsOnlyItsVerifiedPrivateCapabilities() {
        assertEquals(
            StarRingEarbudAdapter::class.java,
            StarRingUltraAdapter.javaClass.superclass,
        )
        assertEquals(
            StandardEarbudAdapter::class.java,
            StarRingEarbudAdapter::class.java.superclass,
        )
        assertEquals(BatterySource.PRIVATE_PROTOCOL, StarRingUltraAdapter.batterySource)
        assertTrue(StarRingUltraAdapter.privateProtocolRequired)
        assertEquals(
            ControlConfirmationPolicy.DEVICE_REPORT,
            StarRingUltraAdapter.noiseControlConfirmation,
        )
        assertTrue(StarRingUltraAdapter.capabilities.battery)
        assertTrue(StarRingUltraAdapter.capabilities.noiseControl)
        assertTrue(StarRingUltraAdapter.capabilities.windNoiseControl)
        assertTrue(StarRingUltraAdapter.capabilities.audioHandoff)
        assertEquals(
            listOf(
                "starring-official-gatt",
                "rfcomm-28",
                "rfcomm-28-insecure",
                "spp-uuid",
                "rfcomm-5",
            ),
            StarRingUltraAdapter.transports.map(EarbudTransportSpec::id),
        )
        val gatt = StarRingUltraAdapter.transports.first() as GattTransportSpec
        assertEquals(
            "00007777-0000-1000-8000-00805F9B34FB",
            gatt.writeCharacteristicUuid,
        )
        assertEquals(
            "00008888-0000-1000-8000-00805F9B34FB",
            gatt.notifyCharacteristicUuid,
        )
        assertEquals(0xA102, gatt.writeInstanceId)
        assertEquals(0xA105, gatt.notifyInstanceId)

        val protocol = requireNotNull(StarRingUltraAdapter.createProtocol())
        val windCommands = protocol.encode(ControlRequest.SetNoiseMode(NoiseMode.WIND))
        assertEquals(1, windCommands.size)
        assertEquals(
            "08 EE 00 00 00 06 82 0E 00 00 00 01 00 8D",
            windCommands.single().hex(),
        )
        assertTrue(protocol.readback(ControlRequest.SetNoiseMode(NoiseMode.WIND)).isEmpty())
        assertEquals(
            EarbudEvent.NoiseModeChanged(NoiseMode.WIND, acknowledged = true),
            protocol.offer(
                hex("09 FF 00 00 01 06 02 0E 00 00 00 01 00 20"),
            ).single(),
        )
    }

    @Test
    fun oppoFamilyUsesOneWireCodecWithModelOwnedAncMapping() {
        assertEquals(
            StandardEarbudAdapter::class.java,
            OppoEarbudAdapter::class.java.superclass,
        )
        assertEquals(
            OppoEarbudAdapter::class.java,
            OppoEncoAir2ProAdapter.javaClass.superclass,
        )

        val generic = OppoEarbudAdapter()
        assertTrue(generic.privateProtocolRequired)
        assertEquals(BatterySource.PRIVATE_PROTOCOL, generic.batterySource)
        assertTrue(generic.capabilities.battery)
        assertTrue(generic.capabilities.noiseControl)
        assertTrue(generic.capabilities.audioHandoff)
        assertEquals(
            setOf(NoiseMode.ANC, NoiseMode.OFF, NoiseMode.TRANSPARENCY),
            generic.supportedNoiseModes,
        )
        assertEquals(
            listOf("oppo-private-rfcomm"),
            generic.transports.map(EarbudTransportSpec::id),
        )

        val standardProtocol = requireNotNull(generic.createProtocol())
        assertEquals(
            listOf(
                "AA 07 00 00 00 02 F0 00 00",
                "AA 07 00 00 06 01 F0 00 00",
                "AA 09 00 00 0C 01 F0 02 00 01 01",
            ),
            standardProtocol.initialReadCommands().map { it.hex() },
        )
        assertEquals(
            "AA 0A 00 00 04 04 F0 03 00 01 01 02",
            standardProtocol
                .encode(ControlRequest.SetNoiseMode(NoiseMode.ANC))
                .single()
                .hex(),
        )
        assertEquals(
            "AA 0A 00 00 04 04 F0 03 00 01 01 01",
            standardProtocol
                .encode(ControlRequest.SetNoiseMode(NoiseMode.OFF))
                .single()
                .hex(),
        )

        val compatibleProtocol = requireNotNull(OppoEncoAir2ProAdapter.createProtocol())
        assertEquals(
            "AA 0A 00 00 04 04 F0 03 00 01 01 01",
            compatibleProtocol
                .encode(ControlRequest.SetNoiseMode(NoiseMode.ANC))
                .single()
                .hex(),
        )
        assertEquals(
            "AA 0A 00 00 04 04 F0 03 00 01 01 02",
            compatibleProtocol
                .encode(ControlRequest.SetNoiseMode(NoiseMode.OFF))
                .single()
                .hex(),
        )
        assertEquals(
            listOf(
                EarbudEvent.Handshake(true),
                EarbudEvent.NoiseModeChanged(NoiseMode.ANC, acknowledged = true),
            ),
            compatibleProtocol.offer(
                OppoWireCodec.packet(
                    command = OppoWireCodec.ANC_RESPONSE,
                    payload = hex("01 01 01 00"),
                ),
            ),
        )
    }

    @Test
    fun oppoProtocolPublishesAuthoritativeBatteryAndNoiseReports() {
        val protocol = requireNotNull(OppoEarbudAdapter().createProtocol())
        val notificationSupport = OppoWireCodec.packet(
            command = OppoWireCodec.NOTIFICATION_SUPPORT_RESPONSE,
            payload = hex("00 04 01 02 03 F1"),
        )
        val battery = OppoWireCodec.packet(
            command = OppoWireCodec.BATTERY_RESPONSE,
            payload = hex("01 4B 02 CA 03 64"),
        )
        val anc = OppoWireCodec.packet(
            command = OppoWireCodec.ANC_RESPONSE,
            payload = hex("01 01 02 00"),
        )

        val handshake = protocol.offer(notificationSupport).single()
        assertEquals(EarbudEvent.Handshake(true), handshake)
        assertEquals(
            "AA 0B 00 00 05 02 F0 04 00 03 01 02 03",
            protocol.followUpCommands(handshake).single().hex(),
        )
        assertTrue(protocol.followUpCommands(handshake).isEmpty())
        assertEquals(
            listOf(
                EarbudEvent.BatteryChanged(
                    EarbudBattery(
                        left = BatteryReading(75, false),
                        right = BatteryReading(74, true),
                        case = BatteryReading(100, false),
                    ),
                ),
            ),
            protocol.offer(battery),
        )
        assertEquals(
            listOf(EarbudEvent.NoiseModeChanged(NoiseMode.ANC, acknowledged = true)),
            protocol.offer(anc),
        )
        assertEquals(
            "AA 09 00 00 0C 01 F0 02 00 01 01",
            protocol
                .readback(ControlRequest.SetNoiseMode(NoiseMode.TRANSPARENCY))
                .single()
                .hex(),
        )
    }

    @Test
    fun boseFamilyUsesBmapIdentityAndBatteryWithoutClaimingNoiseControls() {
        val adapter = EarbudAdapterRegistry.resolve(
            identity(
                name = "电音耳罩",
                standardHeadset = true,
                deviceAddress = "BC:87:FA:00:00:01",
                bluetoothDeviceClass =
                    BoseHeadphonesAdapter.BLUETOOTH_DEVICE_CLASS_HEADPHONES,
            ),
        )

        assertTrue(adapter is BoseHeadphonesAdapter)
        assertTrue(requireNotNull(adapter).privateProtocolRequired)
        assertEquals(BatterySource.PRIVATE_PROTOCOL, adapter.batterySource)
        assertEquals(HeadsetFormFactor.HEADPHONES, adapter.formFactor)
        assertTrue(adapter.capabilities.battery)
        assertTrue(adapter.capabilities.audioHandoff)
        assertFalse(adapter.capabilities.noiseControl)
        assertEquals(
            listOf("rfcomm-8", "spp-uuid", "bmap-uuid", "rfcomm-2"),
            adapter.transports.map(EarbudTransportSpec::id),
        )

        val protocol = requireNotNull(adapter.createProtocol())
        assertEquals(
            listOf(
                "00 03 01 00",
                "02 02 01 00",
            ),
            protocol.initialReadCommands().map { it.hex() },
        )
        val events = protocol.offer(
            hex("00 03 03 03 40 75 02 02 02 03 04 50 FF FF 00"),
        )
        assertEquals(
            listOf(
                EarbudEvent.ModelIdentified(BoseQuietComfortHeadphonesAdapter.id),
                EarbudEvent.Handshake(true),
                EarbudEvent.BatteryChanged(
                    EarbudBattery(
                        overall = BatteryReading(80, false),
                    ),
                ),
            ),
            events,
        )
        assertEquals(
            listOf("1F 06 05 00", "1F 03 01 00"),
            protocol.followUpCommands(events.first()).map { it.hex() },
        )
    }

    @Test
    fun quietComfortMapsVerifiedBmapModesToNativeHeadphoneControls() {
        val adapter = BoseQuietComfortHeadphonesAdapter
        val protocol = requireNotNull(adapter.createProtocol())

        assertTrue(adapter.capabilities.noiseControl)
        assertTrue(adapter.capabilities.windNoiseControl)
        assertEquals(
            setOf(NoiseMode.ANC, NoiseMode.TRANSPARENCY, NoiseMode.WIND),
            adapter.supportedNoiseModes,
        )
        assertEquals(
            BoseQuietComfortHeadphonesAdapter.PRESENTATION_ID,
            adapter.miLinkCardPresentationId,
        )
        assertEquals(adapter.id, adapter.bmapProfile.modelId)
        assertEquals(adapter.PRODUCT_ID, adapter.bmapProfile.productId)
        assertTrue(protocol.encode(ControlRequest.SetNoiseMode(NoiseMode.ANC)).isEmpty())
        assertTrue(
            protocol.readback(ControlRequest.SetNoiseMode(NoiseMode.ANC)).isEmpty(),
        )

        val identityEvents = protocol.offer(hex("00 03 03 03 40 75 02"))
        assertEquals(
            listOf(
                EarbudEvent.ModelIdentified(adapter.id),
                EarbudEvent.Handshake(true),
            ),
            identityEvents,
        )
        assertEquals(
            listOf("1F 06 05 00", "1F 03 01 00"),
            protocol.followUpCommands(identityEvents.first()).map { it.hex() },
        )
        assertEquals(
            "1F 03 05 02 00 00",
            protocol.encode(ControlRequest.SetNoiseMode(NoiseMode.ANC)).single().hex(),
        )
        assertEquals(
            "1F 03 05 02 01 00",
            protocol
                .encode(ControlRequest.SetNoiseMode(NoiseMode.TRANSPARENCY))
                .single()
                .hex(),
        )
        assertTrue(protocol.encode(ControlRequest.SetNoiseMode(NoiseMode.WIND)).isEmpty())

        protocol.offer(boseModeConfigStatus(0, "Quiet", rawCnc = 0, wind = false))
        protocol.offer(boseModeConfigStatus(1, "Aware", rawCnc = 10, wind = false))
        protocol.offer(boseModeConfigStatus(2, "Commute", rawCnc = 4, wind = true))
        protocol.offer(boseModeConfigStatus(3, "Music", rawCnc = 5, wind = false))
        assertEquals(
            "1F 03 05 02 02 00",
            protocol.encode(ControlRequest.SetNoiseMode(NoiseMode.WIND)).single().hex(),
        )
        assertTrue(protocol.encode(ControlRequest.SetNoiseMode(NoiseMode.OFF)).isEmpty())
        assertEquals(
            "1F 03 01 00",
            protocol
                .readback(ControlRequest.SetNoiseMode(NoiseMode.ANC))
                .single()
                .hex(),
        )
        assertEquals(
            EarbudEvent.NoiseModeChanged(NoiseMode.WIND, acknowledged = true),
            protocol.offer(hex("1F 03 03 01 02")).single(),
        )
    }

    @Test
    fun unknownBoseProductNeverUnlocksConcreteModeCommands() {
        val protocol = requireNotNull(BoseEarbudAdapter().createProtocol())
        val events = protocol.offer(hex("00 03 03 03 12 34 00"))

        assertEquals(listOf(EarbudEvent.Handshake(true)), events)
        assertTrue(events.flatMap(protocol::followUpCommands).isEmpty())
        assertTrue(protocol.encode(ControlRequest.SetNoiseMode(NoiseMode.ANC)).isEmpty())
        assertTrue(
            protocol.readback(ControlRequest.SetNoiseMode(NoiseMode.ANC)).isEmpty(),
        )
    }

    @Test
    fun systemAggregateBatteryUsesTheSameValueForBothBuds() {
        assertEquals(
            EarbudBattery(
                left = BatteryReading(67, false),
                right = BatteryReading(67, false),
                overall = BatteryReading(67, false),
            ),
            EarbudBattery.fromSystemAggregate(67),
        )
        assertEquals(EarbudBattery(), EarbudBattery.fromSystemAggregate(-1))
        assertEquals(EarbudBattery(), EarbudBattery.fromSystemAggregate(null))
    }

    @Test
    fun protocolMapsCapturedResponsesToDomainEvents() {
        val protocol = requireNotNull(VivoTwsAir3ProAdapter.createProtocol())
        val events = protocol.offer(
            hex(
                "FF 03 00 04 00 0A 83 00 00 03 03 01 " +
                    "FF 03 00 04 00 1B 82 30 00 02 04 00 " +
                    "FF 03 00 05 00 1B 82 07 00 53 52 5F 00",
            ),
        )

        assertEquals(EarbudEvent.Handshake(true), events[0])
        assertEquals(
            EarbudEvent.NoiseModeChanged(NoiseMode.TRANSPARENCY, acknowledged = false),
            events[1],
        )
        assertEquals(
            EarbudEvent.BatteryChanged(
                EarbudBattery(
                    BatteryReading(83, false),
                    BatteryReading(82, false),
                    BatteryReading(95, false),
                ),
            ),
            events[2],
        )
    }

    @Test
    fun air3ProProtocolUsesCapturedWriteShape() {
        val protocol = requireNotNull(VivoTwsAir3ProAdapter.createProtocol())

        val anc = protocol.encode(ControlRequest.SetNoiseMode(NoiseMode.ANC)).single()
        val off = protocol.encode(ControlRequest.SetNoiseMode(NoiseMode.OFF)).single()
        val transparency =
            protocol.encode(ControlRequest.SetNoiseMode(NoiseMode.TRANSPARENCY)).single()

        assertEquals("FF 03 00 03 00 1B 01 30 00 04 00", anc.hex())
        assertEquals("FF 03 00 03 00 1B 01 30 01 04 00", off.hex())
        assertEquals("FF 03 00 03 00 1B 01 30 02 04 00", transparency.hex())
    }

    private fun hex(value: String): ByteArray {
        val compact = value.filter { it.isDigit() || it.lowercaseChar() in 'a'..'f' }
        return ByteArray(compact.length / 2) { index ->
            compact.substring(index * 2, index * 2 + 2).toInt(16).toByte()
        }
    }

    private fun ByteArray.hex(): String =
        joinToString(" ") { "%02X".format(it.toInt() and 0xFF) }

    private fun boseModeConfigStatus(
        index: Int,
        name: String,
        rawCnc: Int,
        wind: Boolean,
    ): ByteArray {
        val payload = ByteArray(47).apply {
            this[0] = index.toByte()
            this[2] = index.toByte()
            name.toByteArray().copyInto(
                destination = this,
                destinationOffset = 6,
                endIndex = minOf(name.toByteArray().size, 32),
            )
            this[42] = rawCnc.toByte()
            this[46] = if (wind) 1.toByte() else 0.toByte()
        }
        return BoseBmapWireCodec.packet(
            functionBlock = 0x1F,
            function = 0x06,
            operator = BoseBmapWireCodec.Operator.STATUS,
            payload = payload,
        )
    }

    private fun identity(
        name: String?,
        standardHeadset: Boolean = false,
        nativeSystemEarbud: Boolean = false,
        deviceAddress: String? = null,
        bluetoothDeviceClass: Int? = null,
    ): EarbudIdentity = EarbudIdentity(
        deviceName = name,
        standardHeadset = standardHeadset,
        nativeSystemEarbud = nativeSystemEarbud,
        deviceAddress = deviceAddress,
        bluetoothDeviceClass = bluetoothDeviceClass,
    )
}
