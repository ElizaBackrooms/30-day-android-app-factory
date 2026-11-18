package com.example

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.security.EncryptedVault
import com.example.security.HostAddressResolver
import com.example.security.VaultKeyProvider
import com.example.security.WebhookDispatcher
import com.example.viewmodel.SentryViewModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.net.InetAddress
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey

/**
 * In-memory AES-256 VaultKeyProvider for JVM Robolectric tests where AndroidKeyStore is absent.
 * Production code uses AndroidKeystoreKeyProvider exclusively.
 */
private class TestAesKeyProvider : VaultKeyProvider {
    private var key: SecretKey? = null
    var deletedCount = 0
        private set

    override fun getOrCreateKey(): SecretKey {
        val existing = key
        if (existing != null) return existing
        val gen = KeyGenerator.getInstance("AES").apply { init(256) }
        return gen.generateKey().also { key = it }
    }

    override fun deleteKey() {
        key = null
        deletedCount++
    }
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class TripwirePrivacyRobolectricTest {

    private lateinit var app: Application
    private lateinit var keyProvider: TestAesKeyProvider
    private lateinit var vault: EncryptedVault
    private var dnsInvocations = 0

    @Before
    fun setUp() {
        app = ApplicationProvider.getApplicationContext()
        app.getSharedPreferences(EncryptedVault.PREFS_NAME, Context.MODE_PRIVATE).edit().clear().commit()
        File(app.filesDir, EncryptedVault.ENCRYPTED_LOG_FILE_NAME).delete()
        keyProvider = TestAesKeyProvider()
        vault = EncryptedVault(app, keyProvider)
        dnsInvocations = 0
    }

    private fun createViewModel(): SentryViewModel {
        val spyResolver = HostAddressResolver {
            dnsInvocations++
            arrayOf(InetAddress.getByName("93.184.216.34"))
        }
        return SentryViewModel(
            application = app,
            vault = vault,
            webhookDispatcher = WebhookDispatcher(spyResolver)
        )
    }

    @Test
    fun `privacy by default starts strictly offline and in-memory with zero disk writes or network calls`() {
        val vm = createViewModel()
        val state = vm.uiState.value

        assertFalse("Save History on Phone must default to false", state.saveHistoryOnPhone)
        assertFalse("Online alerts must default to false", state.webhookConfig.enabledOptIn)
        assertFalse("Sentry must start disarmed", state.isArmed)
        assertEquals(0, dnsInvocations)
        assertFalse("Encrypted log file must not exist by default", vault.hasEncryptedLogFile())

        val prefs = app.getSharedPreferences(EncryptedVault.PREFS_NAME, Context.MODE_PRIVATE)
        assertTrue("SharedPreferences must remain empty on startup", prefs.all.isEmpty())
    }

    @Test
    fun `triggering alert in default mode stays in memory only and writes nothing to disk`() {
        val vm = createViewModel()
        vm.toggleArmed()

        // Feed a strong vibration spike exceeding default 1.25 m/s² threshold
        vm.processSample(
            x = 2.5f,
            y = 1.8f,
            z = 2.0f,
            soundDb = 45f,
            timestampMs = 10_000L,
            isSimulated = true
        )

        val state = vm.uiState.value
        assertTrue(state.isAlarmActive)
        assertEquals(1, state.alerts.size)
        assertFalse(state.alerts.first().encryptedStored)
        assertFalse("No disk file should be written while opt-in is false", vault.hasEncryptedLogFile())
        assertEquals("Zero network calls when online alerts are off", 0, dnsInvocations)
    }

    @Test
    fun `opting into local persistence encrypts data at rest and 1-tap wipe clears everything`() {
        val vm = createViewModel()
        vm.generatePrivateNtfyTopic()
        val secretTopicUrl = vm.uiState.value.webhookConfig.webhookUrl
        assertTrue(secretTopicUrl.startsWith("https://ntfy.sh/tripwire-"))

        vm.toggleArmed()
        vm.processSample(
            x = 3.2f,
            y = 0.5f,
            z = 1.5f,
            soundDb = 72f,
            timestampMs = 20_000L,
            isSimulated = true
        )

        // Opt in to encrypted local persistence
        vm.setSaveHistoryOnPhone(true)
        assertTrue(vm.uiState.value.saveHistoryOnPhone)
        assertTrue(vault.hasEncryptedLogFile())

        // Verify ciphertext on disk does NOT leak plaintext secret URL or alert reason
        val rawDiskLog = File(app.filesDir, EncryptedVault.ENCRYPTED_LOG_FILE_NAME).readText(Charsets.UTF_8)
        assertFalse(rawDiskLog.contains("Shake spike"))
        assertFalse(rawDiskLog.contains("Tripwire Pulse"))

        val prefs = app.getSharedPreferences(EncryptedVault.PREFS_NAME, Context.MODE_PRIVATE)
        val rawWebhookBlob = prefs.getString(EncryptedVault.KEY_ENCRYPTED_WEBHOOK, "").orEmpty()
        assertTrue(rawWebhookBlob.isNotBlank())
        assertFalse("Webhook URL must be encrypted in SharedPreferences", rawWebhookBlob.contains("ntfy.sh"))

        // Reloading from vault restores decrypted webhook URL and alerts
        val reloaded = vault.loadIfOptedIn()
        assertTrue(reloaded.saveHistoryOnPhone)
        assertEquals(secretTopicUrl, reloaded.webhookConfig.webhookUrl)
        assertEquals(1, reloaded.alerts.size)

        // 1-tap Wipe All Local Data clears SharedPreferences, deletes encrypted file, deletes key, resets memory
        vm.wipeAllLocalData()
        val wipedState = vm.uiState.value
        assertFalse(wipedState.saveHistoryOnPhone)
        assertFalse(wipedState.isArmed)
        assertFalse(wipedState.isAlarmActive)
        assertTrue(wipedState.alerts.isEmpty())
        assertEquals("", wipedState.webhookConfig.webhookUrl)
        assertFalse(wipedState.webhookConfig.enabledOptIn)
        assertFalse(vault.hasEncryptedLogFile())
        assertTrue(prefs.all.isEmpty())
        assertTrue(keyProvider.deletedCount >= 1)
    }

    @Test
    fun `app name string resource matches Tripwire Pulse`() {
        assertEquals("Tripwire Pulse", app.getString(R.string.app_name))
    }
}
