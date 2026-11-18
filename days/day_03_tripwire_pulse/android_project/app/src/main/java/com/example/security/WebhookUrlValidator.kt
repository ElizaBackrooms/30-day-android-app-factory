package com.example.security

import java.net.Inet4Address
import java.net.Inet6Address
import java.net.InetAddress
import java.net.URI
import java.net.UnknownHostException
import java.security.SecureRandom
import java.util.Locale

sealed class UrlValidationResult {
    data class Valid(
        val normalizedUrl: String,
        val uri: URI,
        val resolvedAddresses: List<InetAddress>
    ) : UrlValidationResult()

    data class Invalid(val reason: String) : UrlValidationResult()
}

fun interface HostAddressResolver {
    @Throws(UnknownHostException::class)
    fun resolveAll(host: String): Array<InetAddress>
}

object WebhookUrlValidator {
    const val MAX_URL_LENGTH = 512
    const val MAX_PAYLOAD_FIELD_LENGTH = 200
    const val MIN_NTFY_RANDOM_BYTES = 20 // >= 16 bytes (160 bits of entropy)

    private val URL_CHAR_ALLOWLIST = Regex("^[A-Za-z0-9._~:/?#\\[\\]@!$&'()*+,;=%-]+$")
    private val PAYLOAD_SAFE_CHAR_REGEX = Regex("[^A-Za-z0-9 .,;:!?()\\[\\]\\-_/+%°²'\"@]")
    private val HEX_CHARS = "0123456789abcdef".toCharArray()
    private val secureRandom = SecureRandom()

    val defaultResolver: HostAddressResolver = HostAddressResolver { host ->
        InetAddress.getAllByName(host)
    }

    /**
     * Generates a fresh per-user Ntfy.sh private topic URL using java.security.SecureRandom
     * with >= 16 bytes of cryptographic entropy (20 bytes = 40 hex chars).
     */
    fun generatePrivateNtfyUrl(): String {
        val randomBytes = ByteArray(MIN_NTFY_RANDOM_BYTES)
        secureRandom.nextBytes(randomBytes)
        val hex = StringBuilder(randomBytes.size * 2)
        for (b in randomBytes) {
            val v = b.toInt() and 0xFF
            hex.append(HEX_CHARS[v ushr 4])
            hex.append(HEX_CHARS[v and 0x0F])
        }
        return "https://ntfy.sh/tripwire-$hex"
    }

    /**
     * Enforces strict length and character-allowlist validation on outgoing alert payload fields.
     */
    fun sanitizePayloadText(raw: String, maxLength: Int = MAX_PAYLOAD_FIELD_LENGTH): String {
        val collapsed = raw
            .replace('\r', ' ')
            .replace('\n', ' ')
            .replace('\t', ' ')
            .trim()
        val filtered = PAYLOAD_SAFE_CHAR_REGEX.replace(collapsed, "")
        return if (filtered.length > maxLength) {
            filtered.substring(0, maxLength)
        } else {
            filtered
        }
    }

    /**
     * Pure syntactic check (safe to call on Main thread for immediate form feedback).
     */
    fun validateSyntax(rawUrl: String): UrlValidationResult {
        val trimmed = rawUrl.trim()
        if (trimmed.isEmpty()) {
            return UrlValidationResult.Invalid("Enter an HTTPS web address first.")
        }
        if (trimmed.length > MAX_URL_LENGTH) {
            return UrlValidationResult.Invalid("Web address is too long (max $MAX_URL_LENGTH characters).")
        }
        if (!URL_CHAR_ALLOWLIST.matches(trimmed)) {
            return UrlValidationResult.Invalid("Web address contains unsupported or unsafe characters.")
        }

        val uri = try {
            URI(trimmed)
        } catch (_: Exception) {
            return UrlValidationResult.Invalid("Web address format is not valid.")
        }

        val scheme = uri.scheme?.lowercase(Locale.US)
        if (scheme != "https") {
            return UrlValidationResult.Invalid("Only secure https:// links are allowed.")
        }
        if (!uri.userInfo.isNullOrEmpty()) {
            return UrlValidationResult.Invalid("Embedded usernames or passwords in URLs are not allowed.")
        }

        val rawHost = uri.host
        if (rawHost.isNullOrBlank()) {
            return UrlValidationResult.Invalid("Web address is missing a valid server name.")
        }

        val port = uri.port
        if (port != -1 && (port !in 1..65535)) {
            return UrlValidationResult.Invalid("Web address has an invalid port number.")
        }

        val hostLower = rawHost.trim().trimEnd('.').lowercase(Locale.US)
        val unbracketedHost = hostLower.removePrefix("[").removeSuffix("]")

        if (isForbiddenHostname(unbracketedHost)) {
            return UrlValidationResult.Invalid("Local or internal network addresses are blocked for safety.")
        }

        // Explicitly reject IPv4-mapped IPv6 literals before DNS parsing (since Java converts ::ffff:x.x.x.x to Inet4Address)
        if (isIpv4MappedIpv6Literal(unbracketedHost)) {
            return UrlValidationResult.Invalid("IPv4-mapped IPv6 addresses (::ffff:0:0/96) are blocked for safety.")
        }

        return UrlValidationResult.Valid(
            normalizedUrl = uri.toASCIIString(),
            uri = uri,
            resolvedAddresses = emptyList()
        )
    }

