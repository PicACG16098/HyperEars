package dev.hyperears.integration

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient

/**
 * Honor X5s Pro ANC depth extension.
 *
 * The vendor protocol carries four ANC levels (smart / light / medium / deep) as a byte in the
 * mode command and the mode-state report. Depth is a model-specific feature, so it is exposed as
 * a typed [DeviceFeatureState] and a typed [ControlRequest] sibling of the standard requests;
 * serialization, process transport and Adapter validation are handled by the v2.0.0 framework.
 */
@Serializable
enum class HonorAncDepth {
    SMART,
    LIGHT,
    MEDIUM,
    DEEP,
}

/** Current and supported ANC depth for the active session; null when ANC is not active. */
@Serializable
@SerialName("honor.x5spro.anc_depth")
data class HonorAncDepthFeatureState(
    val current: HonorAncDepth?,
    val supported: Set<HonorAncDepth>,
) : DeviceFeatureState {
    @Transient
    override val featureId: String = FEATURE_ID

    companion object {
        const val FEATURE_ID = "honor.x5spro.anc_depth"
    }
}

/** Model-owned control requests for the Honor X5s Pro. */
@Serializable
sealed interface HonorControlRequest : ControlRequest {
    @Serializable
    @SerialName("honor.x5spro.set_anc_depth")
    data class SetAncDepth(
        val depth: HonorAncDepth,
    ) : HonorControlRequest
}
