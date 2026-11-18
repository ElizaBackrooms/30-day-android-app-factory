package com.example.network

import com.example.dsp.PacketProtocol
import java.io.BufferedInputStream
import java.io.ByteArrayOutputStream
import java.net.Inet4Address
import java.net.Inet6Address
import java.net.InetAddress
import java.net.URL
import java.nio.charset.StandardCharsets
import java.security.SecureRandom
import java.util.Locale
import javax.net.ssl.HttpsURLConnection
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

object SsrfGuard {
  private val blockedHostSuffixes = listOf(".local", ".internal", ".localhost", ".arpa")

  fun validateHttpsUrl(rawUrl: String): Result<URL> {
    val trimmed = rawUrl.trim()
    if (trimmed.isEmpty()) {
      return Result.failure(IllegalArgumentException("Enter an HTTPS relay URL first."))
    }
    if (trimmed.length > 512) {
      return Result.failure(IllegalArgumentException("Relay URL exceeds 512 characters."))
    }
    if (trimmed.any { it.isWhitespace() || it.code < 0x20 || it.code == 0x7F }) {
      return Result.failure(IllegalArgumentException("Relay URL contains whitespace or control characters."))
    }

    val url =
      try {
        URL(trimmed)
      } catch (_: Throwable) {
        return Result.failure(IllegalArgumentException("Invalid URL format."))
      }

    if (!url.protocol.equals("https", ignoreCase = true)) {
      return Result.failure(IllegalArgumentException("Only HTTPS URLs are allowed."))
    }
    if (!url.userInfo.isNullOrEmpty()) {
      return Result.failure(IllegalArgumentException("Credentials inside URLs are blocked."))
    }

    val hostLower = url.host.orEmpty().trim().trim('[', ']').lowercase(Locale.US)
    if (hostLower.isEmpty() || hostLower == "localhost" || blockedHostSuffixes.any { hostLower.endsWith(it) }) {
      return Result.failure(IllegalArgumentException("Local or internal hostnames are blocked."))
    }

    val resolved =
      try {
        InetAddress.getAllByName(hostLower)
      } catch (_: Throwable) {
        return Result.failure(IllegalArgumentException("Could not resolve host '$hostLower'."))
      }

    if (resolved.isEmpty()) {
      return Result.failure(IllegalArgumentException("Host resolved to no addresses."))
    }

    for (addr in resolved) {
      if (isBlockedAddress(addr)) {
        return Result.failure(
          IllegalArgumentException("Blocked private, loopback, link-local, or reserved IP target.")
        )
      }
    }

    return Result.success(url)
  }

  internal fun isBlockedAddress(addr: InetAddress): Boolean {
    if (
      addr.isAnyLocalAddress ||
        addr.isLoopbackAddress ||
        addr.isLinkLocalAddress ||
        addr.isSiteLocalAddress ||
        addr.isMulticastAddress
    ) {
      return true
    }

    return when (addr) {
      is Inet4Address -> isBlockedIpv4Bytes(addr.address)
      is Inet6Address -> isBlockedIpv6Bytes(addr.address)
      else -> true
    }
  }

  private fun isBlockedIpv4Bytes(bytes: ByteArray): Boolean {
    if (bytes.size != 4) return true
    val b0 = bytes[0].toInt() and 0xFF
    val b1 = bytes[1].toInt() and 0xFF
    return when {
      b0 == 0 -> true // 0.0.0.0/8 unspecified
      b0 == 10 -> true // 10.0.0.0/8 RFC-1918
      b0 == 100 && (b1 in 64..127) -> true // 100.64.0.0/10 CGNAT
      b0 == 127 -> true // 127.0.0.0/8 loopback
      b0 == 169 && b1 == 254 -> true // 169.254.0.0/16 link-local
      b0 == 172 && (b1 in 16..31) -> true // 172.16.0.0/12 RFC-1918
      b0 == 192 && b1 == 168 -> true // 192.168.0.0/16 RFC-1918
      b0 == 198 && (b1 in 18..19) -> true // 198.18.0.0/15 benchmarking
      b0 >= 224 -> true // multicast & reserved 224.0.0.0/3
      else -> false
    }
  }

  private fun isBlockedIpv6Bytes(bytes: ByteArray): Boolean {
    if (bytes.size != 16) return true
    // All-zero :: or ::1
    val allZeroPrefix15 = (0 until 15).all { bytes[it].toInt() == 0 }
    if (allZeroPrefix15 && (bytes[15].toInt() == 0 || bytes[15].toInt() == 1)) return true

    val b0 = bytes[0].toInt() and 0xFF
    val b1 = bytes[1].toInt() and 0xFF
    // fc00::/7 Unique Local Addresses
    if ((b0 and 0xFE) == 0xFC) return true
    // fe80::/10 Link-Local
    if (b0 == 0xFE && (b1 and 0xC0) == 0x80) return true
    // ff00::/8 Multicast
    if (b0 == 0xFF) return true

    // IPv4-mapped IPv6 ::ffff:0:0/96
    val isV4Mapped =
      (0 until 10).all { bytes[it].toInt() == 0 } &&
        (bytes[10].toInt() and 0xFF) == 0xFF &&
        (bytes[11].toInt() and 0xFF) == 0xFF
    if (isV4Mapped) {
      val v4 = byteArrayOf(bytes[12], bytes[13], bytes[14], bytes[15])
      if (isBlockedIpv4Bytes(v4)) return true
    }

    return false
  }
}

