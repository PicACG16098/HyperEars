package dev.hyperears.hook

import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import dev.hyperears.integration.EarbudState
import dev.hyperears.integration.EdifierMiLinkPresentationIds
import dev.hyperears.integration.NoiseMode
import dev.hyperears.integration.StandardControlRequest
import java.lang.ref.WeakReference

/**
 * Presents a confirmed four-mode Edifier dialect in MiLink's ANC card.
 *
 * The stock three-item row (ANC, transparency, off) is preserved. A fourth wind-noise item is
 * added using MiLink's own [HOST_ANC_ITEM_CLASS] so the host retains layout, typography, icon
 * sizing and selected-state animation. Vendor-specific ANC depths share MiLink's ANC item because
 * MiLink has no native slot for each depth.
 */
internal object EdifierFourModeMiLinkCardAdapter : MiLinkCardAdapter {
    override val presentationId = EdifierMiLinkPresentationIds.FOUR_MODE

    /** WIND is its own mode, not projected onto any native slot. */
    override fun projectNativeNoiseMode(mode: NoiseMode?): NoiseMode? = mode

    override fun bind(
        root: View,
        address: String,
        environment: MiLinkCardEnvironment,
    ): MiLinkCardBinding? {
        val ancCard = root.findMiLinkView(ANC_CARD_ID) as? LinearLayout ?: return null
        val transparency = root.findMiLinkView(ANC_TRANSPARENCY_ID) ?: return null
        val noiseCancellation = root.findMiLinkView(ANC_NOISE_CANCELLATION_ID) ?: return null
        val off = root.findMiLinkView(ANC_OFF_ID) ?: return null
        if (
            transparency.parent !== ancCard ||
            noiseCancellation.parent !== ancCard ||
            off.parent !== ancCard
        ) {
            return null
        }

        val wind = createNativeMiLinkAncItem(
            context = root.context,
            hostClassLoader = environment.hostClassLoader,
            layoutTemplate = noiseCancellation,
        ) ?: return null
        val windTitle = wind.findMiLinkView(ANC_TITLE_ID) as? TextView ?: return null
        val windIcon = wind.findMiLinkView(ANC_ICON_ID) as? ImageView ?: return null
        val noiseIcon =
            noiseCancellation.findMiLinkView(ANC_ICON_ID) as? ImageView ?: return null

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
        wind.isClickable = true
        wind.isFocusable = true
        ancCard.addView(wind)

        val binding = Binding(
            parent = ancCard,
            transparency = transparency,
            noiseCancellation = noiseCancellation,
            off = off,
            wind = wind,
            address = address,
            environment = environment,
        )
        wind.setOnClickListener { binding.onWindClick() }
        ModuleLog.debug("MiLinkUi", "bound Edifier four-mode presentation")
        return binding
    }

    private class Binding(
        parent: LinearLayout,
        transparency: View,
        noiseCancellation: View,
        off: View,
        wind: View,
        private val address: String,
        private val environment: MiLinkCardEnvironment,
    ) : MiLinkCardBinding {
        private val parent = WeakReference(parent)
        private val transparency = WeakReference(transparency)
        private val noiseCancellation = WeakReference(noiseCancellation)
        private val off = WeakReference(off)
        private val wind = WeakReference(wind)

        override fun render(state: EarbudState) {
            val mode = state.noiseMode
            val projected = projectNativeNoiseMode(mode)
            transparency.get()?.isSelected =
                isModeSelected(NoiseMode.TRANSPARENCY, projected)
            noiseCancellation.get()?.isSelected =
                isModeSelected(NoiseMode.ANC, projected)
            off.get()?.isSelected =
                isModeSelected(NoiseMode.OFF, projected)
            wind.get()?.apply {
                isSelected = isModeSelected(NoiseMode.WIND, mode)
                isEnabled = state.sessionActive && state.connected
                alpha = if (isEnabled) ENABLED_ALPHA else DISABLED_ALPHA
            }
        }

        fun onWindClick() {
            val current = environment.stateProvider(address)
            val target = EdifierFourModeControlPolicy.request(current) ?: return
            environment.controlSender(
                address,
                StandardControlRequest.SetNoiseMode(target),
            )
        }

        override fun unbind() {
            val parent = parent.get() ?: return
            val wind = wind.get() ?: return
            wind.setOnClickListener(null)
            if (wind.parent === parent) parent.removeView(wind)
        }
    }

    private fun isModeSelected(
        itemMode: NoiseMode,
        currentMode: NoiseMode?,
    ): Boolean = itemMode == currentMode

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

/** Pure toggle policy for the fourth native Edifier mode item. */
internal object EdifierFourModeControlPolicy {
    fun request(state: EarbudState): NoiseMode? {
        if (!state.sessionActive || !state.connected) return null
        return if (state.noiseMode == NoiseMode.WIND) NoiseMode.ANC else NoiseMode.WIND
    }
}
