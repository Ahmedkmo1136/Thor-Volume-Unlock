# Thor Audio Unlocker (v6)

Ultra-lightweight Android speaker boost, per-app gain override, 19-band equalizer, stereo stage widener, and output limiter for the **AYN Thor** (zero third-party libraries, ~100 KB APK, minimal RAM).

## Disclaimer

> **This app is 100% vibecoded.** As the author, I have no coding experience and **no plans to maintain, fix, or update this project**. It is shared as-is in case other AYN Thor owners find it useful. Use at your own risk—especially when unlocking Extra dB Mode (+11 dB to +20 dB), which can distort audio or damage your speakers.

## Features
- **Global dB + Non-Conflicting Per-App Overrides**: Set a default Global dB (0–10 dB safe, optional up to +20 dB danger mode) for all apps, and add custom per-app overrides that replace Global dB automatically when you switch apps.
- **Guided Accessibility Setup**: Clearly prompts you to enable **Thor Audio Per-App Switcher** (`TYPE_WINDOW_STATE_CHANGED` only, 0% CPU during gameplay) before adding per-app overrides.
- **Full 19-Band Equalizer (`70 Hz` – `16 kHz`)**: Every band (`-32.0 dB` to `+12.0 dB` in `0.5 dB` steps) plus **EQ Makeup Gain** (`0` to `+15 dB`) is independently customizable.
- **Independent Stereo Stage & Output Limiter Controls**: Customize Stereo Stage Width (`0–100%`), Limiter Threshold (`-6.0` to `0.0 dB`), and Limiter Release (`10–500 ms`) freely without being locked to a profile.
- **Quick Profile Templates**: Load **Joey's Retro Handhelds Profile** (19-band curve, `40%` stereo width, `500 ms` limiter release, `+11 dB` makeup gain), **Wide Stage**, **Vocal Clear**, or **Flat** as starting points, then tweak any slider individually.

## Licenses & Credits

- **This Project (`Thor Audio Unlocker`)**: Licensed under the **MIT License** (see [`LICENSE`](LICENSE)). Provided "as is", without warranty of any kind.
- **Android Platform / SDK APIs**: Uses only standard Android framework APIs (`android.media.audiofx.DynamicsProcessing`, `Virtualizer`, `LoudnessEnhancer`, `AccessibilityService`, `TileService`), part of the Android Open Source Project under the **Apache License 2.0**. No third-party runtime dependencies are bundled in the APK.
- **Build Toolchain**: Built with **Gradle** (Apache License 2.0) and **Android Gradle Plugin** (Apache License 2.0).
- **EQ Profile Credit**: The built-in 19-band AYN Thor equalizer curve and limiter release settings are based on [Joey's Retro Handhelds' AYN Thor audio video](https://www.youtube.com/watch?v=kk5Q4DtMrME) and community tuning by **Amphi** and **JSmash97** on [r/AynThor](https://www.reddit.com/r/AynThor/comments/1p45t7j/you_need_to_change_your_audio_settings/).
