package io.apimeter.wear.ui

import android.view.HapticFeedbackConstants
import android.view.View
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalView

/** Wear 触觉反馈：走 View.performHapticFeedback，振幅由系统 HapticFeedbackConstants 决定，无需 VIBRATE 权限，受系统「触觉总开关」控制。 */
class WearHaptics(private val view: View, val enabled: Boolean) {

    fun tick() {
        if (enabled) view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
    }

    fun confirm() {
        if (enabled) view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
    }

    fun longPress() {
        if (enabled) view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
    }

    fun reject() {
        if (enabled) view.performHapticFeedback(HapticFeedbackConstants.REJECT)
    }
}

@Composable
fun rememberWearHaptics(enabled: Boolean): WearHaptics {
    val view = LocalView.current
    return remember(view, enabled) { WearHaptics(view, enabled) }
}
