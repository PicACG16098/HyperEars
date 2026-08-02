package dev.hyperears.integration

/** A BMAP noise-control dialect established from a successful read-only status response. */
internal enum class BoseDiscoveredDialect {
    AUDIO_MODES,
    ANR,
    CNC,
}

/**
 * Adapters used only when Bose BMAP confirms a control dialect but the product ID is not listed.
 *
 * They are deliberately not name matchers. A live protocol response is the sole upgrade path,
 * preserving the same model/capability boundary used by concrete product adapters.
 */
private class BoseDiscoveredEarbudAdapter(
    override val id: String,
    override val displayName: String,
    override val bmapProfile: BoseBmapProfile,
    override val miLinkCardPresentationId: MiLinkCardPresentationId?,
) : BoseEarbudAdapter() {
    override val supportedNoiseModes: Set<NoiseMode> =
        bmapProfile.noiseControl?.supportedModes.orEmpty()
    override val capabilities: EarbudCapabilities = super.capabilities.copy(
        noiseControl = supportedNoiseModes.isNotEmpty(),
        windNoiseControl = NoiseMode.WIND in supportedNoiseModes,
    )

    override fun matches(identity: EarbudIdentity): Boolean = false
}

private class BoseDiscoveredHeadphonesAdapter(
    override val id: String,
    override val displayName: String,
    override val bmapProfile: BoseBmapProfile,
    override val miLinkCardPresentationId: MiLinkCardPresentationId?,
) : BoseHeadphonesAdapter() {
    override val supportedNoiseModes: Set<NoiseMode> =
        bmapProfile.noiseControl?.supportedModes.orEmpty()
    override val capabilities: EarbudCapabilities = super.capabilities.copy(
        noiseControl = supportedNoiseModes.isNotEmpty(),
        windNoiseControl = NoiseMode.WIND in supportedNoiseModes,
    )

    override fun matches(identity: EarbudIdentity): Boolean = false
}

/** Runtime-only Bose family profiles selected by wire evidence rather than a guessed model name. */
internal object BoseCapabilityAdapterRegistry {
    private data class ProfileTemplate(
        val suffix: String,
        val label: String,
        val control: BoseNoiseControlProfile,
        val presentationId: MiLinkCardPresentationId?,
    )

    private val templates = mapOf(
        BoseDiscoveredDialect.AUDIO_MODES to ProfileTemplate(
            suffix = "audio-modes",
            label = "Quiet/Aware",
            control = BoseNoiseControlProfile.AudioModes(
                supportedModes = setOf(NoiseMode.ANC, NoiseMode.TRANSPARENCY),
            ),
            presentationId = BoseMiLinkPresentationIds.TWO_MODE,
        ),
        BoseDiscoveredDialect.ANR to ProfileTemplate(
            suffix = "anr",
            label = "ANR",
            control = BoseNoiseControlProfile.Anr(),
            presentationId = BoseMiLinkPresentationIds.WIND_REPLACES_TRANSPARENCY,
        ),
        BoseDiscoveredDialect.CNC to ProfileTemplate(
            suffix = "cnc",
            label = "CNC",
            control = BoseNoiseControlProfile.Cnc(),
            presentationId = null,
        ),
    )

    val adapters: List<EarbudAdapter> = buildList {
        HeadsetFormFactor.entries.forEach { formFactor ->
            templates.values.forEach { template ->
                val formSuffix = when (formFactor) {
                    HeadsetFormFactor.TWS -> "earbuds"
                    HeadsetFormFactor.HEADPHONES -> "headphones"
                }
                val id = "bose-discovered-$formSuffix-${template.suffix}"
                val profile = BoseBmapProfile(
                    productId = null,
                    modelId = id,
                    noiseControl = template.control,
                )
                val displayName = "Bose $formSuffix (${template.label})"
                add(
                    when (formFactor) {
                        HeadsetFormFactor.TWS -> BoseDiscoveredEarbudAdapter(
                            id = id,
                            displayName = displayName,
                            bmapProfile = profile,
                            miLinkCardPresentationId = template.presentationId,
                        )

                        HeadsetFormFactor.HEADPHONES -> BoseDiscoveredHeadphonesAdapter(
                            id = id,
                            displayName = displayName,
                            bmapProfile = profile,
                            miLinkCardPresentationId = template.presentationId,
                        )
                    },
                )
            }
        }
    }

    private val profiles = adapters.associate { adapter ->
        val profile = when (adapter) {
            is BoseDiscoveredEarbudAdapter -> adapter.bmapProfile
            is BoseDiscoveredHeadphonesAdapter -> adapter.bmapProfile
            else -> error("Unexpected Bose capability adapter: ${adapter::class.java.name}")
        }
        (adapter.formFactor to dialectOf(requireNotNull(profile.noiseControl))) to profile
    }

    init {
        require(profiles.size == HeadsetFormFactor.entries.size * BoseDiscoveredDialect.entries.size)
    }

    fun profile(
        formFactor: HeadsetFormFactor,
        dialect: BoseDiscoveredDialect,
        cncMaximumRawLevel: Int? = null,
    ): BoseBmapProfile {
        val profile = requireNotNull(profiles[formFactor to dialect])
        if (dialect != BoseDiscoveredDialect.CNC || cncMaximumRawLevel == null) return profile
        return profile.copy(
            noiseControl = BoseNoiseControlProfile.Cnc(
                maximumRawLevel = cncMaximumRawLevel,
            ),
        )
    }

    private fun dialectOf(profile: BoseNoiseControlProfile): BoseDiscoveredDialect =
        when (profile) {
            is BoseNoiseControlProfile.AudioModes -> BoseDiscoveredDialect.AUDIO_MODES
            is BoseNoiseControlProfile.Anr -> BoseDiscoveredDialect.ANR
            is BoseNoiseControlProfile.Cnc -> BoseDiscoveredDialect.CNC
        }
}
