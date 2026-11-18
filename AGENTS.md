# Workspace Security & Oversight Mandate

Every agent, subagent, and scheduled automation operating in this workspace **must** enforce strict **Security Overseer** checks and **Privacy-by-Default (Opt-In Only)** rules before generating, editing, building, or uploading code.

## Mandatory Gates (Required for Every App & Change)

1. **Privacy by Default (No User Data Saved/Sent Without Opt-In):**
   - Apps operate **stateless / in-memory by default**. Local persistence (`SharedPreferences` logs/history) and online API/webhook calls MUST start **OFF (`false`)** behind explicit user opt-in toggles or explicit user action buttons, paired with a 1-tap **"Wipe All Local Data"** control.
   - Zero device fingerprinting (`ANDROID_ID`, `AdvertisingIdClient`, IMEI, MAC, `Build.SERIAL`) and zero analytics/ad SDKs.

2. **Prompt & Architecture Review (Before Building):**
   - Before submitting any `google_ai_studio_prompt.txt` to Google AI Studio, define and invoke a dedicated **`security-overseer`** subagent (`define_subagent` + `invoke_subagent`) to audit the prompt and `play_store_listing.md` against [SECURITY.md](SECURITY.md):
     - Package ID uses `io.github.elizabackrooms.<slug>` (never `com.example` or personal usernames).
     - **LineageOS / Moto G Stylus 2023 Compatibility (Zero GMS):** Zero Google Play Services (`com.google.android.gms`, `play-services-*`) and zero Firebase SDKs; use standard AOSP APIs (`LocationManager`, `SensorManager`, `AudioTrack`, `CameraManager`, `NfcAdapter`, `BluetoothLeScanner`, `HttpsURLConnection`), `minSdk = 26`, and `android:required="false"` on optional hardware `<uses-feature>` tags (NFC, camera flash, barometer, BLE) with clean fallbacks.
     - Android 16 (`targetSdk = 36`) Play Console readiness: `enableEdgeToEdge()`, `Scaffold` `innerPadding` / `WindowInsets`, adaptive large-screen layouts (no `android:screenOrientation` lock), `android:usesCleartextTraffic="false"`, `android:allowBackup="false"`, `android:exported="false"` on all non-launcher components, and `neverForLocation` on `BLUETOOTH_SCAN` when not deriving physical location.
     - Network calls use `HttpsURLConnection` (`https://` only) with `10_000 ms` timeouts, `instanceFollowRedirects = false`, and default strict TLS certificate/hostname verification.
     - Any user-configurable webhook enforces an SSRF blocklist resolving `InetAddress.getAllByName` and rejecting `localhost`, `.local`, `.internal`, unspecified (`0.0.0.0/8`, `::`), loopback (`127.0.0.0/8`, `::1`), private RFC-1918 (`10.0.0.0/8`, `172.16.0.0/12`, `192.168.0.0/16`), CGNAT (`100.64.0.0/10`), link-local (`169.254.0.0/16`, `fe80::/10`), unique-local IPv6 (`fc00::/7`), IPv4-mapped IPv6 (`::ffff:0:0/96`), and multicast targets.
     - Zero open listening sockets (`ServerSocket`, `DatagramSocket`, `LocalServerSocket`), zero hardcoded shared API keys/topics, `SecureRandom` (>=128-bit) per-user tokens stored in `MODE_PRIVATE` (wrapped with Android Keystore AES-GCM for sensitive credentials), `PendingIntent.FLAG_IMMUTABLE`, `FLAG_SECURE` on sensitive seed/token views, zero token logging, and strict length/allowlist validation for BLE, NFC, AFSK, barcode, and JSON inputs.
     - Accurate Google Play Data Safety declarations, live Privacy Policy URL (`https://github.com/ElizaBackrooms/30-day-android-app-factory/blob/main/PRIVACY_POLICY.md`) linked both in-app and in `play_store_listing.md`, and runtime permission rationale UI.

3. **Pre-Upload Code Review & Cryptographic Sign-Off (Before Publishing to GitHub):**
   - First extract downloaded bundles locally without pushing (`scripts/publish_to_github.ps1 -DayFolder days/day_NN_<slug> -ExtractOnly`).
   - Run `scripts/security_audit.ps1` and invoke the **`security-overseer`** subagent to audit ALL files under `days/day_NN_<slug>/` (including extracted Kotlin, Gradle, `AndroidManifest.xml`, prompt, and Play Store listing). Fix any issue and re-audit until `PASS`.
   - Record the SHA-256 verified approval receipt via `scripts/verify_day_attestation.ps1 -Action Write -DayFolder days/day_NN_<slug> -Summary "..."`.
   - Never store private Google AI Studio URLs (`ai_studio_url`), OS usernames, absolute local home paths, keystores, or tokens anywhere outside `.private/` (which is gitignored).
   - Always push via `scripts/publish_to_github.ps1` (which enforces both `security_audit.ps1` and `verify_day_attestation.ps1 -Action Verify` before committing and pushing).

4. **Real-Time Hook & Skill Support:**
   - Use the `android-security-overseer` skill in `.agents/skills/android-security-overseer/SKILL.md` for full audit checklists.
   - `.agents/hooks.json` enforces `scripts/hook_security_gate.ps1` on `run_command` to block unsafe commits/pushes, missing `SECURITY_REVIEW.md` sign-offs, force-adds, or secret exposures.
