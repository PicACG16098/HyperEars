package dev.hyperears.ui.components

import android.view.HapticFeedbackConstants
import android.view.View
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalView

/** Haptic feedback for explicit setting toggles; navigation and ordinary actions stay silent. */
internal class SwitchHaptics(
    private val view: View,
) {
    fun perform(checked: Boolean) {
        view.performHapticFeedback(
            if (checked) HapticFeedbackConstants.TOGGLE_ON else HapticFeedbackConstants.TOGGLE_OFF,
        )
    }
}

@Composable
internal fun rememberSwitchHaptics(): SwitchHaptics {
    val view = LocalView.current
    return remember(view) { SwitchHaptics(view) }
}
