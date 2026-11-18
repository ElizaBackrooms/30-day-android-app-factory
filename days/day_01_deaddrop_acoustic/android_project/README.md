# DeadDrop Acoustic

Offline-first 300-baud acoustic messenger (1200/2200 Hz BFSK with CRC-16) and flashlight Morse beacon for Android 8.0+ (`minSdk = 26`, `targetSdk = 36`), with zero Google Play Services and optional opt-in HTTPS relay support.

## Privacy & Security
- **Stateless by default**: Message logs are kept in memory only and discarded when closed unless the user explicitly enables **Save Message Log on Phone (Opt-In)**.
- **Offline by default**: Zero network calls happen unless the user explicitly enables **Allow Optional Online Relay (Opt-In)** and taps Send/Check.
- **LineageOS / de-Googled ready**: Uses standard AOSP `AudioTrack`, `AudioRecord`, `CameraManager`, `LocationManager`, and `HttpsURLConnection` with strict SSRF IP/host filtering.

## Build Locally
1. Open `android_project/` in Android Studio or build from CLI with Gradle 9.3.1:
   `gradle assembleDebug`
2. Run unit & Robolectric tests:
   `gradle testDebugUnitTest`
