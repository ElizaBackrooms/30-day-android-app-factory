# Security Policy & App Hardening Architecture

This repository publishes open-source native Kotlin + Jetpack Compose Android applications built for reliability, privacy, and safe community collaboration. Every application from **Day 3 onward** must adhere to the strict privacy, app-level, and repository-level security controls below. *(Days 1–2 were offline-only v1 prototypes authored before this policy and must be retrofitted with these manifest/packaging controls before any APK or Play Store release.)*

---

## 0. Privacy by Default & Explicit Opt-In Consent (Zero Unsolicited Data)

Every app follows **strict data minimization**:
1. **Do Not Save User Data Unless Opted In:**
   - Apps operate **stateless / in-memory by default**. Local persistence (`SharedPreferences` logs, sensor histories, saved presets) MUST be gated behind an explicit user **Opt-In** toggle (defaulting to `false`) or explicit user save action, paired with a 1-tap **"Wipe All Local Data"** button.
2. **Do Not Transmit Online Data Unless Opted In:**
   - Apps MUST NOT send network requests silently in the background or on app startup without explicit user opt-in (`"Online Features (Opt-In)"` switch defaulting to `false`) or an explicit user-triggered action button.
   - Public weather/elevation/solar API lookups must use manual or coarse rounded coordinates by default unless the user explicitly opts in to GPS lookup.
3. **Zero Device Fingerprinting, Analytics, or Telemetry:**
   - Strictly forbid `Settings.Secure.ANDROID_ID`, `AdvertisingIdClient`, `TelephonyManager` IMEI/IMSI, MAC addresses, `Build.SERIAL`/`getSerial()`, and any analytics, ad, or crash-tracking SDKs (`firebase-analytics`, `play-services-ads`, `mixpanel`, `amplitude`, etc.).

---

## 1. App-Level Security Architecture (Protecting Users From Attacks)

Because these apps are downloadable and may connect to public APIs, user-configured webhooks, BLE beacons, NFC tags, or audio-modem packets, every app enforces **defense-in-depth**:

1. **Strict TLS / HTTPS Transport (`usesCleartextTraffic="false"`)**
   - `AndroidManifest.xml` must explicitly set `android:usesCleartextTraffic="false"`.
   - All remote API requests must use `HttpsURLConnection` over `https://` with standard certificate/hostname verification enabled (never install permissive `TrustManager` or `HostnameVerifier` stubs) and explicit `10_000 ms` connect and read timeouts.

2. **SSRF & Local-Network Protection on User Webhooks**
   - Any app accepting user-supplied webhook or URL endpoints must validate the target immediately before connecting:
     - Require `https://` scheme only.
     - Set `instanceFollowRedirects = false` so a public URL cannot `302` redirect to an internal IP.
     - Resolve `InetAddress.getAllByName(host)` and reject any unspecified (`0.0.0.0/8`, `::`), loopback (`127.0.0.0/8`, `::1`), private RFC-1918 (`10.0.0.0/8`, `172.16.0.0/12`, `192.168.0.0/16`), CGNAT (`100.64.0.0/10`), link-local (`169.254.0.0/16`, `fe80::/10`), unique-local IPv6 (`fc00::/7`), IPv4-mapped IPv6 (`::ffff:0:0/96`), or multicast (`224.0.0.0/4`, `ff00::/8`) address (and reject `localhost` / `.local` / `.internal` hostnames).

3. **Zero Open Inbound Ports**
   - Apps act strictly as outbound clients or local hardware/sensor processors. Apps must never open listening TCP/UDP/UNIX sockets (`ServerSocket`, `DatagramSocket`, `LocalServerSocket`, `AsynchronousServerSocketChannel`) or expose remote command execution endpoints.

