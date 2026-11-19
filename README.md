# Thor Audio Unlocker

Ultra-lightweight Android speaker boost, per-app gain override, and stereo stage equalizer for the **AYN Thor** (zero third-party libraries, ~100 KB APK, minimal RAM).

## Disclaimer

> **This app is 100% vibecoded.** As the author, I have no coding experience and **no plans to maintain, fix, or update this project**. It is shared as-is in case other AYN Thor owners find it useful. Use at your own risk—especially when unlocking Extra dB Mode (+11 dB to +20 dB), which can distort audio or damage your speakers.

## Features
- **Global dB + Non-Conflicting Per-App Overrides**: Set a default Global dB (e.g. `+2 dB`) for all apps, and add custom per-app overrides (e.g. `+10 dB` for a specific app). Listed apps automatically replace Global dB while in the foreground and revert cleanly when you switch back.
- **Guided Accessibility Setup**: Prompts you to enable **Thor Audio Per-App Switcher** in Android Accessibility Settings (`TYPE_WINDOW_STATE_CHANGED` only, 0% CPU during gameplay) before adding per-app overrides.
- **Joey's Retro Handhelds Thor Profile**: Built-in 19-band EQ curve (`70 Hz` – `16 kHz`) + `500 ms` limiter release + wide stereo stage based on Joey's Retro Handhelds AYN Thor audio guide, plus customizable **5-Band EQ** (`Wide Stage`, `Vocal Clear`, `Flat`, and `Custom`).
- **Safe 0 to +10 dB Slider** + **Extra dB Mode (Up to +20 dB)** with a distortion/speaker danger warning.
- **Auto-Start on Boot**, **Auto-Bypass on Headphones/Bluetooth**, and **Quick Settings Tile**.

## Licenses & Credits

- **This Project (`Thor Audio Unlocker`)**: Licensed under the **MIT License** (see [`LICENSE`](LICENSE)). Provided "as is", without warranty of any kind.
- **Android Platform / SDK APIs**: Uses only standard Android framework APIs (`android.media.audiofx.DynamicsProcessing`, `Virtualizer`, `LoudnessEnhancer`, `AccessibilityService`, `TileService`), part of the Android Open Source Project under the **Apache License 2.0**. No third-party runtime dependencies are bundled in the APK.
- **Build Toolchain**: Built with **Gradle** (Apache License 2.0) and **Android Gradle Plugin** (Apache License 2.0).
- **EQ Profile Credit**: The built-in 19-band AYN Thor equalizer curve and limiter release settings are based on [Joey's Retro Handhelds' AYN Thor audio video](https://www.youtube.com/watch?v=kk5Q4DtMrME) and community tuning by **Amphi** and **JSmash97** on [r/AynThor](https://www.reddit.com/r/AynThor/comments/1p45t7j/you_need_to_change_your_audio_settings/).
