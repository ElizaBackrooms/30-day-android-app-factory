---
name: android-security-overseer
description: >-
  Adversarial Android AppSec, Privacy-by-Default, and OpSec audit skill for the 30-Day Android App Factory.
  Use whenever reviewing prompts, Kotlin/Compose source code, AndroidManifest.xml files,
  GitHub Actions workflows, or preparing daily apps for GitHub and Google Play release.
---

# Android Security Overseer

Run this checklist whenever creating, modifying, downloading, or publishing any daily Android app.

## 1. Automated Static, OpSec & Attestation Audit
Run the shared security audit script from the repository root:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\security_audit.ps1
```

After your manual adversarial review passes, write the signed SHA-256 day review receipt before publishing:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\verify_day_attestation.ps1 -Action Write -DayFolder days\day_NN_<slug> -Summary "<concise audit summary>"
```

## 2. Adversarial Code, Privacy & Manifest Review Checklist
Inspect `days/day_NN_<slug>/google_ai_studio_prompt.txt`, `play_store_listing.md`, and every file under `days/day_NN_<slug>/android_project/`:

1. **Privacy by Default (No Unsolicited User Data):**
   - App starts **stateless / in-memory by default**; local persistence (`SharedPreferences` / files) is strictly gated behind an explicit user **Opt-In** switch defaulting to `false` (with a 1-tap "Wipe All Local Data" action).
   - Network requests are strictly gated behind explicit user opt-in (default `false`) or explicit user button tap (no silent startup pings).
   - Zero `ANDROID_ID`, `AdvertisingIdClient`, IMEI, MAC, `Build.SERIAL`, or analytics/tracking SDKs.
2. **Transport Security:**
   - `AndroidManifest.xml` explicitly sets `android:usesCleartextTraffic="false"`.
   - Zero `http://` endpoints in Kotlin/Java/XML/Gradle code (only `https://`).
   - Zero custom `X509TrustManager` or `HostnameVerifier` bypasses.
   - `connectTimeout = 10_000` and `readTimeout = 10_000` set on `HttpsURLConnection`.
3. **SSRF & Network Attack Surface:**
   - No `ServerSocket`, `DatagramSocket`, `LocalServerSocket`, `AsynchronousServerSocketChannel`, or unauthenticated inbound endpoints.
   - Any user-supplied webhook URL parses via `java.net.URI`, sets `instanceFollowRedirects = false`, resolves `InetAddress.getAllByName`, requires `https`, and rejects `localhost`/`.local`/`.internal`, unspecified (`0.0.0.0/8`, `::`), loopback (`127/8`, `::1`), site-local (`10/8`, `172.16/12`, `192.168/16`), CGNAT (`100.64/10`), link-local (`169.254/16`, `fe80::/10`), unique-local IPv6 (`fc00::/7`), IPv4-mapped IPv6 (`::ffff:0:0/96`), and multicast ranges.
4. **Untrusted Data & Injection Protection:**
   - BLE advertisements, NFC NDEF payloads, AFSK audio frames, barcodes, and JSON responses are length-bounded and parsed strictly as data.
   - Zero `Runtime.exec`, `ProcessBuilder`, `addJavascriptInterface`, `evaluateJavascript`, `javascript:` URLs, `setJavaScriptEnabled(true)`, or `DexClassLoader` / `PathClassLoader` / `InMemoryDexClassLoader` calls.
5. **Component & Storage Hardening:**
   - `android:allowBackup="false"` and `android:exported="false"` on all non-launcher components.
   - Per-user topics/tokens generated via `java.security.SecureRandom` (>= 16 bytes) and stored in `Context.MODE_PRIVATE` (wrapped with Android Keystore AES-GCM for sensitive credentials/seeds), with zero `Log` leakage, `PendingIntent.FLAG_IMMUTABLE`, and `FLAG_SECURE` on sensitive views.
6. **OpSec & Play Store Compliance (2026 Android 16 Standard):**
   - Zero OS usernames, local home directory paths, private studio URLs, or keystores in tracked files.
   - `applicationId` matches `io.github.elizabackrooms.<slug>`, `minSdk >= 26`, `targetSdk = 36` (Android 16 required by Google Play since Aug 31, 2026), `enableEdgeToEdge()` enabled with safe `WindowInsets`, no `android:screenOrientation` locks, zero Google Play Services (`com.google.android.gms` / `play-services-*` / `firebase-*`) for LineageOS Moto G Stylus 2023 compatibility, `android:required="false"` on optional hardware `<uses-feature>` tags (such as NFC/camera/BLE/barometer), Privacy Policy URL (`https://github.com/ElizaBackrooms/30-day-android-app-factory/blob/main/PRIVACY_POLICY.md`) linked inside the app and `play_store_listing.md`, and Data Safety answers match actual opt-in permissions/network requests.
