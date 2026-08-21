package dev.hyperears.hook

import dev.hyperears.integration.StarRingUltraAdapter

internal object StarRingUltraMiLinkCardAdapter : WindNoiseToggleMiLinkCardAdapter(
    presentationId = StarRingUltraAdapter.PRESENTATION_ID,
    modelLabel = "StarRing Ultra",
)
