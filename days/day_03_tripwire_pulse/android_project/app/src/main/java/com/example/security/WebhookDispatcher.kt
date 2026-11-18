package com.example.security

import com.example.model.WebhookDeliveryStatus
import com.example.model.WebhookProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.net.URL
import javax.net.ssl.HttpsURLConnection

data class WebhookSendResult(
    val status: WebhookDeliveryStatus,
    val httpCode: Int?,
    val message: String,
    val payloadJson: String
)

/**
 * Pure AOSP HTTPS webhook dispatcher using javax.net.ssl.HttpsURLConnection on Dispatchers.IO
 * and org.json.JSONObject.
 *
 * Hardening guarantees:
 * - https:// only, verified via WebhookUrlValidator (SSRF guard) before every call
 * - connectTimeout = 10_000 ms, readTimeout = 10_000 ms
 * - instanceFollowRedirects = false, useCaches = false
 * - 64 KB response cap
 * - Default platform TLS verification only (zero TrustManager / HostnameVerifier bypasses)
 * - Zero inbound listening sockets
 * - Never logs URLs, tokens, or payloads via android.util.Log
 */
class WebhookDispatcher(
    private val resolver: HostAddressResolver = WebhookUrlValidator.defaultResolver
) {
    companion object {
        const val CONNECT_TIMEOUT_MS = 10_000
        const val READ_TIMEOUT_MS = 10_000
        const val MAX_RESPONSE_BYTES = 65_536 // 64 KB response cap
    }

    fun buildAlertPayloadJson(
        provider: WebhookProvider,
        label: String,
        reason: String,
        shakeDeltaMs2: Float,
        soundDb: Float,
        isSimulated: Boolean,
        timestampMs: Long
    ): String {
        val safeLabel = WebhookUrlValidator.sanitizePayloadText(label.ifBlank { "Tripwire Pulse" }, 60)
        val safeReason = WebhookUrlValidator.sanitizePayloadText(reason, 160)
        val modeTag = if (isSimulated) "Test Mode" else "Live Sentry"
        val summaryLine = "$safeLabel [$modeTag]: $safeReason"

        return when (provider) {
            WebhookProvider.DISCORD -> {
                JSONObject().apply {
                    put("content", WebhookUrlValidator.sanitizePayloadText(summaryLine, 200))
                }.toString()
            }
            WebhookProvider.NTFY, WebhookProvider.CUSTOM_HTTPS -> {
                JSONObject().apply {
                    put("app", "Tripwire Pulse")
                    put("label", safeLabel)
                    put("mode", modeTag)
                    put("reason", safeReason)
                    put("shake_delta_ms2", String.format(java.util.Locale.US, "%.2f", shakeDeltaMs2))
                    put("sound_db", soundDb.toInt())
                    put("timestamp_ms", timestampMs)
                }.toString()
            }
        }
    }

    suspend fun sendAlert(
        rawUrl: String,
        provider: WebhookProvider,
        label: String,
        reason: String,
        shakeDeltaMs2: Float,
        soundDb: Float,
        isSimulated: Boolean,
        timestampMs: Long
    ): WebhookSendResult = withContext(Dispatchers.IO) {
        val payloadJson = buildAlertPayloadJson(
            provider = provider,
            label = label,
            reason = reason,
            shakeDeltaMs2 = shakeDeltaMs2,
            soundDb = soundDb,
            isSimulated = isSimulated,
            timestampMs = timestampMs
        )

        val validation = WebhookUrlValidator.validateWithDns(rawUrl, resolver)
        if (validation is UrlValidationResult.Invalid) {
            return@withContext WebhookSendResult(
                status = WebhookDeliveryStatus.BLOCKED_INVALID_URL,
                httpCode = null,
                message = validation.reason,
                payloadJson = payloadJson
            )
        }

        val validUrl = (validation as UrlValidationResult.Valid).normalizedUrl
        var connection: HttpsURLConnection? = null
        try {
            val url = URL(validUrl)
            val opened = url.openConnection()
            if (opened !is HttpsURLConnection) {
                return@withContext WebhookSendResult(
                    status = WebhookDeliveryStatus.BLOCKED_INVALID_URL,
                    httpCode = null,
                    message = "Connection refused: HTTPS is required.",
                    payloadJson = payloadJson
                )
            }
            connection = opened
            connection.requestMethod = "POST"
            connection.connectTimeout = CONNECT_TIMEOUT_MS
            connection.readTimeout = READ_TIMEOUT_MS
            connection.instanceFollowRedirects = false
            connection.useCaches = false
            connection.doOutput = true

            val bodyBytes = when (provider) {
                WebhookProvider.NTFY -> {
                    val safeTitle = WebhookUrlValidator.sanitizePayloadText(
                        label.ifBlank { "Tripwire Pulse Alert" },
                        64
                    )
                    val safeBody = WebhookUrlValidator.sanitizePayloadText(
                        "${if (isSimulated) "[TEST] " else ""}$reason",
                        200
                    )
                    connection.setRequestProperty("Content-Type", "text/plain; charset=utf-8")
                    connection.setRequestProperty("Title", safeTitle)
                    connection.setRequestProperty("Priority", if (isSimulated) "default" else "high")
                    safeBody.toByteArray(Charsets.UTF_8)
                }
                WebhookProvider.DISCORD, WebhookProvider.CUSTOM_HTTPS -> {
                    connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")
                    payloadJson.toByteArray(Charsets.UTF_8)
                }
            }

            connection.setRequestProperty("Accept", "application/json, text/plain")
            connection.setRequestProperty("User-Agent", "TripwirePulse/1.0")
            connection.setFixedLengthStreamingMode(bodyBytes.size)

            connection.outputStream.use { out ->
                out.write(bodyBytes)
                out.flush()
            }

            val code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            if (stream != null) {
                readBounded(stream, MAX_RESPONSE_BYTES)
            }

            if (code in 200..299) {
                WebhookSendResult(
                    status = WebhookDeliveryStatus.SENT,
                    httpCode = code,
                    message = "Alert delivered safely (HTTP $code).",
                    payloadJson = payloadJson
                )
            } else if (code in 300..399) {
                WebhookSendResult(
                    status = WebhookDeliveryStatus.FAILED,
                    httpCode = code,
                    message = "Server tried to redirect (HTTP $code). Redirects are blocked for safety.",
                    payloadJson = payloadJson
                )
            } else {
                WebhookSendResult(
                    status = WebhookDeliveryStatus.FAILED,
                    httpCode = code,
                    message = "Alert server returned HTTP $code.",
                    payloadJson = payloadJson
                )
            }
        } catch (_: javax.net.ssl.SSLException) {
            WebhookSendResult(
                status = WebhookDeliveryStatus.FAILED,
                httpCode = null,
                message = "TLS security check failed. Certificate could not be trusted.",
                payloadJson = payloadJson
            )
        } catch (_: java.net.SocketTimeoutException) {
            WebhookSendResult(
                status = WebhookDeliveryStatus.FAILED,
                httpCode = null,
                message = "Timed out after 10 seconds waiting for server.",
                payloadJson = payloadJson
            )
        } catch (_: Exception) {
            WebhookSendResult(
                status = WebhookDeliveryStatus.FAILED,
                httpCode = null,
                message = "Could not reach alert server (offline or unreachable).",
                payloadJson = payloadJson
            )
        } finally {
            connection?.disconnect()
        }
    }

    private fun readBounded(input: InputStream, maxBytes: Int): ByteArray {
        input.use { stream ->
            val out = ByteArrayOutputStream()
            val buf = ByteArray(4096)
            var remaining = maxBytes
            while (remaining > 0) {
                val read = stream.read(buf, 0, minOf(buf.size, remaining))
                if (read <= 0) break
                out.write(buf, 0, read)
                remaining -= read
            }
            return out.toByteArray()
        }
    }
}
