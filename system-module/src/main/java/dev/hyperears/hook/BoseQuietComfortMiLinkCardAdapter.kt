package dev.hyperears.hook

import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import dev.hyperears.integration.BoseQuietComfortHeadphonesAdapter
import dev.hyperears.integration.EarbudState
import dev.hyperears.integration.NoiseMode
import java.lang.ref.WeakReference

/**
 * Presents Quiet, Aware and the device-reported wind preset on MiLink's native three-item row.
 *
 * Bose QuietComfort has no verified unauthenticated Off action. Its third useful action is instead
 * the custom ModeConfig slot whose `wind` flag is enabled. This adapter replaces MiLink's Off item
 * with another instance of MiLink's own ANC-item class, while the protocol resolves and switches
 * the corresponding Bose mode index. No ModeConfig parameter is edited here.
 */
internal object BoseQuietComfortMiLinkCardAdapter : MiLinkCardAdapter {
    override val presentationId = BoseQuietComfortHeadphonesAdapter.PRESENTATION_ID

    override fun bind(
        root: View,
        address: String,
        environment: MiLinkCardEnvironment,
    ): MiLinkCardBinding? {
        val ancCard = root.findMiLinkView(ANC_CARD_ID) as? LinearLayout ?: return null
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

        val wind = createNativeMiLinkAncItem(
            context = root.context,
            hostClassLoader = environment.hostClassLoader,
            layoutTemplate = unsupportedOff,
        ) ?: return null
        val windTitle = wind.findMiLinkView(ANC_TITLE_ID) as? TextView ?: return null
        val windIcon = wind.findMiLinkView(ANC_ICON_ID) as? ImageView ?: return null
        val noiseIcon =
            noiseCancellation.findMiLinkView(ANC_ICON_ID) as? ImageView ?: return null

        val index = ancCard.indexOfChild(unsupportedOff).takeIf { it >= 0 } ?: return null
        val originalLayoutParams = unsupportedOff.layoutParams
        val originalVisibility = unsupportedOff.visibility
        wind.id = unsupportedOff.id
        windTitle.text = WIND_LABEL
        windIcon.setImageDrawable(
            noiseIcon.drawable?.constantState
                ?.newDrawable(root.resources)
                ?.mutate()
                ?: noiseIcon.drawable,
        )
        wind.contentDescription = WIND_LABEL
        wind.visibility = View.VISIBLE
        wind.isSaveEnabled = false

        ancCard.removeViewAt(index)
        ancCard.addView(wind, index)

        val binding = Binding(
            parent = ancCard,
            originalIndex = index,
            originalLayoutParams = originalLayoutParams,
            originalVisibility = originalVisibility,
            unsupportedOff = unsupportedOff,
            transparency = transparency,
            noiseCancellation = noiseCancellation,
            wind = wind,
            address = address,
            environment = environment,
        )
        wind.setOnClickListener { binding.onWindClick() }
        ModuleLog.debug("MiLinkUi", "bound Bose QuietComfort native wind mode")
        return binding
    }

    private class Binding(
        parent: ViewGroup,
        private val originalIndex: Int,
        private val originalLayoutParams: ViewGroup.LayoutParams,
        private val originalVisibility: Int,
        unsupportedOff: View,
        transparency: View,
        noiseCancellation: View,
        wind: View,
        private val address: String,
        private val environment: MiLinkCardEnvironment,
    ) : MiLinkCardBinding {
        private val parent = WeakReference(parent)
        private val unsupportedOff = WeakReference(unsupportedOff)
        private val transparency = WeakReference(transparency)
        private val noiseCancellation = WeakReference(noiseCancellation)
        private val wind = WeakReference(wind)

        override fun render(state: EarbudState) {
            wind.get()?.apply {
                isEnabled =
                    state.sessionActive &&
                    state.connected &&
                    state.noiseMode != null
                alpha = if (isEnabled) ENABLED_ALPHA else DISABLED_ALPHA
            }
            val mode = state.noiseMode ?: return
            transparency.get()?.setSelectedTree(
                isModeSelected(NoiseMode.TRANSPARENCY, mode),
            )
            noiseCancellation.get()?.setSelectedTree(
                isModeSelected(NoiseMode.ANC, mode),
            )
            wind.get()?.setSelectedTree(
                isModeSelected(NoiseMode.WIND, mode),
            )
        }

        fun onWindClick() {
            val current = environment.stateProvider(address)
            if (
                !current.sessionActive ||
                !current.connected ||
                current.noiseMode == NoiseMode.WIND
            ) {
                return
            }
            environment.controlSender(address, NoiseMode.WIND)
        }

        override fun unbind() {
            val parent = parent.get() ?: return
            val wind = wind.get() ?: return
            val unsupportedOff = unsupportedOff.get() ?: return
            if (wind.parent !== parent) return

            wind.setOnClickListener(null)
            parent.removeView(wind)
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

    internal fun isModeSelected(
        itemMode: NoiseMode,
        currentMode: NoiseMode?,
    ): Boolean = itemMode == currentMode

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
    private const val ANC_TITLE_ID = "anc_title"
    private const val ANC_ICON_ID = "anc_icon"
    private const val WIND_LABEL = "抗风噪"
    private const val ENABLED_ALPHA = 1.0f
    private const val DISABLED_ALPHA = 0.45f
}
