package dev.hyperears.integration

import dev.hyperears.protocol.bose.BoseBmapWireCodec
import dev.hyperears.protocol.bose.BoseProductCatalog

/** QuietComfort 35 (`wolfcastle`, product `0x400C`). */
class BoseQuietComfort35Adapter : BoseBmapHeadphonesModelAdapter(
    id = "bose-quietcomfort-35-400c",
    product = BoseProductCatalog.QUIETCOMFORT_35,
    noiseControl = BoseNoiseControlConfig.Anr(),
    miLinkCardPresentationId = BoseMiLinkPresentationIds.WIND_REPLACES_TRANSPARENCY,
)

/** QuietComfort 35 II (`baywolf`, product `0x4020`). */
class BoseQuietComfort35IIAdapter : BoseBmapHeadphonesModelAdapter(
    id = "bose-quietcomfort-35-ii-4020",
    product = BoseProductCatalog.QUIETCOMFORT_35_II,
    noiseControl = BoseNoiseControlConfig.Anr(),
    miLinkCardPresentationId = BoseMiLinkPresentationIds.WIND_REPLACES_TRANSPARENCY,
)

/** Noise Cancelling Headphones 700 (`goodyear`, product `0x4024`). */
class BoseNoiseCancellingHeadphones700Adapter : BoseBmapHeadphonesModelAdapter(
    id = "bose-nc-headphones-700-4024",
    product = BoseProductCatalog.NC_HEADPHONES_700,
    noiseControl = BoseNoiseControlConfig.Cnc(),
)

/** QuietComfort 45 (`duran`, product `0x4039`). */
class BoseQuietComfort45Adapter : BoseBmapHeadphonesModelAdapter(
    id = "bose-quietcomfort-45-4039",
    product = BoseProductCatalog.QUIETCOMFORT_45,
    noiseControl = twoModeAudioModes(),
    miLinkCardPresentationId = BoseMiLinkPresentationIds.TWO_MODE,
)

/** QuietComfort Ultra Headphones (`lonestarr`, product `0x4066`). */
class BoseQuietComfortUltraHeadphonesAdapter : BoseBmapHeadphonesModelAdapter(
    id = "bose-quietcomfort-ultra-headphones-4066",
    product = BoseProductCatalog.QUIETCOMFORT_ULTRA_HEADPHONES,
    noiseControl = twoModeAudioModes(additionalAncModeIndices = setOf(2, 3)),
    miLinkCardPresentationId = BoseMiLinkPresentationIds.TWO_MODE,
)

/** QuietComfort Headphones (`prince`, product `0x4075`), locally verified. */
class BoseQuietComfortHeadphonesAdapter : BoseBmapHeadphonesModelAdapter(
    id = "bose-quietcomfort-headphones-4075",
    product = BoseProductCatalog.QUIETCOMFORT_HEADPHONES,
    noiseControl = BoseNoiseControlConfig.AudioModes(
        quietModeIndex = 0,
        awareModeIndex = 1,
        fullAwareCnc = 10,
        modeConfigLayout = BoseBmapWireCodec.PRINCE_MODE_CONFIG_LAYOUT,
        windModeFromConfig = true,
        supportedModes = setOf(
            NoiseMode.ANC,
            NoiseMode.TRANSPARENCY,
            NoiseMode.WIND,
        ),
    ),
    miLinkCardPresentationId = BoseMiLinkPresentationIds.WIND_REPLACES_OFF,
) {
    companion object {
        const val ID = "bose-quietcomfort-headphones-4075"
        const val PRODUCT_ID = 0x4075
        val PRESENTATION_ID = BoseMiLinkPresentationIds.WIND_REPLACES_OFF
    }
}

/** QuietComfort Ultra Headphones (2nd Gen) (`wolverine`, product `0x4082`). */
class BoseQuietComfortUltraHeadphones2Adapter : BoseBmapHeadphonesModelAdapter(
    id = "bose-quietcomfort-ultra-headphones-2-4082",
    product = BoseProductCatalog.QUIETCOMFORT_ULTRA_HEADPHONES_2,
    noiseControl = twoModeAudioModes(additionalAncModeIndices = setOf(2, 3)),
    miLinkCardPresentationId = BoseMiLinkPresentationIds.TWO_MODE,
)

/** QuietComfort Earbuds (`lando`, product `0x402F`). */
class BoseQuietComfortEarbudsAdapter : BoseBmapModelAdapter(
    id = "bose-quietcomfort-earbuds-402f",
    product = BoseProductCatalog.QUIETCOMFORT_EARBUDS,
    noiseControl = twoModeAudioModes(),
    miLinkCardPresentationId = BoseMiLinkPresentationIds.TWO_MODE,
)

/** QuietComfort Earbuds II (`smalls`, product `0x4064`). */
class BoseQuietComfortEarbudsIIAdapter : BoseBmapModelAdapter(
    id = "bose-quietcomfort-earbuds-ii-4064",
    product = BoseProductCatalog.QUIETCOMFORT_EARBUDS_II,
    noiseControl = twoModeAudioModes(),
    miLinkCardPresentationId = BoseMiLinkPresentationIds.TWO_MODE,
)

