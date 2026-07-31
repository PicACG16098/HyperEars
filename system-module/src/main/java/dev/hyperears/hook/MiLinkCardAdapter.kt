package dev.hyperears.hook

import android.annotation.SuppressLint
import android.content.Context
import android.view.View
import android.widget.LinearLayout
import dev.hyperears.integration.EarbudState
import dev.hyperears.integration.MiLinkCardPresentationId
import dev.hyperears.integration.NoiseMode
import java.util.WeakHashMap

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
    val nativeSelectionController: MiLinkNativeAncSelectionController,
)

/**
 * Guards MiLink's native three-state renderer only for model adapters that register extra modes.
 *
 * WIND must travel through MiLink's stock ANC integer to keep the native card available. MiLink
 * therefore reselects ANC after receiving a WIND callback. Only that visible WIND-to-ANC
 * projection is rejected; every other native selection is accepted and clears its peer items.
 * This deliberately avoids consulting process-local protocol snapshots, which may lag behind a
 * remote MiLink card.
 */
internal class MiLinkNativeAncSelectionController {
    private data class Rule(
        val address: String,
        val itemMode: NoiseMode,
    )

    private val lock = Any()
    private val rules = WeakHashMap<View, Rule>()
    private val acceptedSelection = ThreadLocal<View?>()

    fun register(view: View, address: String, itemMode: NoiseMode) {
        synchronized(lock) {
            rules[view] = Rule(address, itemMode)
        }
    }

    fun unregister(view: View) {
        synchronized(lock) {
            rules.remove(view)
        }
    }

    fun shouldSuppress(view: View, requestedSelected: Boolean): Boolean {
        if (!requestedSelected) return false
        if (acceptedSelection.get() === view) return false
        val (rule, peers, windSelected) = synchronized(lock) {
            val matchedRule = rules[view] ?: return false
            val matching = rules.entries.filter { (_, candidateRule) ->
                candidateRule.address == matchedRule.address
            }
            Triple(
                matchedRule,
                matching.map(Map.Entry<View, Rule>::key).filter { it !== view },
                matching.any { (candidate, candidateRule) ->
                    candidateRule.itemMode == NoiseMode.WIND && candidate.isSelected
                },
            )
        }
        val suppress = MiLinkNativeAncSelectionPolicy.shouldSuppress(
            itemMode = rule.itemMode,
            windSelected = windSelected,
            requestedSelected = requestedSelected,
        )
        if (!suppress) {
            peers.forEach { peer -> peer.isSelected = false }
        }
        return suppress
    }

    /**
     * Reflects MiLink's successful semantic control operation without waiting for its later
     * property callback to traverse the remote-service pipeline. The real device callback still
     * renders through the normal path and therefore remains authoritative.
     */
    fun onControlAccepted(address: String, mode: NoiseMode) {
        val (target, peers) = synchronized(lock) {
            val matching = rules.entries.filter { (_, rule) ->
                rule.address.equals(address, ignoreCase = true)
            }
            val selected = matching.firstOrNull { (_, rule) -> rule.itemMode == mode }?.key
                ?: return
            selected to matching.map(Map.Entry<View, Rule>::key).filter { it !== selected }
        }
        target.post {
            peers.forEach { peer -> peer.isSelected = false }
            acceptedSelection.set(target)
            try {
                target.isSelected = true
            } finally {
                acceptedSelection.remove()
            }
        }
    }
}

internal object MiLinkNativeAncModeCodec {
    fun decode(mode: Int): NoiseMode? = when (mode) {
        0 -> NoiseMode.ANC
        1 -> NoiseMode.TRANSPARENCY
        2 -> NoiseMode.OFF
        else -> null
    }
}

internal object MiLinkNativeAncSelectionPolicy {
    fun shouldSuppress(
        itemMode: NoiseMode,
        windSelected: Boolean,
        requestedSelected: Boolean,
    ): Boolean =
        requestedSelected && itemMode == NoiseMode.ANC && windSelected
}

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

/**
 * Creates one item using MiLink's native ANC-item class and the row's existing layout contract.
 *
 * Concrete card adapters own the item's semantics; this helper only centralizes stable host-view
 * construction so model adapters never draw an imitation of MiLink's controls.
 */
internal fun createNativeMiLinkAncItem(
    context: Context,
    hostClassLoader: ClassLoader,
    layoutTemplate: View,
): View? = runCatching {
    val item = Class.forName(HOST_ANC_ITEM_CLASS, true, hostClassLoader)
        .asSubclass(View::class.java)
        .getConstructor(Context::class.java)
        .newInstance(context)
    item.layoutParams = when (val source = layoutTemplate.layoutParams) {
        is LinearLayout.LayoutParams -> LinearLayout.LayoutParams(source)
        else -> LinearLayout.LayoutParams(
            0,
            LinearLayout.LayoutParams.WRAP_CONTENT,
            1f,
        )
    }
    item
}.onFailure {
    ModuleLog.warn("MiLinkUi", "native ANC item unavailable", it)
}.getOrNull()

/** Stable, non-obfuscated native item boundary shared by construction and selection guarding. */
internal const val HOST_ANC_ITEM_CLASS =
    "com.miui.circulate.world.headset.ui.HeadsetControlAncItemView"
private const val MILINK_PACKAGE = "com.milink.service"
