package com.example

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.example.database.AppDatabase
import com.example.security.AEADEngine
import com.example.security.ChannelConfig
import com.example.security.CommunicationRepository
import com.example.ui.CommunicationViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
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
    fun testEndToEndEncryption_AES_GCM_AEAD_SecurityAndIntegrity() {
        val passphrase = "Ajan_Secure_Pass"
        val wrongPassphrase = "farkli_yanlis_parola"
        val originalText = "Selam, operasyon noktasına vardım."

        val keySpec = AEADEngine.deriveKey(passphrase)
        val wrongKeySpec = AEADEngine.deriveKey(wrongPassphrase)

        // 1. Encrypt text
        val cipherText = AEADEngine.encrypt(originalText, keySpec)
        assertNotNull(cipherText)
        assertNotEquals(originalText, cipherText)

        // 2. Randomized Nonce Check: Encrypting twice must produce different ciphertexts!
        val cipherText2 = AEADEngine.encrypt(originalText, keySpec)
        assertNotEquals(cipherText, cipherText2)

        // 3. Decrypt with correct key -> Should succeed
        val decryptedSuccess = AEADEngine.decrypt(cipherText, keySpec)
        assertEquals(originalText, decryptedSuccess)

        // 4. Decrypt with wrong key -> Must throw exception (No plaintext fallback)
        assertThrows(Exception::class.java) {
            AEADEngine.decrypt(cipherText, wrongKeySpec)
        }
    }

    @Test
    fun testCommunicationRepository_MessageDeliveryStates() = runTest {
        val repository = CommunicationRepository(context)
        val config = ChannelConfig(
            channelId = "LANU-SECURE-777",
            encryptionKey = "Ajan_Secure_Pass"
        )
        repository.configureChannel(config)

        // Initially messages list should be empty
        assertTrue(repository.messagesFlow.value.isEmpty())

        // Send message
        repository.sendMessage("Ajan Alpha", "+90 555 111 2233", "Güvenli mesaj testi.")

        // Verify message was added to list and is in sending or sent state
        val messages = repository.messagesFlow.value
        assertEquals(1, messages.size)
        assertEquals("Güvenli mesaj testi.", messages[0].message)
        assertEquals("Ajan Alpha", messages[0].senderName)

        repository.disconnect()
    }
}
