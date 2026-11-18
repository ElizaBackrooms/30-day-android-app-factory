# Security Policy & App Hardening Architecture

This repository publishes open-source native Kotlin + Jetpack Compose Android applications built for reliability, privacy, and safe community collaboration. Every application from **Day 3 onward** must adhere to the strict app-level and repository-level security controls below. *(Days 1–2 were offline-only v1 prototypes authored before this policy and must be retrofitted with these manifest/packaging controls before any APK or Play Store release.)*

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
   - Generate per-user webhook topics or pairing tokens locally via `java.security.SecureRandom` (>= 128 bits of entropy). Encrypt sensitive secrets/passphrases at rest via Android Keystore (`AES/GCM/NoPadding` wrapping) inside `Context.MODE_PRIVATE` storage, and never log tokens, seeds, or webhook URLs to `android.util.Log`.
   - Set `android:allowBackup="false"`, `android:dataExtractionRules` (API 31+), `android:exported="false"` on all non-launcher components (services, receivers, providers), `PendingIntent.FLAG_IMMUTABLE` on all `PendingIntent` instances, and `WindowManager.LayoutParams.FLAG_SECURE` on screens displaying sensitive keys or seeds.

6. **Graceful Permissions & Hardware Fallbacks**
   - Every sensitive permission (`CAMERA`, `RECORD_AUDIO`, `ACCESS_FINE_LOCATION`, `BLUETOOTH_SCAN`, `NFC`) must display an in-app rationale before prompting and degrade cleanly without crashing if denied or if hardware sensors are absent.

---

## 2. Repository & Contributor Security Policy

To ensure neither the maintainers nor contributors ever expose personal data, private build keys, or credentials:

- **No Personal Information (PII):** Never commit OS usernames, home directory paths, personal email addresses, or private Google AI Studio session URLs.
- **No Keystores or Secrets:** Release signing keystores (`*.jks`, `*.keystore`), `keystore.properties`, `local.properties`, `.env` files, and API tokens are permanently blocked by `.gitignore` and automated pre-push checks.
- **Pre-Execution Hook & Audit Gate:** `.agents/hooks.json` runs `scripts/hook_security_gate.ps1` before git commit/push/force-add commands, and all pushes run through `scripts/publish_to_github.ps1` + `scripts/security_audit.ps1`, blocking any commit containing local user paths, private URLs, keystores, credential patterns, cleartext HTTP, TLS bypasses, open sockets, or execution sinks.
- **Dedicated Security Overseer Agents:** Every app prompt and extracted Android project is audited by the workspace `security-overseer` subagent (`AGENTS.md`, `.agents/skills/android-security-overseer/SKILL.md`) plus daily scheduled oversight audits logged in `SECURITY_AUDIT_LOG.md`.
- **GitHub Cloud Security Agents:** `.github/workflows/security-and-android-ci.yml` runs on every push and pull request; `.github/workflows/codeql.yml` (CodeQL SAST) runs on every push, pull request, and weekly Monday schedule; `.github/dependabot.yml` monitors dependencies; and GitHub Secret Scanning + Push Protection guard all pushes.

---

## 3. Reporting a Vulnerability

If you discover a security vulnerability in any app or build configuration in this repository:
- **Do not open a public issue with exploit details.**
- Report the issue privately via **GitHub Security Advisories** (`Security` -> `Report a vulnerability` on this repository).
- Include the affected `days/day_NN_<slug>` directory, reproduction steps, and suggested mitigation.
