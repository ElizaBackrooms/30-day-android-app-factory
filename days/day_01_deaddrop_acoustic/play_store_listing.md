# Google Play Store Listing — DeadDrop Acoustic

- **Package ID:** `io.github.elizabackrooms.deaddropacoustic`
- **App Name:** DeadDrop Acoustic
- **Category:** Tools / Communication
- **Privacy Policy URL:** https://github.com/ElizaBackrooms/30-day-android-app-factory/blob/main/PRIVACY_POLICY.md

## Short Description (80 chars max)
Send text via phone speaker tones & flashlight Morse — offline-first, no ads.

## Full Description
**DeadDrop Acoustic** turns short text notes, quick check-ins, and emergency GPS coordinates into 300-baud audio tones (1200/2200 Hz BFSK with CRC-16 error checking) that another Android phone can hear and decode through its microphone — with zero cell service, Wi-Fi, Bluetooth pairing, or cloud servers.

### Key Features
- **Plain-English Sound Messaging (Send & Listen):** Type a short message, press **Play Sound Message**, and any nearby phone in **Listen** mode decodes and verifies the message in real time.
- **Built-In Offline Self-Test:** Verify the encoder and decoder in one tap directly on one device.
- **Flashlight Morse Beacon:** Flash any A–Z / 0–9 message using your camera LED flash (or automatic screen strobe fallback on devices without a flash).
- **Optional Opt-In Online Relay:** Disabled by default. Users who want an online backup copy can explicitly turn on **Online Relay (Opt-In)** and configure their own HTTPS endpoint (such as a random private ntfy.sh room) with built-in SSRF private-IP protection.
- **Privacy by Default (Zero Tracking):** Runs stateless and in memory unless you explicitly switch **Save Message Log on Phone (Opt-In)** to ON. Includes a 1-tap **Wipe All Local Data** button.

### Permissions & Rationale
- **Microphone (`RECORD_AUDIO`):** Used strictly while **Start Listening** is active to decode incoming sound tones in real time. Audio is never saved to disk or uploaded.
- **Location (`ACCESS_FINE_LOCATION` / `ACCESS_COARSE_LOCATION`):** Requested only if you explicitly tap **Add My GPS** to insert coordinates into a message. Never accessed in the background.
- **Internet (`INTERNET`):** Dormant until you explicitly enable **Online Relay (Opt-In)** and tap **Send Copy Online Now** or **Check Inbox**.
- **Camera Flash (`android.hardware.camera.flash`, optional):** Used for hardware Morse code signaling (`required="false"`).

### Google Play Data Safety Summary
- **Data collected by developer:** None.
- **Data shared automatically:** None. If the user manually turns on Optional Online Relay and taps Send Copy Online Now, the message payload is posted strictly to the HTTPS URL chosen by the user.
- **Local storage:** Stateless/in-memory by default. Message audit logs are written to private app storage only when the user opts in via **Save Message Log on Phone (Opt-In)**, excluded from cloud backup/device transfer, and removable in one tap via **Wipe All Local Data**.
- **Analytics / Ads / Third-Party SDKs:** Zero. Zero Google Play Services dependencies (`LineageOS` / de-Googled Android compatible).
