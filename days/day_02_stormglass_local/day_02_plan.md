# Day 02 — StormGlass Local (Barometric Weather & Squall Forecaster)

## 1. What We Are Building
**StormGlass Local** is an offline-first **barometric pressure trend logger, Negretti & Zambra (1915) Zambretti local forecaster, and rapid-drop squall alarm** built in pure Kotlin + Jetpack Compose via Google AI Studio (Build -> Android).

- **Moto G Stylus Hardware Reality:** Moto G Stylus 5G SKUs include `Sensor.TYPE_PRESSURE` while older 4G SKUs omit it. StormGlass auto-detects `Sensor.TYPE_PRESSURE` on startup (`HARDWARE SENSOR: ACTIVE`) and falls back gracefully to a tactile `+/- 0.1 / 1.0 hPa` manual entry stepper (for wrist altimeters / NOAA radio readings) so every feature works identically on all models.
- **Tab 1 (`Cockpit & Barograph`)**: Station Pressure + MSLP readout (`hPa`/`inHg`), hardware mode pill, rapid-drop squall alert banner (`ΔP ≤ -2.0 hPa / 3h`), 3h/12h/24h Compose Canvas trend graph, and 1-tap observation logger.
- **Tab 2 (`Zambretti Forecaster`)**: Altitude calibration (`0–3000m` MSLP formula), season & wind quadrant modifiers, Zambretti index (`Z = 1..26`) plain-text maritime forecast, and storm-readiness checklist.
- **Tab 3 (`Historical Log & Settings`)**: `SharedPreferences` persisted log (up to 100 entries), configurable rapid-drop threshold, `ToneGenerator` test alarm, 1-tap CSV export to clipboard, and 24h simulated squall demo generator.

## 2. Why We Should Build It
- **Grid-Down / Canyon Pain Point:** Cloud weather apps go dark without cell towers and carry tracking SDKs / GMS bloat. Barometric pressure changes are unjammable local physics that signal squalls and cold fronts 2–6 hours ahead.
- **Zero-GMS LineageOS Fit:** Zero internet permissions, zero Play Services, zero Room/KSP/Hilt dependencies.
- **Monetization / Niche Angle:** Strong appeal for LineageOS/GrapheneOS users, sailors, SAR crews, alpine hikers, and storm-preparedness communities (`$3.99` ad-free Pro Survival Utility).
