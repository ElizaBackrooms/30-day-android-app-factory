# Day 01 — DeadDrop Acoustic (Doomsday & Grid-Down Comms)

## 1. What We Are Building
**DeadDrop Acoustic** is an offline-first **300-baud Audio FSK data modem, CRC-16 survival packet encoder/decoder, and hardware Torch Morse beacon** written in pure Kotlin + Jetpack Compose via Google AI Studio (Build -> Android).

- **Tab 1 (`MODEM TX`)**: Encodes `[CALLSIGN] + TEXT / SOS_LOC (GNSS or manual grid) / PING` into a `0x55555555` + `0xAA55` + CRC-16-CCITT binary packet, previews the hex length/duration/CRC, transmits clean phase-continuous `1200 Hz / 2200 Hz` AFSK tones over speaker or walkie-talkie mic, and provides a **1-Tap Loopback Self-Test** on one device.
- **Tab 2 (`DECODER RX`)**: Runs a 7x-oversampled pure-Kotlin Goertzel filter over live `AudioRecord` PCM audio, visualizes real-time `1200 Hz` vs `2200 Hz` tone energy and a 48-bar Compose waveform, and validates incoming CRC-16 frames with SNR telemetry.
- **Tab 3 (`BEACON & LOG`)**: Flashes configurable `5–20 WPM` International Morse Code via `CameraManager.setTorchMode` (with an on-screen optical strobe mirror for flashless devices/emulators) plus a local `SharedPreferences` JSON audit log of TX/RX/Loopback packets.

## 2. Why We Should Build It
- **Real Grid-Down Problem:** When cell towers die (disasters, remote trails, congested arenas) or devices are air-gapped, standard messengers become bricks. Billions of analog FRS/GMRS/VHF walkie-talkies still work—and DeadDrop Acoustic lets any Android phone turn spoken static into verified digital text and GPS packets.
- **Zero-Slop Technical Edge:** Runs 100% offline in Airplane Mode with zero paid API keys, zero accounts, and zero Play Services dependencies.
- **Monetization / Niche Fit:** High-intent prepper, backcountry outdoor, ham/GMRS radio, and hardware-hacker Play Store appeal (Freemium core + optional `$4.99` Pro unlock for pre-shared passphrase scrambling, 1200-baud fast mode, and automated timed beacon bursts).

## 3. Critic Audit (Single-Shot Build Safety for Google AI Studio)
- Cut Room / KSP, Hilt/Koin, CameraX, Navigation-Compose, and `play-services-location` so Google AI Studio's single-module Gradle build compiles cleanly on the first attempt.
- Use `CameraManager.setTorchMode` directly (no `CAMERA` permission prompt needed) and native `LocationManager` with an instant manual coordinate fallback if GPS permission is denied.
- Zero allocations inside the hot Goertzel demodulator (`FloatArray(147)` circular buffer + 7 parallel shift-register phases) and throttled 20 FPS telemetry emission.
