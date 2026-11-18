# Google Play Store Listing — StormGlass Local

- **Package ID:** `io.github.elizabackrooms.stormglasslocal`
- **App Name:** StormGlass Local
- **Category:** Weather / Utilities
- **Privacy Policy URL:** https://github.com/ElizaBackrooms/30-day-android-app-factory/blob/main/PRIVACY_POLICY.md

## Short Description (80 chars max)
Offline barometer & Zambretti storm forecaster with optional live station check.

## Full Description
**StormGlass Local** tracks air pressure directly from your phone's built-in barometer sensor (or via manual entry on phones without one) to warn you of approaching storms and squalls without needing internet access or Google Play Services.

### Key Features
- **Live Air Pressure & Trend Chart (Works 100% Offline):** View your current station pressure, 3-hour pressure change, and 3H / 12H / 24H pressure trace graph.
- **Plain-English Zambretti Weather Outlook:** Normalizes your reading to Sea-Level Pressure (MSLP) based on your altitude, wind direction, and season to give clear weather expectations plus a storm-readiness checklist.
- **Rapid Storm Drop Alarm:** Alerts you visually and audibly when pressure falls rapidly over 3 hours (customizable sensitivity).
- **Optional Opt-In Online Weather Check (Open-Meteo):** Disabled by default. Enter optional coordinates and tap **Compare With Live Weather Station** to verify outside conditions and surface pressure via Open-Meteo's keyless HTTPS API.
- **Privacy by Default (Zero Tracking):** Runs stateless and in memory unless you explicitly enable **Save History & Settings on Phone (Opt-In)**. Includes a 1-tap **Wipe All Local Data** button.

### Permissions & Hardware Notes
- **Internal Barometer (`android.hardware.sensor.barometer`, optional):** Marked `required="false"` — devices without a pressure sensor automatically switch to Manual Entry mode. Works smoothly on Moto G Stylus 5G (2023) / LineageOS.
- **Internet (`INTERNET`):** Dormant until you manually toggle **Allow Online Weather Check (Opt-In)** ON and tap **Compare With Live Weather Station**.

### Google Play Data Safety Summary
- **Data collected by developer:** None.
- **Data shared automatically:** None. When the user opts into **Allow Online Weather Check** and enters coordinates to tap **Compare With Live Weather Station**, only those manually typed coordinates are sent via HTTPS to Open-Meteo.
- **Local storage:** Stateless/in-memory by default. Pressure readings and settings persist locally only if the user opts in via **Save History & Settings on Phone (Opt-In)**, are excluded from cloud backup/device transfer, and can be wiped in one tap.
- **Analytics / Ads / Third-Party SDKs:** Zero. Zero Google Play Services dependencies.
