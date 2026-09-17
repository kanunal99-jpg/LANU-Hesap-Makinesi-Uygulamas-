package com.example

import android.app.Application
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.core.app.ApplicationProvider
import com.example.ui.CalculatorViewModel
import com.example.ui.CallType
import com.example.ui.SecretAreaScreen
import com.example.ui.theme.MyApplicationTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class SecretMessagingAndCallTest {

  @get:Rule
  val composeTestRule = createComposeRule()

  private fun setupScreen(initiallyUnlocked: Boolean = true): CalculatorViewModel {
    val context = ApplicationProvider.getApplicationContext<Application>()
    val viewModel = CalculatorViewModel(context)

    if (initiallyUnlocked) {
      viewModel.verifySecretPassword("admin2011")
    }

    composeTestRule.setContent {
      MyApplicationTheme {
        SecretAreaScreen(
          viewModel = viewModel,
          onBack = {}
        )
      }
    }
    composeTestRule.waitForIdle()
    return viewModel
  }

  @Test
  fun testAccessibility_ContentDescriptionsExistForNavigationAndActions() {
    setupScreen(initiallyUnlocked = true)

    // Verify Tab accessibility content descriptions
    composeTestRule.onNodeWithContentDescription("Profil Sekmesi")
      .assertIsDisplayed()
      .assertHasClickAction()

    composeTestRule.onNodeWithContentDescription("Mesajlaşma Sekmesi")
      .assertIsDisplayed()
      .assertHasClickAction()

    composeTestRule.onNodeWithContentDescription("Arama Sekmesi")
      .assertIsDisplayed()
      .assertHasClickAction()

    composeTestRule.onNodeWithContentDescription("Denetim Logları Sekmesi")
      .assertIsDisplayed()
      .assertHasClickAction()

    // Verify Messaging send button accessibility
    composeTestRule.onNodeWithContentDescription("Mesaj Gönder")
      .assertIsDisplayed()
      .assertHasClickAction()

    // Switch to Call tab and verify call icons accessibility
    composeTestRule.onNodeWithTag("tab_call").performClick()
    composeTestRule.waitForIdle()

    composeTestRule.onNodeWithContentDescription("Sesli Arama İkonu").assertIsDisplayed()
    composeTestRule.onNodeWithContentDescription("Görüntülü Arama İkonu").assertIsDisplayed()
  }

  @Test
  fun testStateManagement_ProfileUpdate() {
    val viewModel = setupScreen(initiallyUnlocked = true)

    // Switch to Profile tab
    composeTestRule.onNodeWithTag("tab_profile").performClick()
    composeTestRule.waitForIdle()

    // Verify profile inputs exist
    composeTestRule.onNodeWithTag("nickname_input").assertIsDisplayed()
    composeTestRule.onNodeWithTag("phone_input").assertIsDisplayed()

    // Clear and enter new Nickname and Phone
    composeTestRule.onNodeWithTag("nickname_input")
      .performTextReplacement("SiberAjan_99")

    composeTestRule.onNodeWithTag("phone_input")
      .performTextReplacement("+90 532 999 8877")

    // Click save profile
    composeTestRule.onNodeWithTag("save_profile_button").performClick()
    composeTestRule.waitForIdle()

    // Verify ViewModel state updated
    assertEquals("SiberAjan_99", viewModel.uiState.value.secretNickname)
    assertEquals("+90 532 999 8877", viewModel.uiState.value.secretPhoneNumber)
  }

  @Test
  fun testStateManagement_SendMessageAndDisplaysInChatList() {
    val viewModel = setupScreen(initiallyUnlocked = true)

    // Ensure we are on Messaging tab
    composeTestRule.onNodeWithTag("tab_messaging").performClick()
    composeTestRule.waitForIdle()

    // Enter message
    val testMessage = "Gizli operasyon verisi aktarıldı."
    composeTestRule.onNodeWithTag("chat_message_input").performTextInput(testMessage)
    composeTestRule.waitForIdle()

    // Click send
    composeTestRule.onNodeWithTag("send_message_button").performClick()
    composeTestRule.waitForIdle()

    // Verify message added to ViewModel state
    val messages = viewModel.uiState.value.secretMessages
    assertTrue(messages.any { it.message == testMessage })

    // Verify message is displayed on screen
    composeTestRule.onNodeWithText(testMessage).assertIsDisplayed()
  }

  @Test
  fun testStateManagement_VoiceCallStartAndEnd() {
    val viewModel = setupScreen(initiallyUnlocked = true)

    // Navigate to Call tab
    composeTestRule.onNodeWithTag("tab_call").performClick()
    composeTestRule.waitForIdle()

    // Assert active call card is not yet visible
    assertFalse(viewModel.uiState.value.isCallActive)

    // Start Voice Call
    composeTestRule.onNodeWithTag("voice_call_button").performClick()
    composeTestRule.waitForIdle()

    // Check state and active call overlay
    assertTrue(viewModel.uiState.value.isCallActive)
    assertEquals(CallType.Audio, viewModel.uiState.value.callType)
    composeTestRule.onNodeWithTag("active_call_card").assertIsDisplayed()
    composeTestRule.onNodeWithText("Şifreli Sesli Görüşme Devam Ediyor...").assertIsDisplayed()

    // End call
    composeTestRule.onNodeWithTag("end_call_button").performClick()
    composeTestRule.waitForIdle()

    // Verify call ended in state
    assertFalse(viewModel.uiState.value.isCallActive)
    assertEquals(CallType.None, viewModel.uiState.value.callType)
  }

  @Test
  fun testStateManagement_VideoCallStartAndEnd() {
    val viewModel = setupScreen(initiallyUnlocked = true)

    // Navigate to Call tab
    composeTestRule.onNodeWithTag("tab_call").performClick()
    composeTestRule.waitForIdle()

    // Start Video Call
    composeTestRule.onNodeWithTag("video_call_button").performClick()
    composeTestRule.waitForIdle()

    // Check state and active video call display
    assertTrue(viewModel.uiState.value.isCallActive)
    assertEquals(CallType.Video, viewModel.uiState.value.callType)
    composeTestRule.onNodeWithTag("active_call_card").assertIsDisplayed()
    composeTestRule.onNodeWithText("Şifreli Görüntülü Görüşme Devam Ediyor...").assertIsDisplayed()

    // End call
    composeTestRule.onNodeWithTag("end_call_button").performClick()
    composeTestRule.waitForIdle()

    // Verify call ended in state
    assertFalse(viewModel.uiState.value.isCallActive)
  }

  @Test
  fun testSecurityUnlockFlow_StateTransitions() {
    val viewModel = setupScreen(initiallyUnlocked = false)

    // Verify login card is displayed initially
    composeTestRule.onNodeWithTag("secret_login_card").assertIsDisplayed()
    assertFalse(viewModel.uiState.value.isSecretUnlocked)

    // Enter wrong password
    composeTestRule.onNodeWithTag("secret_password_input").performTextInput("wrongpass")
    composeTestRule.onNodeWithTag("secret_login_button").performClick()
    composeTestRule.waitForIdle()

    composeTestRule.onNodeWithText("Hatalı şifre!").assertIsDisplayed()
    assertFalse(viewModel.uiState.value.isSecretUnlocked)

    // Enter correct password
    composeTestRule.onNodeWithTag("secret_password_input").performTextReplacement("admin2011")
    composeTestRule.onNodeWithTag("secret_login_button").performClick()
    composeTestRule.waitForIdle()

    // Verify unlocked and tabs appear
    composeTestRule.onNodeWithTag("secret_area_tabs").assertIsDisplayed()
    assertTrue(viewModel.uiState.value.isSecretUnlocked)
  }
}