4. **Untrusted External Input Validation**
   - All external data—BLE advertisements, NFC NDEF payloads, AFSK audio frames, barcodes, and JSON API responses—must be treated as **untrusted input**:
     - Enforce strict byte-length bounds and character allowlists.
     - Parse JSON strictly via `org.json.JSONObject` / `JSONArray` and treat values as inert UI strings/numbers.
     - Never pass untrusted payloads to `Runtime.exec`, `ProcessBuilder`, WebView `addJavascriptInterface` / `evaluateJavascript` / `javascript:` URLs, `DexClassLoader`, or SQL string concatenation.

5. **Zero Hardcoded Shared Secrets & Hardened Component/Storage Config**
   - Never hardcode API keys, shared secret tokens, or shared public webhook channels/topics in source code.
   - Generate per-user webhook topics or pairing tokens locally via `java.security.SecureRandom` (>= 128 bits of entropy) only when the user enables that feature. Encrypt sensitive secrets/passphrases at rest via Android Keystore (`AES/GCM/NoPadding` wrapping) inside `Context.MODE_PRIVATE` storage, and never log tokens, seeds, or webhook URLs to `android.util.Log`.
   - Set `android:allowBackup="false"`, `android:dataExtractionRules` (API 31+), `android:exported="false"` on all non-launcher components (services, receivers, providers), `PendingIntent.FLAG_IMMUTABLE` on all `PendingIntent` instances, and `WindowManager.LayoutParams.FLAG_SECURE` on screens displaying sensitive keys or seeds. Avoid locking `android:screenOrientation` on `<activity>` to maintain Android 16 (`targetSdk = 36`) large-screen adaptive compliance.
   - Include `enableEdgeToEdge()` with safe `Scaffold` `WindowInsets` and link the hosted Privacy Policy (`https://github.com/ElizaBackrooms/30-day-android-app-factory/blob/main/PRIVACY_POLICY.md`) in the **Field Guide & Privacy** tab.

6. **Graceful Permissions & Hardware Fallbacks**
   - Every sensitive permission (`CAMERA`, `RECORD_AUDIO`, `ACCESS_FINE_LOCATION`, `BLUETOOTH_SCAN`, `NFC`) must display an in-app rationale before prompting and degrade cleanly without crashing if denied or if hardware sensors are absent.

---

## 2. Mandatory Pre-Upload Security Overseer Gate & Repository Policy

To prevent compromised code or leaked personal data from ever being uploaded to GitHub:

- **Pre-Build & Pre-Upload Security Overseer Review (Hard Gate):** Every app from Day 3 onward (and any `days/day_*/android_project/`) MUST be audited by the dedicated `security-overseer` subagent **before** Google AI Studio submission and **again after** extracting `android_project/` **before** any commit or push to GitHub.
- **Cryptographic Attestation (`SECURITY_REVIEW.md`):** After passing review, `scripts/verify_day_attestation.ps1 -Action Write` records a SHA-256 manifest hash of every file in `days/day_NN_<slug>/` inside `SECURITY_REVIEW.md`. `scripts/publish_to_github.ps1`, `scripts/hook_security_gate.ps1`, and GitHub Actions CI all refuse commits/pushes if any file in the day folder was added or modified without a matching `SECURITY_REVIEW.md` sign-off.
- **No Personal Information (PII) or Keystores:** OS usernames, home directory paths, personal email addresses, private Google AI Studio URLs, keystores (`*.jks`, `*.keystore`), and secrets are blocked by `.gitignore`, `scripts/hook_security_gate.ps1`, `scripts/security_audit.ps1`, and GitHub Secret Scanning + Push Protection.
- **GitHub Cloud Security Agents:** `.github/workflows/security-and-android-ci.yml` runs on every push and pull request; `.github/workflows/codeql.yml` (CodeQL SAST) runs on every push, pull request, and weekly Monday schedule; and `.github/dependabot.yml` monitors dependencies.

---

## 3. Reporting a Vulnerability

If you discover a security vulnerability in any app or build configuration in this repository:
- **Do not open a public issue with exploit details.**
- Report the issue privately via **GitHub Security Advisories** (`Security` -> `Report a vulnerability` on this repository).
- Include the affected `days/day_NN_<slug>` directory, reproduction steps, and suggested mitigation.
