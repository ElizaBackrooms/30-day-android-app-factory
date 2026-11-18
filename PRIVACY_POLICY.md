# Privacy Policy — 30-Day Android App Factory (`ElizaBackrooms`)

**Effective Date:** November 18, 2026  
**Developer / Publisher:** `ElizaBackrooms`  
**Repository & Apps Covered:** All Android applications published under package prefix `io.github.elizabackrooms.*` from the [30-Day Android App Factory](https://github.com/ElizaBackrooms/30-day-android-app-factory) repository (`Day 3` onward, and updated releases of `Days 1–2`).

---

## 1. Core Privacy Principle: Zero Unsolicited Data Collection
We build standalone Android utility applications designed to respect your privacy by default:
- **No Accounts Required:** None of our apps require user registration, login, or developer-operated cloud accounts.
- **No Analytics, Ads, or Device Fingerprinting:** Our apps contain **zero** ad networks, **zero** third-party analytics SDKs, and **zero** tracking identifiers (`ANDROID_ID`, Advertising ID, IMEI, MAC addresses, or hardware serial numbers).
- **Stateless by Default:** Calculations, sensor readings, and audio/RF processing remain in temporary device memory (RAM) and are discarded when you close the app, **unless you explicitly opt in** to saving local logs or presets on your device.

---

## 2. Optional Opt-In Features & Third-Party Requests
Some apps provide optional features that are **turned OFF by default** and execute **only when you explicitly opt in or tap a manual action button**:

1. **Local Storage (Opt-In Only):**
   - If you enable local history/log saving, entries are stored strictly on your own device in private app storage (`Context.MODE_PRIVATE`, with `android:allowBackup="false"`). You can erase all saved local data anytime using the in-app **"Wipe All Local Data"** button or by uninstalling the app.
2. **Optional Public API Lookups (Opt-In / Action-Triggered):**
   - When you explicitly opt in or request live online data (for example: weather, elevation, solar indices, foreign exchange rates, dictionary definitions, food barcodes, or BGP/ASN diagnostics), the app sends a standard encrypted `HTTPS` request directly from your device to the corresponding public keyless service (such as Open-Meteo, NOAA SWPC, Open-Elevation, OpenFoodFacts, Frankfurter/ECB, Free Dictionary API, RIPEstat, or OSM Nominatim).
   - Location-based queries use manual or coarse coordinates unless you explicitly grant location permission and opt in to GPS lookup.
   - Please review the respective public API provider's privacy notice if you choose to enable online lookups.
3. **Optional User-Configured Webhooks (Opt-In Only):**
   - If an app supports sending alerts to a webhook you configure (such as Ntfy.sh or Discord), requests are sent solely to the `https://` endpoint you specify, protected by strict TLS verification and SSRF private-network blocking. No developer servers receive your alerts.

---

## 3. Sensitive Hardware & Runtime Permissions
Apps request Android runtime permissions (`RECORD_AUDIO`, `CAMERA`, `ACCESS_FINE_LOCATION`, `BLUETOOTH_SCAN`, `NFC`, `POST_NOTIFICATIONS`) **only when needed** for the specific tool you choose to run, and display an in-app explanation before prompting:
- **Microphone / Camera / Sensors / BLE / NFC:** Processed strictly on-device in real time; raw audio, camera frames, or local scans are never uploaded to developer servers.
- **Permission Denial Fallback:** Denying optional permissions disables only that hardware input while keeping manual entry and non-hardware tools functional.

---

## 4. Data Security & Deletion
- All network traffic enforces HTTPS/TLS (`android:usesCleartextTraffic="false"`).
- Sensitive local tokens or keys (when used) are generated via `SecureRandom` on-device and protected with Android Keystore encryption.
- **Deleting Your Data:** Because `ElizaBackrooms` operates no remote user database, you control 100% of your data. Use the in-app **"Wipe All Local Data"** button, clear app storage in Android Settings, or uninstall the app.

---

## 5. Contact & Privacy Inquiries
For privacy questions, security reports, or policy inquiries regarding any `io.github.elizabackrooms.*` app:
- **GitHub Security Advisories:** https://github.com/ElizaBackrooms/30-day-android-app-factory/security/advisories
- **Public Repository & Issue Tracker:** https://github.com/ElizaBackrooms/30-day-android-app-factory/issues
