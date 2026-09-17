package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.security.SecretManager
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class SecretTransitionTest {

  @Test
  fun testSecretSequence2011() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val secretManager = SecretManager(context)

    secretManager.resetBuffer()
    assertFalse(secretManager.feedInput('2'))
    assertFalse(secretManager.feedInput('0'))
    assertFalse(secretManager.feedInput('1'))
    assertFalse(secretManager.feedInput('1'))
    assertTrue(secretManager.feedInput('.'))
  }

  @Test
  fun testIncorrectSequences() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val secretManager = SecretManager(context)

    // "2011" without dot
    secretManager.resetBuffer()
    secretManager.feedInput('2')
    secretManager.feedInput('0')
    secretManager.feedInput('1')
    assertFalse(secretManager.feedInput('1'))

    // "02011."
    secretManager.resetBuffer()
    secretManager.feedInput('0')
    secretManager.feedInput('2')
    secretManager.feedInput('0')
    secretManager.feedInput('1')
    secretManager.feedInput('1')
    assertFalse(secretManager.feedInput('.'))
  }
}
