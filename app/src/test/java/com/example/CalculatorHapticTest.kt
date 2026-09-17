package com.example

import android.app.Application
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.core.app.ApplicationProvider
import com.example.ui.CalculatorScreen
import com.example.ui.CalculatorViewModel
import com.example.ui.HapticHelper
import com.example.ui.ThemeCustomizerScreen
import com.example.ui.theme.MyApplicationTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class CalculatorHapticTest {

  @get:Rule
  val composeTestRule = createComposeRule()

  @Test
  fun testHapticFeedback_EnabledByDefaultInViewModel() {
    val context = ApplicationProvider.getApplicationContext<Application>()
    val viewModel = CalculatorViewModel(context)

    assertTrue(
      "Haptik geri bildirim varsayılan olarak açık olmalıdır",
      viewModel.uiState.value.isHapticFeedbackEnabled
    )
  }

  @Test
  fun testHapticFeedback_ToggleState() {
    val context = ApplicationProvider.getApplicationContext<Application>()
    val viewModel = CalculatorViewModel(context)

    viewModel.setHapticFeedbackEnabled(false)
    assertFalse(viewModel.uiState.value.isHapticFeedbackEnabled)

    viewModel.setHapticFeedbackEnabled(true)
    assertTrue(viewModel.uiState.value.isHapticFeedbackEnabled)
  }

  @Test
  fun testHapticFeedback_CalculatorButtonsClick() {
    val context = ApplicationProvider.getApplicationContext<Application>()
    val viewModel = CalculatorViewModel(context)

    composeTestRule.setContent {
      MyApplicationTheme {
        CalculatorScreen(
          viewModel = viewModel,
          onNavigateFinancial = {},
          onNavigateTheme = {}
        )
      }
    }
    composeTestRule.waitForIdle()

    // Test clicking keypad buttons with haptics enabled
    composeTestRule.onNodeWithTag("calc_key_7").assertIsDisplayed().performClick()
    composeTestRule.waitForIdle()
    assertEquals("7", viewModel.uiState.value.expression)

    composeTestRule.onNodeWithTag("calc_key_+").assertIsDisplayed().performClick()
    composeTestRule.waitForIdle()

    composeTestRule.onNodeWithTag("calc_key_3").assertIsDisplayed().performClick()
    composeTestRule.waitForIdle()

    composeTestRule.onNodeWithTag("calc_key_=").assertIsDisplayed().performClick()
    composeTestRule.waitForIdle()

    assertEquals("10", viewModel.uiState.value.result)
  }

  @Test
  fun testHapticFeedback_ThemeCustomizerSwitchToggle() {
    val context = ApplicationProvider.getApplicationContext<Application>()
    val viewModel = CalculatorViewModel(context)

    composeTestRule.setContent {
      MyApplicationTheme {
        ThemeCustomizerScreen(
          viewModel = viewModel,
          onBack = {}
        )
      }
    }
    composeTestRule.waitForIdle()

    // Verify switch exists and reflects enabled state
    val switchNode = composeTestRule.onNodeWithTag("haptic_feedback_switch")
    switchNode.assertIsDisplayed()
    assertTrue(viewModel.uiState.value.isHapticFeedbackEnabled)

    // Toggle off
    switchNode.performClick()
    composeTestRule.waitForIdle()
    assertFalse(viewModel.uiState.value.isHapticFeedbackEnabled)

    // Toggle on again
    switchNode.performClick()
    composeTestRule.waitForIdle()
    assertTrue(viewModel.uiState.value.isHapticFeedbackEnabled)
  }

  @Test
  fun testHapticHelper_ExecutionSafety() {
    val context = ApplicationProvider.getApplicationContext<Application>()
    val helper = HapticHelper(context)

    // Ensure calling performKeyClickHaptic does not throw exceptions
    try {
      helper.performKeyClickHaptic()
    } catch (e: Exception) {
      fail("HapticHelper threw exception: ${e.message}")
    }
  }
}