    /**
     * Full SSRF validation including DNS resolution via InetAddress.getAllByName.
     * MUST be called off the main thread (Dispatchers.IO) before any network request.
     */
    fun validateWithDns(
        rawUrl: String,
        resolver: HostAddressResolver = defaultResolver
    ): UrlValidationResult {
        val syntaxResult = validateSyntax(rawUrl)
        if (syntaxResult is UrlValidationResult.Invalid) {
            return syntaxResult
        }
        val validSyntax = syntaxResult as UrlValidationResult.Valid
        val host = validSyntax.uri.host?.removePrefix("[")?.removeSuffix("]")
            ?: return UrlValidationResult.Invalid("Missing server host.")

        val resolved = try {
            resolver.resolveAll(host)
        } catch (_: UnknownHostException) {
            return UrlValidationResult.Invalid("Could not look up server address (check internet or spelling).")
        } catch (_: SecurityException) {
            return UrlValidationResult.Invalid("Network lookup was blocked by the system.")
        } catch (_: Exception) {
            return UrlValidationResult.Invalid("Could not verify server address.")
        }

        if (resolved.isEmpty()) {
            return UrlValidationResult.Invalid("Server address did not resolve to any public IP.")
        }

        for (addr in resolved) {
            if (isForbiddenIpAddress(addr)) {
                return UrlValidationResult.Invalid(
                    "Blocked private, local, link-local, or reserved IP address for SSRF safety."
                )
            }
        }

        return UrlValidationResult.Valid(
            normalizedUrl = validSyntax.normalizedUrl,
            uri = validSyntax.uri,
            resolvedAddresses = resolved.toList()
        )
    }

    fun isForbiddenHostname(hostLowerNoTrailingDot: String): Boolean {
        if (hostLowerNoTrailingDot.isEmpty()) return true
        if (hostLowerNoTrailingDot == "localhost") return true
        if (hostLowerNoTrailingDot.endsWith(".localhost")) return true
        if (hostLowerNoTrailingDot.endsWith(".local")) return true
        if (hostLowerNoTrailingDot.endsWith(".internal")) return true
        return false
    }

    fun isIpv4MappedIpv6Literal(unbracketedHostLower: String): Boolean {
        if (!unbracketedHostLower.contains(':')) return false
        return unbracketedHostLower.contains("::ffff:") ||
            Regex("^(0+:){5}ffff:", RegexOption.IGNORE_CASE).containsMatchIn(unbracketedHostLower)
    }

    /**
     * Rejects all non-public, private, loopback, link-local, unspecified, CGNAT, ULA,
     * IPv4-mapped IPv6, and multicast IP ranges.
     */
    fun isForbiddenIpAddress(address: InetAddress): Boolean {
        if (address.isAnyLocalAddress ||
            address.isLoopbackAddress ||
            address.isLinkLocalAddress ||
            address.isSiteLocalAddress ||
            address.isMulticastAddress
        ) {
            return true
        }

        val bytes = address.address
        return when {
            address is Inet4Address || bytes.size == 4 -> isForbiddenIpv4Bytes(bytes)
            address is Inet6Address || bytes.size == 16 -> isForbiddenIpv6Bytes(bytes)
            else -> true
        }
    }

    fun isForbiddenIpv4Bytes(bytes: ByteArray): Boolean {
        if (bytes.size != 4) return true
        val b0 = bytes[0].toInt() and 0xFF
        val b1 = bytes[1].toInt() and 0xFF

        return when {
            b0 == 0 -> true                                    // 0.0.0.0/8 (unspecified / current network)
            b0 == 10 -> true                                   // 10.0.0.0/8 (RFC 1918 private)
            b0 == 100 && (b1 in 64..127) -> true               // 100.64.0.0/10 (CGNAT)
            b0 == 127 -> true                                  // 127.0.0.0/8 (loopback)
            b0 == 169 && b1 == 254 -> true                     // 169.254.0.0/16 (link-local)
            b0 == 172 && (b1 in 16..31) -> true                // 172.16.0.0/12 (RFC 1918 private)
            b0 == 192 && b1 == 168 -> true                     // 192.168.0.0/16 (RFC 1918 private)
            (b0 and 0xF0) == 224 -> true                       // 224.0.0.0/4 (multicast)
            b0 >= 240 -> true                                  // 240.0.0.0/4 (reserved & broadcast)
            else -> false
        }
    }

    fun isForbiddenIpv6Bytes(bytes: ByteArray): Boolean {
        if (bytes.size != 16) return true
        val b0 = bytes[0].toInt() and 0xFF
        val b1 = bytes[1].toInt() and 0xFF

        // :: (all zeros — unspecified IPv6)
        val allZero = bytes.all { it.toInt() == 0 }
        if (allZero) return true

        // ::1 (loopback IPv6)
        val isLoopback = (0..14).all { bytes[it].toInt() == 0 } && (bytes[15].toInt() and 0xFF) == 1
        if (isLoopback) return true

        // fc00::/7 (Unique Local Address)
        if ((b0 and 0xFE) == 0xFC) return true

        // fe80::/10 (Link-Local)
        if (b0 == 0xFE && (b1 and 0xC0) == 0x80) return true

        // ff00::/8 (Multicast)
        if (b0 == 0xFF) return true

        // ::ffff:0:0/96 (IPv4-mapped IPv6)
        val isIpv4Mapped = (0..9).all { bytes[it].toInt() == 0 } &&
            (bytes[10].toInt() and 0xFF) == 0xFF &&
            (bytes[11].toInt() and 0xFF) == 0xFF
        if (isIpv4Mapped) return true

        // ::/96 (legacy IPv4-compatible IPv6)
        val isIpv4Compat = (0..11).all { bytes[it].toInt() == 0 }
        if (isIpv4Compat) return true

        return false
    }
}
