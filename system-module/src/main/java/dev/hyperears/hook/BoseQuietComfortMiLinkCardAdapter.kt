package dev.hyperears.hook

import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import dev.hyperears.integration.BoseQuietComfortHeadphonesAdapter
import dev.hyperears.integration.EarbudState
import dev.hyperears.integration.NoiseMode
import java.lang.ref.WeakReference

/**
 * Adapts MiLink's native over-ear ANC card to QuietComfort's two authoritative modes.
 *
 * MiLink 17.2.4 owns the type-7 headphones artwork, single-battery layout and ANC lifecycle.
 * Its capability ABI cannot express Quiet + Aware without Off, so this concrete model adapter
 * detaches that one unsupported action after the native card is bound. A hidden, same-ID
 * [LinearLayout] remains in its place so later host lookups remain type-safe, while MiLink's
 * cached reference to the original action can no longer make it visible. The two remaining
 * selections are rendered from HyperEars' device-confirmed state so a missed host callback or
 * card rebind cannot leave the native presentation stale. On a receiving device with no local
 * headset session, the binding preserves MiLink's transported native selection instead.
 */
internal object BoseQuietComfortMiLinkCardAdapter : MiLinkCardAdapter {
    override val presentationId = BoseQuietComfortHeadphonesAdapter.PRESENTATION_ID

    override fun bind(
        root: View,
        address: String,
        environment: MiLinkCardEnvironment,
    ): MiLinkCardBinding? {
        val ancCard = root.findMiLinkView(ANC_CARD_ID) as? ViewGroup ?: return null
        val transparency = root.findMiLinkView(ANC_TRANSPARENCY_ID) ?: return null
        val noiseCancellation = root.findMiLinkView(ANC_NOISE_CANCELLATION_ID) ?: return null
        val unsupportedOff = root.findMiLinkView(ANC_OFF_ID) ?: return null
        if (
            transparency.parent !== ancCard ||
            noiseCancellation.parent !== ancCard ||
            unsupportedOff.parent !== ancCard
        ) {
            return null
        }

        val index = ancCard.indexOfChild(unsupportedOff).takeIf { it >= 0 } ?: return null
        val originalLayoutParams = unsupportedOff.layoutParams
        val originalVisibility = unsupportedOff.visibility
        val placeholder = LinearLayout(root.context).apply {
            id = unsupportedOff.id
            layoutParams = originalLayoutParams
            visibility = View.GONE
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
        }

        ancCard.removeViewAt(index)
        ancCard.addView(placeholder, index)
        ModuleLog.debug("MiLinkUi", "bound Bose QuietComfort native mode filter")

        return Binding(
            parent = ancCard,
            originalIndex = index,
            originalLayoutParams = originalLayoutParams,
            originalVisibility = originalVisibility,
            unsupportedOff = unsupportedOff,
            placeholder = placeholder,
            transparency = transparency,
            noiseCancellation = noiseCancellation,
        )
    }

    private class Binding(
        parent: ViewGroup,
        private val originalIndex: Int,
        private val originalLayoutParams: ViewGroup.LayoutParams,
        private val originalVisibility: Int,
        unsupportedOff: View,
        placeholder: View,
        transparency: View,
        noiseCancellation: View,
    ) : MiLinkCardBinding {
        private val parent = WeakReference(parent)
        private val unsupportedOff = WeakReference(unsupportedOff)
        private val placeholder = WeakReference(placeholder)
        private val transparency = WeakReference(transparency)
        private val noiseCancellation = WeakReference(noiseCancellation)

        override fun render(state: EarbudState) {
            placeholder.get()?.visibility = View.GONE
            val mode = state.noiseMode ?: return
            transparency.get()?.setSelectedTree(
                isTransparencySelected(mode),
            )
            noiseCancellation.get()?.setSelectedTree(
                isNoiseCancellationSelected(mode),
            )
        }

        override fun unbind() {
            val parent = parent.get() ?: return
            val placeholder = placeholder.get() ?: return
            val unsupportedOff = unsupportedOff.get() ?: return
            if (placeholder.parent !== parent) return

            parent.removeView(placeholder)
            if (unsupportedOff.parent == null) {
                unsupportedOff.layoutParams = originalLayoutParams
                unsupportedOff.visibility = originalVisibility
                parent.addView(
                    unsupportedOff,
                    originalIndex.coerceAtMost(parent.childCount),
                )
            }
        }
    }

    internal fun isTransparencySelected(mode: NoiseMode?): Boolean =
        mode == NoiseMode.TRANSPARENCY

    internal fun isNoiseCancellationSelected(mode: NoiseMode?): Boolean =
        mode == NoiseMode.ANC

    private fun View.setSelectedTree(selected: Boolean) {
        isSelected = selected
        if (this !is ViewGroup) return
        for (index in 0 until childCount) {
            getChildAt(index).setSelectedTree(selected)
        }
    }

    private const val ANC_CARD_ID = "anc_card"
    private const val ANC_TRANSPARENCY_ID = "anc_clear"
    private const val ANC_NOISE_CANCELLATION_ID = "anc_noise_cancel"
    private const val ANC_OFF_ID = "anc_off"
}
