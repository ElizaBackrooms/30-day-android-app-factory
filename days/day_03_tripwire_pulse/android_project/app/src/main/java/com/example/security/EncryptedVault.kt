package com.example.security

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import com.example.model.AlertEvent
import com.example.model.WebhookConfig
import com.example.model.WebhookDeliveryStatus
import com.example.model.WebhookProvider
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

interface VaultKeyProvider {
    fun getOrCreateKey(): SecretKey
    fun deleteKey()
}

/**
 * Production key provider backed by hardware/OS AndroidKeyStore (AES-256 / GCM / NoPadding).
 */
class AndroidKeystoreKeyProvider(
    private val alias: String = DEFAULT_KEY_ALIAS
) : VaultKeyProvider {
    companion object {
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val DEFAULT_KEY_ALIAS = "tripwire_pulse_vault_aes256"
    }

    override fun getOrCreateKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        val existing = keyStore.getKey(alias, null) as? SecretKey
        if (existing != null) return existing

        val keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        val spec = KeyGenParameterSpec.Builder(
            alias,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .setRandomizedEncryptionRequired(true)
            .build()

        keyGenerator.init(spec)
        return keyGenerator.generateKey()
    }

    override fun deleteKey() {
        try {
            val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
            if (keyStore.containsAlias(alias)) {
                keyStore.deleteEntry(alias)
            }
        } catch (_: Exception) {
            // Best-effort cleanup
        }
    }
}

data class VaultSnapshot(
    val saveHistoryOnPhone: Boolean,
    val alerts: List<AlertEvent>,
    val webhookConfig: WebhookConfig
)

/**
 * Privacy-by-Default encrypted local vault.
 *
 * - Writes NOTHING to disk until the user explicitly opts in via "Save History on Phone (Opt-In)".
 * - Encrypts both alert payload logs and webhook URL/credentials using AES/GCM/NoPadding
 *   stored strictly in Context.MODE_PRIVATE app files and SharedPreferences.
 * - Never logs secrets or decrypted payloads.
 */