/** QuietComfort Ultra Earbuds (`scotty`, product `0x4072`). */
class BoseQuietComfortUltraEarbudsAdapter : BoseBmapModelAdapter(
    id = "bose-quietcomfort-ultra-earbuds-4072",
    product = BoseProductCatalog.QUIETCOMFORT_ULTRA_EARBUDS,
    noiseControl = twoModeAudioModes(additionalAncModeIndices = setOf(2, 3)),
    miLinkCardPresentationId = BoseMiLinkPresentationIds.TWO_MODE,
)

/** QuietComfort Ultra Earbuds (2nd Gen) (`edith`, product `0x4062`). */
class BoseQuietComfortUltraEarbuds2Adapter : BoseBmapModelAdapter(
    id = "bose-quietcomfort-ultra-earbuds-2-4062",
    product = BoseProductCatalog.QUIETCOMFORT_ULTRA_EARBUDS_2,
    noiseControl = twoModeAudioModes(additionalAncModeIndices = setOf(2, 3)),
    miLinkCardPresentationId = BoseMiLinkPresentationIds.TWO_MODE,
)

/** Known BMAP products whose public sources establish identity, but not safe control writes. */
private class BoseCatalogModelAdapter(
    id: String,
    product: BoseProductCatalog.Product,
) : BoseBmapModelAdapter(
    id = id,
    product = product,
)

private class BoseCatalogHeadphonesAdapter(
    id: String,
    product: BoseProductCatalog.Product,
) : BoseBmapHeadphonesModelAdapter(
    id = id,
    product = product,
)

/** Single catalog of concrete Bose product adapter definitions. */
object BoseBmapModelRegistry {
    val factories: List<() -> EarbudAdapter> = listOf(
        ::BoseQuietComfort35Adapter,
        ::BoseQuietComfort35IIAdapter,
        ::BoseNoiseCancellingHeadphones700Adapter,
        ::BoseQuietComfort45Adapter,
        ::BoseQuietComfortUltraHeadphonesAdapter,
        ::BoseQuietComfortHeadphonesAdapter,
        ::BoseQuietComfortUltraHeadphones2Adapter,
        ::BoseQuietComfortEarbudsAdapter,
        ::BoseQuietComfortEarbudsIIAdapter,
        ::BoseQuietComfortUltraEarbudsAdapter,
        ::BoseQuietComfortUltraEarbuds2Adapter,
        { BoseCatalogHeadphonesAdapter(
            id = "bose-hearphones-4015",
            product = BoseProductCatalog.HEARPHONES,
        ) },
        { BoseCatalogHeadphonesAdapter(
            id = "bose-proflight-4021",
            product = BoseProductCatalog.PROFLIGHT,
        ) },
        { BoseCatalogHeadphonesAdapter(
            id = "bose-hearphones-ii-402b",
            product = BoseProductCatalog.HEARPHONES_II,
        ) },
        { BoseCatalogModelAdapter(
            id = "bose-soundsport-4012",
            product = BoseProductCatalog.SOUNDSPORT,
        ) },
        { BoseCatalogModelAdapter(
            id = "bose-soundsport-pulse-4013",
            product = BoseProductCatalog.SOUNDSPORT_PULSE,
        ) },
        { BoseCatalogModelAdapter(
            id = "bose-quietcontrol-30-4014",
            product = BoseProductCatalog.QUIETCONTROL_30,
        ) },
        { BoseCatalogModelAdapter(
            id = "bose-soundsport-free-4018",
            product = BoseProductCatalog.SOUNDSPORT_FREE,
        ) },
        { BoseCatalogModelAdapter(
            id = "bose-sport-earbuds-402d",
            product = BoseProductCatalog.SPORT_EARBUDS,
        ) },
        { BoseCatalogModelAdapter(
            id = "bose-sport-open-earbuds-403a",
            product = BoseProductCatalog.SPORT_OPEN_EARBUDS,
        ) },
        { BoseCatalogModelAdapter(
            id = "bose-ultra-open-earbuds-4068",
            product = BoseProductCatalog.ULTRA_OPEN_EARBUDS,
        ) },
    )

    val adapters: List<EarbudAdapter> get() = factories.map { it() }

    private val configurations = adapters
        .mapNotNull { adapter ->
            when (adapter) {
                is BoseBmapModelAdapter -> adapter.wireConfig
                is BoseBmapHeadphonesModelAdapter -> adapter.wireConfig
                else -> null
            }
        }
        .associateBy(BoseWireConfig::productId)

    init {
        require(configurations.size == adapters.size) {
            "Bose BMAP product IDs must be unique"
        }
        require(configurations.keys == BoseProductCatalog.products.map { it.productId }.toSet()) {
            "Every catalogued Bose headset product must have one concrete adapter"
        }
    }

    fun find(productId: Int): BoseWireConfig? = configurations[productId]
}

private fun twoModeAudioModes(
    additionalAncModeIndices: Set<Int> = emptySet(),
): BoseNoiseControlConfig.AudioModes = BoseNoiseControlConfig.AudioModes(
    additionalAncModeIndices = additionalAncModeIndices,
    supportedModes = setOf(NoiseMode.ANC, NoiseMode.TRANSPARENCY),
)
