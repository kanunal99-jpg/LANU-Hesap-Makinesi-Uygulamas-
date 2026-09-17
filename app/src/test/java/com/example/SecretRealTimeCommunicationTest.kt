package com.example

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.example.database.AppDatabase
import com.example.database.SecretMessageEntity
import com.example.security.ChannelConfig
import com.example.security.SecretNetworkEngine
import com.example.ui.CalculatorViewModel
import com.example.ui.CallType
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class SecretRealTimeCommunicationTest {

  private lateinit var context: Application
  private lateinit var database: AppDatabase

  @Before
  fun setup() {
    context = ApplicationProvider.getApplicationContext()
    database = AppDatabase.getDatabase(context)
    database.clearAllTables()
  }

  @Test
  fun testEndToEndEncryption_AES256_CorrectKeySucceeds_WrongKeyFails() {
    val testScope = TestScope(StandardTestDispatcher())
    val engine = SecretNetworkEngine(context, testScope)

    val keyA = "gizli_parola_123"
    val keyB = "farkli_yanlis_parola"
    val originalText = "Toplantı saat 18:00'da güvenli noktada yapılacak."

    // Encrypt with keyA
    val encrypted = engine.encrypt(originalText, keyA)
    assertNotNull(encrypted)
    assertNotEquals(originalText, encrypted)

    // Decrypt with keyA (Same key) -> Should succeed
    val decryptedSuccess = engine.decrypt(encrypted, keyA)
    assertEquals(originalText, decryptedSuccess)

    // Decrypt with keyB (Wrong key) -> Should fail to decrypt
    val decryptedFail = engine.decrypt(encrypted, keyB)
    assertEquals("[Şifre Çözülemedi - Anahtar Uyuşmazlığı]", decryptedFail)
  }

  @Test
  fun testTwoPersonMessageExchange_Simulation() = runTest {
    val testScope = this
    val engineA = SecretNetworkEngine(context, testScope)
    val engineB = SecretNetworkEngine(context, testScope)

    val sharedSecret = "admin2011"

    // Track messages received on Device B
    val bReceivedMessages = mutableListOf<Triple<String, String, String>>()
    engineB.onMessageReceivedListener = { sender, phone, text, _ ->
      bReceivedMessages.add(Triple(sender, phone, text))
    }

    // Device A crafts an encrypted message packet
    val messageText = "Selam, operasyon noktasına vardım."
    val senderNameA = "Ajan Alpha"
    val senderPhoneA = "+90 555 111 2233"

    val encryptedPayload = engineA.encrypt(messageText, sharedSecret)
    val packetToSend = JSONObject().apply {
      put("type", "CHAT")
      put("senderId", "device-A-id")
      put("sender", senderNameA)
      put("phone", senderPhoneA)
      put("payload", encryptedPayload)
      put("time", System.currentTimeMillis())
    }.toString()

    // Device B receives the packet over the wire
    engineB.handlePayloadJson(packetToSend, sharedSecret)

    // Verify Device B decoded and processed the message correctly
    assertEquals(1, bReceivedMessages.size)
    assertEquals(senderNameA, bReceivedMessages[0].first)
    assertEquals(senderPhoneA, bReceivedMessages[0].second)
    assertEquals(messageText, bReceivedMessages[0].third)
  }

  @Test
  fun testViewModel_MultiPersonChatAndPersistence() = runTest {
    val viewModel = CalculatorViewModel(context)
    viewModel.verifySecretPassword("admin2011")
    viewModel.updateSecretProfile("Ajan Beta", "+90 555 987 6543")

    // User A sends outgoing message
    viewModel.sendSecretMessage("Merhaba, güvenli hat aktif mi?")
    val messagesAfterSend = viewModel.uiState.value.secretMessages
    assertTrue(messagesAfterSend.any { it.message == "Merhaba, güvenli hat aktif mi?" && it.isMe })

    // Simulate incoming message packet from User B ("Ajan Gamma")
    val sharedSecret = viewModel.uiState.value.channelConfig.encryptionKey
    val encryptedPayload = viewModel.networkEngine.encrypt("Evet hat aktif, seni duyabiliyorum.", sharedSecret)
    val packet = JSONObject().apply {
      put("type", "CHAT")
      put("senderId", "remote-user-gamma")
      put("sender", "Ajan Gamma")
      put("phone", "+90 555 333 4455")
      put("payload", encryptedPayload)
      put("time", System.currentTimeMillis())
    }.toString()

    // Pass packet into network engine
    viewModel.networkEngine.handlePayloadJson(packet, sharedSecret)

    val updatedMessages = viewModel.uiState.value.secretMessages
    val incomingMsg = updatedMessages.find { it.message == "Evet hat aktif, seni duyabiliyorum." }
    assertNotNull(incomingMsg)
    assertEquals("Ajan Gamma", incomingMsg?.senderName)
    assertFalse(incomingMsg?.isMe ?: true)

    // Verify messages saved in Room DB
    val savedDao = database.secretMessageDao()
    val savedInDb = savedDao.getMessagesForChannel(viewModel.uiState.value.channelConfig.channelId)
    assertTrue(savedInDb.size >= 2)
  }

  @Test
  fun testIncomingCallSignalingFlow() = runTest {
    val viewModel = CalculatorViewModel(context)
    viewModel.verifySecretPassword("admin2011")

    // Assert no incoming call initially
    assertNull(viewModel.uiState.value.incomingCall)
    assertFalse(viewModel.uiState.value.isCallActive)

    // Remote peer sends CALL_OFFER packet
    val sharedSecret = viewModel.uiState.value.channelConfig.encryptionKey
    val packet = JSONObject().apply {
      put("type", "CALL_OFFER")
      put("senderId", "remote-commander")
      put("callerName", "Merkez Komuta")
      put("callerPhone", "+90 850 000 0000")
      put("isVideo", true)
      put("time", System.currentTimeMillis())
    }.toString()

    // Handle packet
    viewModel.networkEngine.handlePayloadJson(packet, sharedSecret)

    // Verify incoming call signal state
    val incoming = viewModel.uiState.value.incomingCall
    assertNotNull(incoming)
    assertEquals("Merkez Komuta", incoming?.callerName)
    assertTrue(incoming?.isVideo == true)

    // User accepts call
    viewModel.acceptIncomingCall()

    // Verify call is now active and in video mode
    assertTrue(viewModel.uiState.value.isCallActive)
    assertEquals(CallType.Video, viewModel.uiState.value.callType)
    assertNull(viewModel.uiState.value.incomingCall)

    // End call
    viewModel.endCall()
    assertFalse(viewModel.uiState.value.isCallActive)
  }
}
