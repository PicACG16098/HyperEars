package dev.hyperears.hook

import android.content.Context
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.CompoundButton
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.Switch
import android.widget.TextView
import androidx.core.view.isVisible
import dev.hyperears.integration.EarbudState
import dev.hyperears.integration.NoiseMode
import dev.hyperears.integration.StarRingUltraAdapter
import java.lang.ref.WeakReference

/** Adds StarRing Ultra's vendor-only wind control once to MiLink's native ANC card. */
internal object StarRingUltraMiLinkCardAdapter : MiLinkCardAdapter {
    override val presentationId = StarRingUltraAdapter.PRESENTATION_ID

    override fun bind(
        root: View,
        address: String,
        environment: MiLinkCardEnvironment,
    ): MiLinkCardBinding? {
        val title = root.findMiLinkView("anc_card_title") as? TextView ?: return null
        val ancCard = root.findMiLinkView("anc_card") ?: return null
        val parent = title.parent as? ViewGroup ?: return null
        val index = parent.indexOfChild(title).takeIf { it >= 0 } ?: return null
        val originalParams = title.layoutParams
        val originalWidth = originalParams.width

        parent.removeViewAt(index)
        val wrapper = FrameLayout(root.context).apply {
            layoutParams = originalParams.apply {
                width = ViewGroup.LayoutParams.MATCH_PARENT
            }
        }
        title.layoutParams = FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT,
        )
        wrapper.addView(title)

        val accessory = LinearLayout(root.context).apply {
            gravity = Gravity.CENTER_VERTICAL
            orientation = LinearLayout.HORIZONTAL
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_YES
        }
        val label = TextView(root.context).apply {
            text = WIND_LABEL
            setTextColor(title.currentTextColor)
            setTextSize(TypedValue.COMPLEX_UNIT_PX, title.textSize)
            typeface = title.typeface
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            setPadding(0, 0, root.context.dp(LABEL_END_PADDING_DP), 0)
        }
        val toggle = createHostToggle(root.context, environment.hostClassLoader).apply {
            contentDescription = WIND_LABEL
            isSaveEnabled = false
        }
        accessory.addView(label)
        accessory.addView(toggle)
        wrapper.addView(
            accessory,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
                Gravity.END or Gravity.CENTER_VERTICAL,
            ),
        )
        parent.addView(wrapper, index)

        val binding = Binding(
            parent = parent,
            originalIndex = index,
            originalLayoutParams = originalParams,
            originalWidth = originalWidth,
            wrapper = wrapper,
            title = title,
            ancCard = ancCard,
            accessory = accessory,
            toggle = toggle,
            address = address,
            environment = environment,
        )
        toggle.setOnCheckedChangeListener { _, checked -> binding.onToggle(checked) }
        ModuleLog.debug("MiLinkUi", "bound StarRing Ultra wind-noise presentation")
        return binding
    }

    private class Binding(
        parent: ViewGroup,
        private val originalIndex: Int,
        private val originalLayoutParams: ViewGroup.LayoutParams,
        private val originalWidth: Int,
        wrapper: View,
        title: View,
        ancCard: View,
        accessory: View,
        toggle: CompoundButton,
        private val address: String,
        private val environment: MiLinkCardEnvironment,
    ) : MiLinkCardBinding {
        private val parent = WeakReference(parent)
        private val wrapper = WeakReference(wrapper)
        private val title = WeakReference(title)
        private val ancCard = WeakReference(ancCard)
        private val accessory = WeakReference(accessory)
        private val toggle = WeakReference(toggle)
        private var rendering = false

        override fun render(state: EarbudState) {
            val wrapper = wrapper.get() ?: return
            val title = title.get() ?: return
            val ancCard = ancCard.get() ?: return
            val accessory = accessory.get() ?: return
            val toggle = toggle.get() ?: return
            wrapper.visibility = ancCard.visibility
            accessory.visibility =
                if (ancCard.isVisible && title.isVisible) View.VISIBLE else View.GONE
            val ancActive =
                state.noiseMode == NoiseMode.ANC || state.noiseMode == NoiseMode.WIND
            rendering = true
            toggle.isChecked = state.noiseMode == NoiseMode.WIND
            toggle.isEnabled = state.connected && ancActive
            toggle.alpha = if (toggle.isEnabled) ENABLED_ALPHA else DISABLED_ALPHA
            accessory.alpha = if (toggle.isEnabled) ENABLED_ALPHA else DISABLED_ALPHA
            rendering = false
        }

        fun onToggle(checked: Boolean) {
            if (rendering) return
            val current = environment.stateProvider(address)
            val currentIsAnc =
                current.noiseMode == NoiseMode.ANC || current.noiseMode == NoiseMode.WIND
            val toggle = toggle.get() ?: return
            rendering = true
            toggle.isChecked = current.noiseMode == NoiseMode.WIND
            rendering = false
            if (!current.connected || !currentIsAnc) return
            environment.controlSender(
                address,
                if (checked) NoiseMode.WIND else NoiseMode.ANC,
            )
        }

        override fun unbind() {
            val parent = parent.get() ?: return
            val wrapper = wrapper.get() ?: return
            val title = title.get() ?: return
            if (wrapper.parent !== parent) return
            (title.parent as? ViewGroup)?.removeView(title)
            parent.removeView(wrapper)
            originalLayoutParams.width = originalWidth
            title.layoutParams = originalLayoutParams
            parent.addView(title, originalIndex.coerceAtMost(parent.childCount))
        }
    }

    private fun createHostToggle(
        context: Context,
        hostClassLoader: ClassLoader,
    ): CompoundButton = runCatching {
        Class.forName(MIUIX_SLIDING_BUTTON, true, hostClassLoader)
            .asSubclass(CompoundButton::class.java)
            .getConstructor(Context::class.java)
            .newInstance(context)
    }.getOrElse {
        @Suppress("DEPRECATION")
        Switch(context)
    }

    private fun Context.dp(value: Int): Int =
        (value * resources.displayMetrics.density).toInt()

    private const val MIUIX_SLIDING_BUTTON = "miuix.slidingwidget.widget.SlidingButton"
    private const val WIND_LABEL = "抗风噪"
    private const val LABEL_END_PADDING_DP = 8
    private const val ENABLED_ALPHA = 1.0f
    private const val DISABLED_ALPHA = 0.45f
}
