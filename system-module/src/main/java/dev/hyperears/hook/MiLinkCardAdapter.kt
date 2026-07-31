package dev.hyperears.hook

import android.annotation.SuppressLint
import android.view.View
import dev.hyperears.integration.EarbudState
import dev.hyperears.integration.MiLinkCardPresentationId
import dev.hyperears.integration.NoiseMode

/**
 * A model-specific, one-shot adaptation of MiLink's already-created native headset card.
 *
 * The common coordinator owns lifecycle only. Each concrete implementation owns its view
 * contract and must not perform Bluetooth I/O or poll state.
 */
internal interface MiLinkCardAdapter {
    val presentationId: MiLinkCardPresentationId

    fun bind(
        root: View,
        address: String,
        environment: MiLinkCardEnvironment,
    ): MiLinkCardBinding?
}

internal fun interface MiLinkCardBinding {
    fun render(state: EarbudState)

    /** Restores any host views replaced during [MiLinkCardAdapter.bind]. */
    fun unbind() = Unit
}

internal data class MiLinkCardEnvironment(
    val hostClassLoader: ClassLoader,
    val stateProvider: (String) -> EarbudState,
    val controlSender: (String, NoiseMode) -> Unit,
)

/**
 * Single composition root for model-specific MiLink presentations.
 *
 * The lifecycle coordinator resolves opaque IDs through this registry and therefore never
 * imports concrete models or contains their view contracts.
 */
internal object MiLinkCardAdapterRegistry {
    private val adapters = listOf(
        StarRingUltraMiLinkCardAdapter,
        BoseQuietComfortMiLinkCardAdapter,
    )
    private val byId = adapters.associateBy(MiLinkCardAdapter::presentationId)

    init {
        require(byId.size == adapters.size) {
            "MiLink card presentation IDs must be unique"
        }
    }

    fun resolve(id: MiLinkCardPresentationId): MiLinkCardAdapter? = byId[id]
}

@SuppressLint("DiscouragedApi")
internal fun View.findMiLinkView(name: String): View? {
    val id = resources.getIdentifier(name, "id", context.packageName)
        .takeIf { it != 0 }
        ?: resources.getIdentifier(name, "id", MILINK_PACKAGE)
    return id.takeIf { it != 0 }?.let(::findViewById)
}

private const val MILINK_PACKAGE = "com.milink.service"