object OnlineRelayClient {
  private const val CONNECT_TIMEOUT_MS = 10_000
  private const val READ_TIMEOUT_MS = 10_000
  private const val MAX_RESPONSE_BYTES = 64 * 1024
  private val secureRandom = SecureRandom()

  fun generateRandomTopicUrl(): String {
    val bytes = ByteArray(16)
    secureRandom.nextBytes(bytes)
    val token = bytes.joinToString("") { "%02x".format(it.toInt() and 0xFF) }
    return "https://ntfy.sh/deaddrop-$token"
  }

  suspend fun sendPacketOnline(
    rawUrl: String,
    callsign: String,
    packetType: String,
    payloadText: String,
    crcHex: String,
  ): Result<String> =
    withContext(Dispatchers.IO) {
      val validatedUrl = SsrfGuard.validateHttpsUrl(rawUrl).getOrElse { return@withContext Result.failure(it) }
      var conn: HttpsURLConnection? = null
      try {
        conn =
          (validatedUrl.openConnection() as? HttpsURLConnection)
            ?: return@withContext Result.failure(IllegalStateException("HTTPS connection required"))

        conn.requestMethod = "POST"
        conn.connectTimeout = CONNECT_TIMEOUT_MS
        conn.readTimeout = READ_TIMEOUT_MS
        conn.instanceFollowRedirects = false
        conn.useCaches = false
        conn.doOutput = true
        conn.setRequestProperty("Content-Type", "application/json; charset=utf-8")
        conn.setRequestProperty("Title", "DeadDrop Acoustic ($packetType)")

        val safeBody =
          JSONObject()
            .put("sender", PacketProtocol.sanitizeText(callsign, 16))
            .put("type", PacketProtocol.sanitizeText(packetType, 16))
            .put("message", PacketProtocol.sanitizeText(payloadText, 240))
            .put("crc16", PacketProtocol.sanitizeText(crcHex, 12))
            .toString()

        conn.outputStream.use { os ->
          os.write(safeBody.toByteArray(StandardCharsets.UTF_8))
          os.flush()
        }

        val code = conn.responseCode
        if (code in 200..299) {
          Result.success("Sent online (HTTP $code)")
        } else {
          Result.failure(IllegalStateException("Relay rejected request (HTTP $code)"))
        }
      } catch (t: Throwable) {
        Result.failure(IllegalStateException(t.message ?: "Network send failed"))
      } finally {
        conn?.disconnect()
      }
    }

  suspend fun fetchOnlineMessages(rawUrl: String): Result<List<String>> =
    withContext(Dispatchers.IO) {
      val baseUrl = SsrfGuard.validateHttpsUrl(rawUrl).getOrElse { return@withContext Result.failure(it) }
      val targetStr =
        if (
          baseUrl.host.equals("ntfy.sh", ignoreCase = true) &&
            baseUrl.query.isNullOrEmpty() &&
            !baseUrl.path.endsWith("/json")
        ) {
          val cleanPath = baseUrl.path.trimEnd('/')
          "https://${baseUrl.host}$cleanPath/json?poll=1"
        } else {
          baseUrl.toString()
        }
      val validatedUrl = SsrfGuard.validateHttpsUrl(targetStr).getOrElse { return@withContext Result.failure(it) }

      var conn: HttpsURLConnection? = null
      try {
        conn =
          (validatedUrl.openConnection() as? HttpsURLConnection)
            ?: return@withContext Result.failure(IllegalStateException("HTTPS connection required"))

        conn.requestMethod = "GET"
        conn.connectTimeout = CONNECT_TIMEOUT_MS
        conn.readTimeout = READ_TIMEOUT_MS
        conn.instanceFollowRedirects = false
        conn.useCaches = false

        val code = conn.responseCode
        if (code !in 200..299) {
          return@withContext Result.failure(IllegalStateException("Inbox check failed (HTTP $code)"))
        }

        val rawText = readCappedUtf8(conn)
        val lines =
          rawText
            .lineSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .mapNotNull { line ->
              try {
                val obj = JSONObject(line)
                if (obj.optString("event") == "keepalive") return@mapNotNull null
                val rawMsg = obj.optString("message", "").ifEmpty { line }
                try {
                  val nested = JSONObject(rawMsg)
                  val sender = PacketProtocol.sanitizeText(nested.optString("sender", "ONLINE"), 16)
                  val msg = PacketProtocol.sanitizeText(nested.optString("message", rawMsg), 200)
                  "$sender: $msg"
                } catch (_: Throwable) {
                  PacketProtocol.sanitizeText(rawMsg, 220)
                }
              } catch (_: Throwable) {
                PacketProtocol.sanitizeText(line, 220)
              }
            }
            .toList()
            .takeLast(20)
            .reversed()

        Result.success(lines)
      } catch (t: Throwable) {
        Result.failure(IllegalStateException(t.message ?: "Network check failed"))
      } finally {
        conn?.disconnect()
      }
    }

  private fun readCappedUtf8(conn: HttpsURLConnection): String {
    val input = BufferedInputStream(conn.inputStream)
    val out = ByteArrayOutputStream()
    val buf = ByteArray(4096)
    var total = 0
    input.use { stream ->
      while (total < MAX_RESPONSE_BYTES) {
        val toRead = (MAX_RESPONSE_BYTES - total).coerceAtMost(buf.size)
        val read = stream.read(buf, 0, toRead)
        if (read <= 0) break
        out.write(buf, 0, read)
        total += read
      }
    }
    return String(out.toByteArray(), StandardCharsets.UTF_8)
  }
}