class EncryptedVault(
    private val context: Context,
    private val keyProvider: VaultKeyProvider = AndroidKeystoreKeyProvider()
) {
    companion object {
        const val PREFS_NAME = "tripwire_pulse_private_prefs"
        const val KEY_OPT_IN_SAVE_HISTORY = "opt_in_save_history"
        const val KEY_ENCRYPTED_WEBHOOK = "enc_webhook_blob"
        const val ENCRYPTED_LOG_FILE_NAME = "tripwire_alert_vault.enc"
        private const val CIPHER_TRANSFORMATION = "AES/GCM/NoPadding"
        private const val GCM_TAG_BITS = 128
        private const val IV_LENGTH_BYTES = 12
        private const val MAX_STORED_ALERTS = 100
    }

    private val prefsFile: File
        get() = File(context.filesDir, ENCRYPTED_LOG_FILE_NAME)

    private fun openPrefs() = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /**
     * Reads opted-in encrypted vault ONLY if the user previously opted in.
     * On first run (or after wipe), performs zero disk writes and returns Privacy-by-Default state.
     */
    fun loadIfOptedIn(): VaultSnapshot {
        val prefs = openPrefs()
        val optedIn = prefs.getBoolean(KEY_OPT_IN_SAVE_HISTORY, false)
        if (!optedIn) {
            return VaultSnapshot(
                saveHistoryOnPhone = false,
                alerts = emptyList(),
                webhookConfig = WebhookConfig()
            )
        }

        val webhookConfig = try {
            val blob = prefs.getString(KEY_ENCRYPTED_WEBHOOK, null)
            if (!blob.isNullOrEmpty()) {
                val jsonText = decryptString(blob)
                parseWebhookConfig(jsonText)
            } else {
                WebhookConfig()
            }
        } catch (_: Exception) {
            WebhookConfig()
        }

        val alerts = try {
            if (prefsFile.exists()) {
                val encryptedText = prefsFile.readText(Charsets.UTF_8)
                if (encryptedText.isNotBlank()) {
                    val jsonText = decryptString(encryptedText)
                    parseAlerts(jsonText)
                } else {
                    emptyList()
                }
            } else {
                emptyList()
            }
        } catch (_: Exception) {
            emptyList()
        }

        return VaultSnapshot(
            saveHistoryOnPhone = true,
            alerts = alerts,
            webhookConfig = webhookConfig
        )
    }

    /**
     * Persists opted-in flag, encrypted alert payload log, and encrypted webhook config.
     * If optIn is false, removes local files & SharedPreferences immediately.
     */
    fun saveState(
        optIn: Boolean,
        alerts: List<AlertEvent>,
        webhookConfig: WebhookConfig
    ): Boolean {
        if (!optIn) {
            clearEncryptedFilesAndPrefsOnly()
            return true
        }

        return try {
            val prefs = openPrefs()
            val encryptedWebhook = encryptString(serializeWebhookConfig(webhookConfig))
            prefs.edit()
                .putBoolean(KEY_OPT_IN_SAVE_HISTORY, true)
                .putString(KEY_ENCRYPTED_WEBHOOK, encryptedWebhook)
                .apply()

            val boundedAlerts = alerts.take(MAX_STORED_ALERTS)
            val encryptedLog = encryptString(serializeAlerts(boundedAlerts))
            context.openFileOutput(ENCRYPTED_LOG_FILE_NAME, Context.MODE_PRIVATE).use { out ->
                out.write(encryptedLog.toByteArray(Charsets.UTF_8))
                out.flush()
            }
            true
        } catch (_: Exception) {
            false
        }
    }

    /**
     * 1-tap full wipe: clears SharedPreferences, deletes encrypted alert log file,
     * and deletes Keystore key alias.
     */
    fun wipeAll() {
        clearEncryptedFilesAndPrefsOnly()
        keyProvider.deleteKey()
    }

    private fun clearEncryptedFilesAndPrefsOnly() {
        openPrefs().edit().clear().apply()
        if (prefsFile.exists()) {
            prefsFile.delete()
        }
    }

    fun hasEncryptedLogFile(): Boolean = prefsFile.exists() && prefsFile.length() > 0L

    fun encryptString(plaintext: String): String {
        val key = keyProvider.getOrCreateKey()
        val cipher = Cipher.getInstance(CIPHER_TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, key)
        val iv = cipher.iv
        val ciphertext = cipher.doFinal(plaintext.toByteArray(Charsets.UTF_8))

        val combined = ByteArray(iv.size + ciphertext.size)
        System.arraycopy(iv, 0, combined, 0, iv.size)
        System.arraycopy(ciphertext, 0, combined, iv.size, ciphertext.size)
        return Base64.encodeToString(combined, Base64.NO_WRAP)
    }

    fun decryptString(encoded: String): String {
        val combined = Base64.decode(encoded, Base64.NO_WRAP)
        require(combined.size > IV_LENGTH_BYTES) { "Malformed encrypted payload" }
        val iv = combined.copyOfRange(0, IV_LENGTH_BYTES)
        val ciphertext = combined.copyOfRange(IV_LENGTH_BYTES, combined.size)

        val key = keyProvider.getOrCreateKey()
        val cipher = Cipher.getInstance(CIPHER_TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(GCM_TAG_BITS, iv))
        val plaintext = cipher.doFinal(ciphertext)
        return String(plaintext, Charsets.UTF_8)
    }

    private fun serializeWebhookConfig(config: WebhookConfig): String {
        return JSONObject().apply {
            put("enabledOptIn", config.enabledOptIn)
            put("provider", config.provider.name)
            put("webhookUrl", config.webhookUrl)
            put("customLabel", config.customLabel)
        }.toString()
    }

    private fun parseWebhookConfig(jsonText: String): WebhookConfig {
        val obj = JSONObject(jsonText)
        val providerName = obj.optString("provider", WebhookProvider.NTFY.name)
        val provider = runCatching { WebhookProvider.valueOf(providerName) }
            .getOrDefault(WebhookProvider.NTFY)
        return WebhookConfig(
            enabledOptIn = obj.optBoolean("enabledOptIn", false),
            provider = provider,
            webhookUrl = obj.optString("webhookUrl", ""),
            customLabel = obj.optString("customLabel", "Front Door Sentry")
        )
    }

    private fun serializeAlerts(alerts: List<AlertEvent>): String {
        val arr = JSONArray()
        for (event in alerts) {
            arr.put(
                JSONObject().apply {
                    put("id", event.id)
                    put("timestampMs", event.timestampMs)
                    put("reason", event.reason)
                    put("shakeDeltaMs2", event.shakeDeltaMs2.toDouble())
                    put("soundDb", event.soundDb.toDouble())
                    put("isSimulated", event.isSimulated)
                    put("webhookStatus", event.webhookStatus.name)
                    put("webhookStatusNote", event.webhookStatusNote)
                    put("encryptedStored", true)
                    put("payloadJson", event.payloadJson)
                }
            )
        }
        return arr.toString()
    }

    private fun parseAlerts(jsonText: String): List<AlertEvent> {
        val arr = JSONArray(jsonText)
        val list = ArrayList<AlertEvent>(arr.length())
        for (i in 0 until arr.length()) {
            val obj = arr.getJSONObject(i)
            val statusName = obj.optString("webhookStatus", WebhookDeliveryStatus.OFFLINE_ONLY.name)
            val status = runCatching { WebhookDeliveryStatus.valueOf(statusName) }
                .getOrDefault(WebhookDeliveryStatus.OFFLINE_ONLY)
            list.add(
                AlertEvent(
                    id = obj.optString("id", "evt_$i"),
                    timestampMs = obj.optLong("timestampMs", 0L),
                    reason = obj.optString("reason", "Movement detected"),
                    shakeDeltaMs2 = obj.optDouble("shakeDeltaMs2", 0.0).toFloat(),
                    soundDb = obj.optDouble("soundDb", 0.0).toFloat(),
                    isSimulated = obj.optBoolean("isSimulated", false),
                    webhookStatus = status,
                    webhookStatusNote = obj.optString("webhookStatusNote", ""),
                    encryptedStored = true,
                    payloadJson = obj.optString("payloadJson", "{}")
                )
            )
        }
        return list
    }
}
