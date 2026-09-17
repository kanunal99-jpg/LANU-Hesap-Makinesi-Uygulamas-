package com.example.ui

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.HapticFeedbackConstants
import android.view.View
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalView

class HapticHelper(
  private val context: Context,
  private val view: View? = null,
  private val composeHaptic: HapticFeedback? = null
) {
  private val vibrator: Vibrator? by lazy {
    try {
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
        vibratorManager?.defaultVibrator
      } else {
        @Suppress("DEPRECATION")
        context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
      }
    } catch (_: Exception) {
      null
    }
  }

  /**
   * Performs subtle, tactile haptic feedback for calculator keypad button presses.
   */
  fun performKeyClickHaptic() {
    try {
      // 1. Compose-level tactile haptic
      composeHaptic?.performHapticFeedback(HapticFeedbackType.TextHandleMove)

      // 2. View-level keyboard click haptic feedback
      val viewSuccess = view?.performHapticFeedback(
        HapticFeedbackConstants.KEYBOARD_TAP,
        HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING
      ) ?: false

      // 3. Hardware vibrator fallback for a crisp 15ms pulse
      if (!viewSuccess && vibrator?.hasVibrator() == true) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
          vibrator?.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_CLICK))
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
          vibrator?.vibrate(VibrationEffect.createOneShot(15, 60))
        } else {
          @Suppress("DEPRECATION")
          vibrator?.vibrate(15)
        }
      }
    } catch (_: Exception) {
      // Gracefully catch any hardware or permission constraints
    }
  }
}

@Composable
fun rememberKeyHapticHelper(): HapticHelper {
  val context = LocalContext.current
  val view = LocalView.current
  val composeHaptic = LocalHapticFeedback.current

  return remember(context, view, composeHaptic) {
    HapticHelper(context, view, composeHaptic)
  }
}
