package dev.hyperears.integration

import dev.hyperears.protocol.bose.BoseBmapWireCodec

/** One Bose BMAP codec session; transport and lifecycle remain owned by the system module. */
internal class BoseBmapEarbudProtocol(
    private val expectedProfile: BoseBmapProfile? = null,
    private val fallbackFormFactor: HeadsetFormFactor = HeadsetFormFactor.TWS,
) : EarbudProtocol {
    private val decoder = BoseBmapWireCodec.Decoder()
    private val modeConfigs = mutableMapOf<Int, BoseBmapWireCodec.ModeConfig>()
    private var identityAccepted: Boolean? = null
    private var activeProfile: BoseBmapProfile? = null
    private var pendingBattery: EarbudEvent.BatteryChanged? = null
    private var currentModeIndex: Int? = null
    private var currentCncEnabled: Boolean? = null

    override fun initialReadCommands(): List<ByteArray> = listOf(
        // QC35/35 II require this harmless BMAP initialization read before other requests.
        BoseBmapWireCodec.queryFunctionBlockInfo,
        BoseBmapWireCodec.queryProductIdentity,
        BoseBmapWireCodec.queryBattery,
    )

    override fun encode(request: ControlRequest): List<ByteArray> = when (request) {
        ControlRequest.Refresh -> initialReadCommands() + activeProfile.noiseReadCommands()
        is ControlRequest.SetNoiseMode -> activeProfile
            ?.noiseControl
            ?.encode(request.mode)
            .orEmpty()
    }

    override fun followUpCommands(event: EarbudEvent): List<ByteArray> = when {
        event is EarbudEvent.ModelIdentified &&
            event.modelId == activeProfile?.modelId &&
            activeProfile?.productId != null -> {
            activeProfile.noiseReadCommands().ifEmpty(::capabilityProbeCommands)
        }

        event is EarbudEvent.Handshake &&
            event.accepted &&
            activeProfile == null -> capabilityProbeCommands()

        else -> emptyList()
    }

    override fun readback(request: ControlRequest): List<ByteArray> = when (request) {
        ControlRequest.Refresh -> emptyList()
        is ControlRequest.SetNoiseMode -> activeProfile.noiseStateReadCommands()
    }

    override fun offer(bytes: ByteArray): List<EarbudEvent> = buildList {
        decoder.offer(bytes).forEach { frame ->
            if (BoseBmapWireCodec.isFunctionBlockInfo(frame)) return@forEach

            BoseBmapWireCodec.parseProductIdentity(frame)?.let { identity ->
                identityAccepted = expectedProfile?.productId == null ||
                    identity.productId == expectedProfile.productId
                activeProfile = BoseBmapModelRegistry
                    .find(identity.productId)
                    ?.takeIf { identityAccepted == true }
                activeProfile?.let { profile ->
                    add(EarbudEvent.ModelIdentified(profile.modelId))
                }
                add(EarbudEvent.Handshake(identityAccepted == true))
                if (identityAccepted == true) pendingBattery?.let(::add)
                pendingBattery = null
                return@forEach
            }

            BoseBmapWireCodec.parseBatteryState(frame)?.let { battery ->
                val event = EarbudEvent.BatteryChanged(
                    EarbudBattery(
                        left = BatteryReading(battery.leftPercent, charging = false),
                        right = BatteryReading(battery.rightPercent, charging = false),
                        case = BatteryReading(battery.casePercent, charging = false),
                        overall = BatteryReading(battery.overallPercent, charging = false),
                    ),
                )
                when {
                    expectedProfile == null || identityAccepted == true -> add(event)
                    identityAccepted == null -> pendingBattery = event
                }
                return@forEach
            }

            if (identityAccepted == true && activeProfile?.noiseControl == null) {
                discoverNoiseProfile(frame)?.let { profile ->
                    activeProfile = profile
                    add(EarbudEvent.ModelIdentified(profile.modelId))
                }
            }

            val noiseControl = activeProfile?.noiseControl
            when (noiseControl) {
                is BoseNoiseControlProfile.AudioModes -> {
                    noiseControl.modeConfigLayout?.let { layout ->
                        BoseBmapWireCodec.parseModeConfig(frame, layout)
                    }?.let { config ->
                        modeConfigs[config.index] = config
                        currentModeIndex
                            ?.takeIf { it == config.index }
                            ?.toNoiseMode(noiseControl)
                            ?.let { add(EarbudEvent.NoiseModeChanged(it, acknowledged = true)) }
                        return@forEach
                    }

                    BoseBmapWireCodec.parseCurrentMode(frame)?.let { modeIndex ->
                        currentModeIndex = modeIndex
                        modeIndex.toNoiseMode(noiseControl)?.let { mode ->
                            add(EarbudEvent.NoiseModeChanged(mode, acknowledged = true))
                        }
                        return@forEach
                    }
                }

                is BoseNoiseControlProfile.Anr -> {
                    BoseBmapWireCodec.parseAnrState(frame)?.let { state ->
                        state.level.toNoiseMode(noiseControl)?.let { mode ->
                            add(EarbudEvent.NoiseModeChanged(mode, acknowledged = true))
                        }
                        return@forEach
                    }
                }

                is BoseNoiseControlProfile.Cnc -> {
                    BoseBmapWireCodec.parseCncState(frame)?.let { state ->
                        currentCncEnabled = state.enabled
                        add(
                            EarbudEvent.NoiseModeChanged(
                                mode = when {
                                    !state.enabled -> NoiseMode.OFF
                                    state.rawLevel >= state.maximumRawLevel ->
                                        NoiseMode.TRANSPARENCY
                                    else -> NoiseMode.ANC
                                },
                                acknowledged = true,
                            ),
                        )
                        return@forEach
                    }
                }

                null -> Unit
            }

            add(
                EarbudEvent.UnknownFrame(
                    version = 0,
                    vendor = frame.functionBlock,
                    command = frame.function,
                    payloadSize = frame.payload.size,
                ),
            )
        }
    }

    override fun reset() {
        decoder.reset()
        modeConfigs.clear()
        identityAccepted = null
        activeProfile = null
        pendingBattery = null
        currentModeIndex = null
        currentCncEnabled = null
    }

    private fun BoseNoiseControlProfile.encode(mode: NoiseMode): List<ByteArray> = when (this) {
        is BoseNoiseControlProfile.AudioModes -> when (mode) {
            NoiseMode.ANC -> listOf(BoseBmapWireCodec.switchMode(quietModeIndex))
            NoiseMode.TRANSPARENCY -> listOf(BoseBmapWireCodec.switchMode(awareModeIndex))
            NoiseMode.WIND -> windModeIndex()
                ?.let { listOf(BoseBmapWireCodec.switchMode(it)) }
                .orEmpty()

            NoiseMode.OFF -> emptyList()
        }

        is BoseNoiseControlProfile.Anr -> when (mode) {
            NoiseMode.ANC -> listOf(BoseBmapWireCodec.setAnr(highValue))
            NoiseMode.OFF -> listOf(BoseBmapWireCodec.setAnr(offValue))
            NoiseMode.WIND -> listOf(BoseBmapWireCodec.setAnr(windValue))
            NoiseMode.TRANSPARENCY -> emptyList()
        }

        is BoseNoiseControlProfile.Cnc -> when (mode) {
            NoiseMode.ANC -> listOf(BoseBmapWireCodec.setCnc(rawLevel = 0, enabled = true))
            NoiseMode.TRANSPARENCY -> {
                val command = BoseBmapWireCodec.setCnc(
                    rawLevel = maximumRawLevel,
                    enabled = true,
                )
                // NC700 powers ANC back on at its maximum level; a second SETGET is required
                // only when enabling directly into the fully-aware endpoint.
                if (currentCncEnabled == false) listOf(command, command) else listOf(command)
            }

            NoiseMode.OFF -> listOf(BoseBmapWireCodec.setCnc(rawLevel = 0, enabled = false))
            NoiseMode.WIND -> emptyList()
        }
    }

    private fun BoseBmapProfile?.noiseReadCommands(): List<ByteArray> =
        this?.noiseControl?.let { control ->
            when (control) {
                is BoseNoiseControlProfile.AudioModes -> buildList {
                    if (control.modeConfigLayout != null) {
                        add(BoseBmapWireCodec.queryModeConfigs)
                    }
                    add(BoseBmapWireCodec.queryCurrentMode)
                }

                is BoseNoiseControlProfile.Anr -> listOf(BoseBmapWireCodec.queryAnr)
                is BoseNoiseControlProfile.Cnc -> listOf(BoseBmapWireCodec.queryCnc)
            }
        }.orEmpty()

    private fun BoseBmapProfile?.noiseStateReadCommands(): List<ByteArray> =
        this?.noiseControl?.let { control ->
            when (control) {
                is BoseNoiseControlProfile.AudioModes ->
                    listOf(BoseBmapWireCodec.queryCurrentMode)

                is BoseNoiseControlProfile.Anr -> listOf(BoseBmapWireCodec.queryAnr)
                is BoseNoiseControlProfile.Cnc -> listOf(BoseBmapWireCodec.queryCnc)
            }
        }.orEmpty()

    /**
     * GET-only probes for the three public BMAP noise-control generations.
     *
     * ERROR or absent responses are ignored. A write path is enabled only after one exact STATUS
     * frame passes the corresponding codec parser.
     */
    private fun capabilityProbeCommands(): List<ByteArray> = listOf(
        BoseBmapWireCodec.queryCurrentMode,
        BoseBmapWireCodec.queryCnc,
        BoseBmapWireCodec.queryAnr,
    )

    private fun discoverNoiseProfile(
        frame: BoseBmapWireCodec.Frame,
    ): BoseBmapProfile? {
        val cncState = BoseBmapWireCodec.parseCncState(frame)
        val (dialect, cncMaximumRawLevel) = when {
            BoseBmapWireCodec.parseCurrentMode(frame) != null ->
                BoseDiscoveredDialect.AUDIO_MODES to null

            cncState != null -> BoseDiscoveredDialect.CNC to cncState.maximumRawLevel

            BoseBmapWireCodec.parseAnrState(frame) != null ->
                BoseDiscoveredDialect.ANR to null

            else -> return null
        }
        return BoseCapabilityAdapterRegistry.profile(
            formFactor = fallbackFormFactor,
            dialect = dialect,
            cncMaximumRawLevel = cncMaximumRawLevel,
        )
    }

    private fun Int.toNoiseMode(profile: BoseNoiseControlProfile.AudioModes): NoiseMode? =
        when (this) {
            profile.quietModeIndex -> NoiseMode.ANC
            profile.awareModeIndex -> NoiseMode.TRANSPARENCY
            in profile.additionalAncModeIndices -> NoiseMode.ANC
            else -> modeConfigs[this]?.let { config ->
                when {
                    profile.windModeFromConfig && config.wind -> NoiseMode.WIND
                    config.rawCnc >= profile.fullAwareCnc -> NoiseMode.TRANSPARENCY
                    else -> NoiseMode.ANC
                }
            }
        }

    private fun Int.toNoiseMode(profile: BoseNoiseControlProfile.Anr): NoiseMode? = when (this) {
        profile.offValue -> NoiseMode.OFF
        profile.highValue -> NoiseMode.ANC
        profile.windValue -> NoiseMode.WIND
        else -> null
    }

    private fun BoseNoiseControlProfile.AudioModes.windModeIndex(): Int? {
        if (!windModeFromConfig) return null
        return modeConfigs.values
            .asSequence()
            .filterNot { it.index == quietModeIndex || it.index == awareModeIndex }
            .firstOrNull { it.wind }
            ?.index
    }
}
