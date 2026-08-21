package dev.hyperears.hook

import dev.hyperears.integration.EdifierMiLinkPresentationIds

/**
 * Presents protocol-confirmed Edifier WIND as an ANC-branch option on MiLink's native card.
 *
 * The shared adapter leaves MiLink's transparency, ANC and off controls intact and adds the
 * device-specific WIND switch beside the native section title. This keeps all four-mode families
 * on one presentation contract and lets that contract adapt to both supported MiLink layouts.
 */
internal object EdifierFourModeMiLinkCardAdapter : WindNoiseToggleMiLinkCardAdapter(
    presentationId = EdifierMiLinkPresentationIds.FOUR_MODE,
    modelLabel = "Edifier four-mode protocol family",
)
