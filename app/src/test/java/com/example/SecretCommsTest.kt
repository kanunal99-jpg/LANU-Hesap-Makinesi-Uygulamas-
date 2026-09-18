package com.example

import android.app.Application
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.core.app.ApplicationProvider
import com.example.ui.CalculatorViewModel
import com.example.ui.CallType
import com.example.ui.CommunicationViewModel
import com.example.ui.SecretAreaScreen
import com.example.ui.theme.MyApplicationTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@Config(sdk = [36])
class SecretCommsTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private fun setupScreen(): Pair<CalculatorViewModel, CommunicationViewModel> {
        val context = ApplicationProvider.getApplicationContext<Application>()
        val calcViewModel = CalculatorViewModel(context)
        val commViewModel = CommunicationViewModel(context)

        composeTestRule.setContent {
            MyApplicationTheme {
                SecretAreaScreen(
                    calculatorViewModel = calcViewModel,
                    onBack = {},
                    communicationViewModel = commViewModel
                )
            }
        }
        composeTestRule.waitForIdle()
        return Pair(calcViewModel, commViewModel)
    }

    @Test
    fun testAccessibility_ContentDescriptionsExistForNavigationAndActions() {
        setupScreen()

        // Verify Tab accessibility content descriptions exist
        composeTestRule.onNodeWithContentDescription("Profil Sekmesi").assertExists()
        composeTestRule.onNodeWithContentDescription("Mesajlaşma Sekmesi").assertExists()
        composeTestRule.onNodeWithContentDescription("Arama Sekmesi").assertExists()
        composeTestRule.onNodeWithContentDescription("Denetim Logları Sekmesi").assertExists()

        // Switch to Call tab via text node click (extra resilient in Robolectric)
        composeTestRule.onNodeWithText("Arama").assertExists().performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithContentDescription("Sesli Arama İkonu").assertExists()
        composeTestRule.onNodeWithContentDescription("Görüntülü Arama İkonu").assertExists()
    }

    @Test
    fun testStateManagement_ProfileUpdate() {
        val (_, commViewModel) = setupScreen()

        // Switch to Profile tab
        composeTestRule.onNodeWithText("Profil").assertExists().performClick()
        composeTestRule.waitForIdle()

        // Verify profile inputs exist
        composeTestRule.onNodeWithTag("nickname_input").assertExists()
        composeTestRule.onNodeWithTag("phone_input").assertExists()

        // Clear and enter new Nickname and Phone
        composeTestRule.onNodeWithTag("nickname_input")
            .performTextReplacement("SiberAjan_99")

        composeTestRule.onNodeWithTag("phone_input")
            .performTextReplacement("+90 532 999 8877")

        // Click save profile
        composeTestRule.onNodeWithTag("save_profile_button").assertExists().performClick()
        composeTestRule.waitForIdle()

        // Verify state is updated directly and immediately in the ViewModel
        assertEquals("SiberAjan_99", commViewModel.uiState.value.secretNickname)
        assertEquals("+90 532 999 8877", commViewModel.uiState.value.secretPhoneNumber)
    }

    @Test
    fun testStateManagement_SendMessageAndDisplaysInChatList() {
        val (_, commViewModel) = setupScreen()

        // Ensure we are on Messaging tab
        composeTestRule.onNodeWithText("Mesajlaşma").assertExists().performClick()
        composeTestRule.waitForIdle()

        // Enter message
        val testMessage = "Gizli operasyon verisi aktarıldı."
        composeTestRule.onNodeWithTag("chat_message_input").performTextInput(testMessage)
        composeTestRule.waitForIdle()

        // Click send
        composeTestRule.onNodeWithTag("send_message_button").assertExists().performClick()
        composeTestRule.waitForIdle()

        // Verify message added to ViewModel state
        val messages = commViewModel.uiState.value.messages
        assertTrue(messages.any { it.message == testMessage })

        // Verify message exists in Compose tree
        composeTestRule.onNodeWithText(testMessage).assertExists()
    }

    @Test
    fun testStateManagement_VoiceCallStartAndEnd() {
        val (_, commViewModel) = setupScreen()

        // Navigate to Call tab with safe text click assertion
        composeTestRule.onNodeWithText("Arama").assertExists().performClick()
        composeTestRule.waitForIdle()

        // Assert active call card is not yet visible
        assertFalse(commViewModel.uiState.value.isCallActive)

        // Start Voice Call with safe assertion
        composeTestRule.onNodeWithTag("voice_call_button").assertExists().performClick()
        composeTestRule.waitForIdle()

        // Check state and active call overlay
        assertTrue(commViewModel.uiState.value.isCallActive)
        assertEquals(CallType.Audio, commViewModel.uiState.value.callType)
        composeTestRule.onNodeWithTag("active_call_card").assertExists()

        // End Call
        composeTestRule.onNodeWithTag("end_call_button").assertExists().performClick()
        composeTestRule.waitForIdle()

        // Assert active call card is gone and status reset
        assertFalse(commViewModel.uiState.value.isCallActive)
        assertEquals(CallType.None, commViewModel.uiState.value.callType)
    }
}
