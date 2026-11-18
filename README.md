# 30-Day Android App Factory

An open-source collection of **30 daily native Kotlin + Jetpack Compose Android applications** (`io.github.elizabackrooms.<slug>`), combining real hardware sensors and deterministic DSP with online-enhanced APIs and Google Play Store readiness.

- **Roadmap & API Integrations:** [30_DAY_SLATE.md](30_DAY_SLATE.md)
- **Live Tracker:** [tracker.json](tracker.json)
- **Security & App Hardening Standard:** [SECURITY.md](SECURITY.md)
- **Play Store Packaging & Signing Guide:** [PLAY_STORE_AND_ONLINE_GUIDE.md](PLAY_STORE_AND_ONLINE_GUIDE.md)

---

## How Apps Are Built & Distributed

Every app from **Day 3 onward** is engineered with:
- **Native Kotlin + Jetpack Compose (Material 3):** Clean single-module architecture targeting Android 8.0+ (`targetSdk = 35`) with zero Google Play Services (GMS) bloat.
- **Online-Enhanced + Offline-Resilient:** Uses free, keyless public APIs and user-configured HTTPS webhooks via `HttpsURLConnection` + Coroutines (`Dispatchers.IO`), backed by `SharedPreferences` caching for full offline capability.
- **Security-First Design:** Strict HTTPS-only networking (`usesCleartextTraffic="false"`), SSRF-safe webhook validation, bounded input parsing for BLE/NFC/AFSK/JSON payloads, and `SecureRandom` user-scoped tokens. See [SECURITY.md](SECURITY.md) for full details.

---

## Downloading & Contributing

### Download & Run
1. **Pre-built Debug APKs (when available):** Check the **Actions** tab (`Security Audit & Android APK Build` workflow) for downloadable `.apk` artifacts produced for each `days/day_*/android_project`.
2. **Build from Source:**
   - Clone this repository:
     ```bash
     git clone https://github.com/ElizaBackrooms/30-day-android-app-factory.git
     ```
   - Open any `days/day_NN_<slug>/android_project` in **Android Studio** (or generate/customize using `days/day_NN_<slug>/google_ai_studio_prompt.txt` in Google AI Studio) and build via `./gradlew assembleDebug` or `./gradlew bundleRelease`.

### Contribute Improvements
Pull requests to fix bugs, enhance UI, add tests, or improve hardware compatibility are welcome!
- Keep all apps compliant with [SECURITY.md](SECURITY.md).
- Do not submit keystores, `local.properties`, personal paths, or hardcoded API keys (CI will fail automatically if detected).
