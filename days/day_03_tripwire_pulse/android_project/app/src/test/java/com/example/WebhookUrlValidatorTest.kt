package com.example

import com.example.security.HostAddressResolver
import com.example.security.UrlValidationResult
import com.example.security.WebhookUrlValidator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.InetAddress

class WebhookUrlValidatorTest {

    private fun resolverReturning(vararg ips: String): HostAddressResolver {
        val addrs = ips.map { InetAddress.getByName(it) }.toTypedArray()
        return HostAddressResolver { addrs }
    }

    @Test
    fun `accepts valid public HTTPS webhook URLs`() {
        val ntfyResult = WebhookUrlValidator.validateWithDns(
            "https://ntfy.sh/tripwire-abcdef1234567890",
            resolverReturning("93.184.216.34")
        )
        assertTrue(ntfyResult is UrlValidationResult.Valid)

        val discordResult = WebhookUrlValidator.validateWithDns(
            "https://discord.com/api/webhooks/12345/abcdef_Token-99",
            resolverReturning("162.159.135.232")
        )
        assertTrue(discordResult is UrlValidationResult.Valid)

        // Boundary check: 172.32.0.1 and 100.128.0.1 are public
        val publicBoundary = WebhookUrlValidator.validateWithDns(
            "https://alerts.example.org/hook",
            resolverReturning("172.32.0.1", "100.128.0.1")
        )
        assertTrue(publicBoundary is UrlValidationResult.Valid)
    }

    @Test
    fun `rejects non-HTTPS schemes, userinfo, oversized URLs, and unsafe characters`() {
        val badUrls = listOf(
            "",
            "http" + "://ntfy.sh/mytopic",
            "ftp://ntfy.sh/mytopic",
            "file:///etc/passwd",
            "javascript:alert(1)",
            "https://user:pass@ntfy.sh/topic",
            "https://ntfy.sh/topic with spaces",
            "https://ntfy.sh/topic\r\nInjected: header",
            "https://ntfy.sh/" + "a".repeat(600)
        )
        for (bad in badUrls) {
            val result = WebhookUrlValidator.validateWithDns(bad, resolverReturning("93.184.216.34"))
            assertTrue("Expected invalid for: $bad", result is UrlValidationResult.Invalid)
        }
    }

    @Test
    fun `rejects localhost, dot-local, and dot-internal hostnames`() {
        val forbiddenHosts = listOf(
            "https://localhost/hook",
            "https://LOCALHOST./hook",
            "https://sub.localhost/hook",
            "https://router.local/hook",
            "https://metadata.google.internal/hook",
            "https://service.INTERNAL./hook"
        )
        for (url in forbiddenHosts) {
            val result = WebhookUrlValidator.validateWithDns(url, resolverReturning("93.184.216.34"))
            assertTrue("Expected blocked hostname for $url", result is UrlValidationResult.Invalid)
        }
    }

    @Test
    fun `rejects all forbidden IPv4, IPv6, IPv4-mapped IPv6, and multicast ranges`() {
        val forbiddenIps = listOf(
            "0.0.0.0",             // 0.0.0.0/8
            "0.12.34.56",          // 0.0.0.0/8
            "::",                  // unspecified IPv6
            "127.0.0.1",           // 127.0.0.0/8
            "127.255.0.1",         // 127.0.0.0/8
            "::1",                 // IPv6 loopback
            "10.0.0.1",            // 10.0.0.0/8
            "172.16.0.1",          // 172.16.0.0/12
            "172.31.255.254",      // 172.16.0.0/12
            "192.168.1.1",         // 192.168.0.0/16
            "100.64.0.1",          // 100.64.0.0/10 (CGNAT)
            "100.127.255.254",     // 100.64.0.0/10 (CGNAT)
            "169.254.169.254",     // 169.254.0.0/16 (link-local / cloud metadata)
            "fc00::1",             // fc00::/7 (ULA)
            "fd12:3456:789a::1",   // fc00::/7 (ULA)
            "fe80::1",             // fe80::/10 (IPv6 link-local)
            "224.0.0.1",           // IPv4 multicast
            "239.255.255.250",     // IPv4 multicast SSDP
            "ff02::1"              // IPv6 multicast
        )

        for (ip in forbiddenIps) {
            val result = WebhookUrlValidator.validateWithDns(
                "https://webhook.example.com/notify",
                resolverReturning(ip)
            )
            assertTrue("Expected SSRF block for resolved IP $ip", result is UrlValidationResult.Invalid)
        }

        // Mixed DNS response containing one public IP and one private IP must still be rejected
        val mixedDns = WebhookUrlValidator.validateWithDns(
            "https://rebind.example.com/hook",
            resolverReturning("93.184.216.34", "10.0.0.5")
        )
        assertTrue(mixedDns is UrlValidationResult.Invalid)

        // IPv4-mapped IPv6 literals (::ffff:0:0/96) blocked even if mapped to a public IP
        val mappedPublic = WebhookUrlValidator.validateSyntax("https://[::ffff:93.184.216.34]/hook")
        assertTrue(mappedPublic is UrlValidationResult.Invalid)
        val mappedLoopback = WebhookUrlValidator.validateSyntax("https://[::ffff:127.0.0.1]/hook")
        assertTrue(mappedLoopback is UrlValidationResult.Invalid)

        // Direct 16-byte check for ::ffff:93.184.216.34
        val mappedBytes = ByteArray(16).apply {
            this[10] = 0xFF.toByte()
            this[11] = 0xFF.toByte()
            this[12] = 93
            this[13] = 184.toByte()
            this[14] = 216.toByte()
            this[15] = 34
        }
        assertTrue(WebhookUrlValidator.isForbiddenIpv6Bytes(mappedBytes))
    }

    @Test
    fun `generatePrivateNtfyUrl creates unique high-entropy HTTPS topics`() {
        val first = WebhookUrlValidator.generatePrivateNtfyUrl()
        val second = WebhookUrlValidator.generatePrivateNtfyUrl()

        assertTrue(first.startsWith("https://ntfy.sh/tripwire-"))
        assertTrue(second.startsWith("https://ntfy.sh/tripwire-"))
        assertNotEquals(first, second)

        val tokenPart = first.removePrefix("https://ntfy.sh/tripwire-")
        assertTrue("Expected >= 32 hex chars (>= 16 random bytes)", tokenPart.length >= 32)
        assertTrue(WebhookUrlValidator.validateSyntax(first) is UrlValidationResult.Valid)
    }

    @Test
    fun `sanitizePayloadText strips control characters and enforces max length`() {
        val dirty = "Front Door\r\n<script>alert(1)</script>\t" + "X".repeat(300)
        val clean = WebhookUrlValidator.sanitizePayloadText(dirty, maxLength = 50)
        assertFalse(clean.contains('\r'))
        assertFalse(clean.contains('\n'))
        assertFalse(clean.contains('<'))
        assertEquals(50, clean.length)
    }
}
