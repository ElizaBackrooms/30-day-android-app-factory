# Security Policy & App Hardening Architecture

This repository publishes open-source native Kotlin + Jetpack Compose Android applications built for reliability, privacy, and safe community collaboration. Every application from **Day 3 onward** must adhere to the strict app-level and repository-level security controls below.

---

## 1. App-Level Security Architecture (Protecting Users From Attacks)

Because these apps are downloadable and may connect to public APIs, user-configured webhooks, BLE beacons, NFC tags, or audio-modem packets, every app enforces **defense-in-depth**:

1. **Strict TLS / HTTPS Transport (`usesCleartextTraffic="false"`)**
   - `AndroidManifest.xml` must set `android:usesCleartextTraffic="false"`.
   - All remote API requests must use `HttpsURLConnection` over `https://` with standard certificate/hostname verification enabled (never install permissive `TrustManager` or `HostnameVerifier` stubs) and explicit `10_000 ms` connect and read timeouts.

2. **SSRF & Local-Network Protection on User Webhooks**
   - Any app accepting user-supplied webhook or URL endpoints must validate the URL before connecting:
     - Require `https://` scheme only.
     - Resolve/inspect the host and reject loopback, private, and link-local IP ranges (`localhost`, `127.0.0.0/8`, `::1`, `10.0.0.0/8`, `172.16.0.0/12`, `192.168.0.0/16`, `169.254.0.0/16`, `fc00::/7`).

3. **Zero Open Inbound Ports**
   - Apps act strictly as outbound clients or local hardware/sensor processors. Apps must never open unauthenticated listening TCP/UDP server sockets or expose remote command execution endpoints.

4. **Untrusted External Input Validation**
   - All external data—BLE advertisements, NFC NDEF payloads, AFSK audio frames, barcodes, and JSON API responses—must be treated as **untrusted input**:
     - Enforce strict byte-length bounds and character allowlists.
     - Parse JSON strictly via `org.json.JSONObject` / `JSONArray` and treat values as inert UI strings/numbers.
     - Never pass untrusted payloads to `Runtime.exec`, shell commands, WebView JavaScript evaluation, or dynamic reflection.

5. **Zero Hardcoded Shared Secrets & Isolated Local Storage**
   - Never hardcode API keys, shared secret tokens, or shared public webhook channels/topics in source code.
   - Any user-specific webhook topic or pairing token must be generated locally using `java.security.SecureRandom` (>= 128 bits of entropy) and stored in `Context.MODE_PRIVATE` `SharedPreferences`.
   - Set `android:allowBackup="false"` and `android:exported="false"` on all non-launcher Android components (services, receivers, providers).

6. **Graceful Permissions & Hardware Fallbacks**
   - Every sensitive permission (`CAMERA`, `RECORD_AUDIO`, `ACCESS_FINE_LOCATION`, `BLUETOOTH_SCAN`, `NFC`) must display an in-app rationale before prompting and degrade cleanly without crashing if denied or if hardware sensors are absent.

---

## 2. Repository & Contributor Security Policy

To ensure neither the maintainers nor contributors ever expose personal data, private build keys, or credentials:

- **No Personal Information (PII):** Never commit OS usernames, home directory paths, personal email addresses, or private Google AI Studio session URLs.
- **No Keystores or Secrets:** Release signing keystores (`*.jks`, `*.keystore`), `keystore.properties`, `local.properties`, `.env` files, and API tokens are permanently blocked by `.gitignore` and automated pre-push checks.
- **Pre-Push Security Gate:** All pushes run through `scripts/publish_to_github.ps1`, which blocks any commit containing local user paths, private URLs, keystores, or credential patterns.
- **CI Security Audit:** Every push and pull request is automatically audited by `.github/workflows/security-and-android-ci.yml` for leaked secrets and insecure manifest settings (`usesCleartextTraffic="true"`).

---

## 3. Reporting a Vulnerability

If you discover a security vulnerability in any app or build configuration in this repository:
- **Do not open a public issue with exploit details.**
- Report the issue privately via **GitHub Security Advisories** (`Security` -> `Report a vulnerability` on this repository).
- Include the affected `days/day_NN_<slug>` directory, reproduction steps, and suggested mitigation.
