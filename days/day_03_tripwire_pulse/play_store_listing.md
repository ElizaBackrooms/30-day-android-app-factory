# Google Play Store Listing - Tripwire Pulse

- **Package ID:** `io.github.elizabackrooms.tripwirepulse`
- **App Name:** Tripwire Pulse
- **Category:** Tools / Utilities
- **Privacy Policy URL:** https://github.com/ElizaBackrooms/30-day-android-app-factory/blob/main/PRIVACY_POLICY.md

## Short Description (80 chars max)
Tripwire Pulse: offline-first Android utility with opt-in online tools.

## Full Description
**Tripwire Pulse** is an offline-first perimeter sentry utility designed for everyday users and de-Googled LineageOS devices (including Moto G Stylus 5G 2023) with zero Google Play Services dependencies.

### Key Features
- **Core Hardware & Sensor Utility:** 3-axis accelerometer geophone (`TYPE_LINEAR_ACCELERATION`) + mic dB gate + live Compose seismograph.
- **Optional Opt-In Online Mode:** Live Ntfy.sh / Discord / custom HTTPS webhook push alert over Wi-Fi/cellular on intrusion trigger + encrypted alert payload log (disabled by default; connects via HTTPS only when explicitly enabled by the user).
- **Plain-English Interface:** Simple, everyday labels with 1-line helper explanations and a built-in Help & Privacy tab.
- **Privacy by Default (Stateless):** Saves nothing to disk and makes zero network requests unless you explicitly opt in. Includes a 1-tap **Wipe All Local Data** button.

## Google Play Data Safety Summary
- **Data collected automatically:** None.
- **Data shared automatically:** None.
- **Optional Opt-In Microphone (`RECORD_AUDIO`):** Used solely for local, on-device decibel level thresholding when the Mic Gate is enabled; audio is never recorded, stored, or uploaded.
- **Optional Opt-In Network (`INTERNET`):** Disabled by default. When explicitly enabled by the user, intrusion alert payloads are transmitted over HTTPS exclusively to the user's configured Ntfy.sh, Discord, or custom HTTPS webhook endpoint.
- **Local storage:** In-memory only by default; optional on-device encrypted alert log (Android Keystore AES-GCM in `MODE_PRIVATE`) is excluded from cloud backup/device transfer and removable in one tap.
