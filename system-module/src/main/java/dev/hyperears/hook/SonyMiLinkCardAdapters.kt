package dev.hyperears.hook

import android.view.View
import dev.hyperears.integration.EarbudState
import dev.hyperears.integration.MiLinkCardPresentationId
import dev.hyperears.integration.SonyMiLinkPresentationIds

/** WF-C510 exposes ambient sound and off, but no noise-cancelling command. */
internal object SonyAmbientOnlyMiLinkCardAdapter : MiLinkCardAdapter {
    override val presentationId: MiLinkCardPresentationId =
        SonyMiLinkPresentationIds.AMBIENT_ONLY

    override fun bind(
        root: View,
        address: String,
        environment: MiLinkCardEnvironment,
    ): MiLinkCardBinding? {
        val noiseCancellation = root.findMiLinkView(ANC_NOISE_CANCELLATION_ID) ?: return null
        val originalVisibility = noiseCancellation.visibility
        noiseCancellation.visibility = View.GONE
        return object : MiLinkCardBinding {
            override fun render(state: EarbudState) {
                noiseCancellation.visibility = View.GONE
            }

            override fun unbind() {
                noiseCancellation.visibility = originalVisibility
            }
        }
    }

    private const val ANC_NOISE_CANCELLATION_ID = "anc_noise_cancel"
}
