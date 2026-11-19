# Thor Audio Unlocker v4 (Per-App Gain + Stereo Stage EQ)

Ultra-lightweight Android speaker boost + per-app gain + stereo stage widener for the **AYN Thor** (zero external dependencies, ~100 KB APK, minimal RAM).

## What's New in v4
- **Per-App Gain Mode**: Tap **+ Add App**, pick **DroidDeck** (or any game/emulator), set its exact dB slider, and enable **Per-App Boost Only** so all other apps stay at 0 dB standby.
- **Event-Driven App Switcher**: Uses a minimal `TYPE_WINDOW_STATE_CHANGED` Accessibility Service (`canRetrieveWindowContent="false"`) for 0% CPU overhead during gameplay.
- **0 to +10 dB Safe Gain** + **Extra dB Mode (Up to +20 dB)** with danger warning.
- **Stereo Stage Widener (0–100%)** + **5-Band Equalizer** + **Output Limiter (-0.5 dB)**.
