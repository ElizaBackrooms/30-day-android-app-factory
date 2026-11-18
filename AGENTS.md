# Workspace Security & Oversight Mandate

Every agent, subagent, and scheduled automation operating in this workspace **must** enforce strict **Security Overseer** checks before generating, editing, building, or pushing code.

## Mandatory Security Overseer Gate (Required for Every App & Change)

1. **Prompt & Architecture Review (Before Building):**
   - Before submitting any `google_ai_studio_prompt.txt` to Google AI Studio, define and invoke a dedicated **`security-overseer`** subagent (`define_subagent` + `invoke_subagent`) to audit the prompt and `play_store_listing.md` against [SECURITY.md](SECURITY.md):
     - Package ID uses `io.github.elizabackrooms.<slug>` (never `com.example` or personal usernames).
     - `android:usesCleartextTraffic="false"`, `android:allowBackup="false"`, and `android:exported="false"` on all non-launcher components.
     - Network calls use `HttpsURLConnection` (`https://` only) with `10_000 ms` timeouts, `instanceFollowRedirects = false`, and default strict TLS certificate/hostname verification.
     - Any user-configurable webhook enforces an SSRF blocklist resolving `InetAddress.getAllByName` and rejecting `localhost`, `.local`, `.internal`, unspecified (`0.0.0.0/8`, `::`), loopback (`127.0.0.0/8`, `::1`), private RFC-1918 (`10.0.0.0/8`, `172.16.0.0/12`, `192.168.0.0/16`), CGNAT (`100.64.0.0/10`), link-local (`169.254.0.0/16`, `fe80::/10`), unique-local IPv6 (`fc00::/7`), IPv4-mapped IPv6 (`::ffff:0:0/96`), and multicast targets.
     - Zero open listening sockets (`ServerSocket`, `DatagramSocket`, `LocalServerSocket`), zero hardcoded shared API keys/topics, `SecureRandom` (>=128-bit) per-user tokens stored in `MODE_PRIVATE` (wrapped with Android Keystore AES-GCM for sensitive credentials), `PendingIntent.FLAG_IMMUTABLE`, `FLAG_SECURE` on sensitive seed/token views, zero token logging, and strict length/allowlist validation for BLE, NFC, AFSK, barcode, and JSON inputs.
     - Accurate Google Play Data Safety declarations and runtime permission rationale UI.

2. **Code & OpSec Review (Before Publishing to GitHub):**
   - After downloading and extracting any `days/day_NN_<slug>/android_project/`, run `scripts/security_audit.ps1` and invoke the **`security-overseer`** subagent to audit the extracted Kotlin, Gradle, and `AndroidManifest.xml` files.
   - Never store private Google AI Studio URLs (`ai_studio_url`), OS usernames, absolute local home paths, keystores, or tokens anywhere outside `.private/` (which is gitignored).
   - Always push via `scripts/publish_to_github.ps1` so the pre-push security audit runs automatically before `git commit` and `git push`.

3. **Real-Time Hook & Skill Support:**
   - Use the `android-security-overseer` skill in `.agents/skills/android-security-overseer/SKILL.md` for full audit checklists.
   - `.agents/hooks.json` enforces `scripts/hook_security_gate.ps1` on `run_command` to block unsafe commits/pushes, force-adds, or secret exposures.
