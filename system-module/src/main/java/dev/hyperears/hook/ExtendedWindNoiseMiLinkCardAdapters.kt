package dev.hyperears.hook

import dev.hyperears.integration.NiceHckYuanDaoOrigAdapter
import dev.hyperears.integration.RoseBudsFeelMk2Adapter
import dev.hyperears.integration.RoseEarfreeI5Adapter

/** Model-owned registrations reusing the same native MiLink wind-noise accessory contract. */
internal object RoseEarfreeI5MiLinkCardAdapter : WindNoiseToggleMiLinkCardAdapter(
    presentationId = RoseEarfreeI5Adapter.PRESENTATION_ID,
    modelLabel = "ROSE EARFREE protocol family",
)

internal object RoseBudsFeelMk2MiLinkCardAdapter : WindNoiseToggleMiLinkCardAdapter(
    presentationId = RoseBudsFeelMk2Adapter.PRESENTATION_ID,
    modelLabel = "ROSE BudsFeel protocol family",
)

internal object NiceHckOrigMiLinkCardAdapter : WindNoiseToggleMiLinkCardAdapter(
    presentationId = NiceHckYuanDaoOrigAdapter.PRESENTATION_ID,
    modelLabel = "NiceHCK YuanDao OriG in",
)
