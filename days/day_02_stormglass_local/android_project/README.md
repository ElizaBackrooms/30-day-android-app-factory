# StormGlass Local

Offline-first barometric weather station, Zambretti storm forecaster, and rapid squall alert utility for Android 8.0+ (`minSdk = 26`, `targetSdk = 36`), with zero Google Play Services and optional opt-in live station comparison via Open-Meteo.

## Privacy & Security
- **Stateless by default**: Pressure logs, elevation, and alert preferences stay in memory only until the user explicitly enables **Save History & Settings on Phone (Opt-In)**.
- **Offline by default**: Zero network calls happen unless the user explicitly enables **Allow Online Weather Check (Opt-In)** and taps **Compare With Live Station**.
- **LineageOS / de-Googled ready**: Uses standard AOSP `SensorManager` (`TYPE_PRESSURE`), `ToneGenerator`, and `HttpsURLConnection` (Open-Meteo keyless public HTTPS API).

## Build Locally
1. Open `android_project/` in Android Studio or build from CLI with Gradle 9.3.1:
   `gradle assembleDebug`
2. Run unit & Robolectric tests:
   `gradle testDebugUnitTest`
