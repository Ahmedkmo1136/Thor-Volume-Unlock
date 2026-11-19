# Thor Audio Unlocker

Ultra-lightweight Android speaker boost, per-app gain override, 19-band equalizer, stereo stage widener, and output limiter for the **AYN Thor** (zero third-party libraries, \~100 KB APK, minimal RAM and 0% CPU overhead during gameplay).

## Disclaimer

> **This app is 100% vibecoded.** As the author, I have no coding experience and **no plans to maintain, fix, or update this project**. It is shared as-is in case other AYN Thor owners find it useful. Use at your own risk—especially when unlocking Extra dB Mode (+11 dB to +20 dB), which can distort audio or damage your speakers.

## Features

* **3-Tab Handheld-Friendly UI (`Gain & Apps`, `EQ & Sound`, `Setup & System`)**: Fixed top master power switch with live status badge (`ACTIVE` / `PAUSED` / `OFF`) and an **Apply & Close UI** button that frees UI memory while the background service keeps running.

* **Global dB + Non-Conflicting Per-App Overrides**: Set a default **Global Speaker Gain** (`0` to `+10 dB` safe range, plus an optional **Extra dB Mode up to `+20 dB`** with a danger warning). Add any app to **Per-App dB Overrides** to automatically replace Global dB when that app is open—never stacking or conflicting.

* **19-Band Equalizer (`70 Hz` – `16 kHz`, `-12.0` to `+12.0 dB`)**: Starts on **Normal (Default)** flat EQ with centered sliders (`0.5 dB` steps) and independent **EQ Makeup Gain** (`0` to `+12 dB`).

* **6 Equal-Sized Profile Templates**:

  * **Normal (Default)** — clean flat response for pure volume boost

  * **Stereo Widening (Lite Joey)** — light tonal shaping with `55%` stereo stage width

  * **Similar to Joey Retro Profile** — Android IIR-adapted version of Joey's Retro Handhelds Thor curve with `40%` stereo width and `500 ms` limiter release (re-scaled so it stays loud without crushing audio)

  * **Bass & Punch** — fuller handheld low-end without sub-bass speaker rattle

  * **Vocal & Dialogue** — brings speech and RPG dialogue forward

  * **Crisp Retro** — added upper clarity for retro and indie games

* **Fully Independent Sound Controls**: Profiles only act as starting templates—**Stereo Stage Widener** (`0–100%`), **Output Limiter** (threshold `-6.0` to `0.0 dB`, release `10–500 ms`), and all 19 EQ sliders stay independently customizable.

* **Zero-Pop, Low-Overhead Audio Engine**: Updates Android's native `DynamicsProcessing` in place without tearing down the audio session, skips redundant updates, and uses an event-driven Accessibility Service (`TYPE_WINDOW_STATE_CHANGED` only, `canRetrieveWindowContent="false"`) that sleeps at 0% CPU during gameplay.

* **Quick Controls & Protection**: Persistent notification with **Turn Off / Turn On** and **Exit** buttons, Quick Settings **Thor Boost** tile, **Auto-Bypass** for Headphones/USB-C/HDMI/Bluetooth, and **Auto-Start on Boot**.

## Prerequisites & Setup

1. **Notification Permission (Android 13+)**: Grant when prompted (or in the **Setup & System** tab) so Android keeps the background audio service alive.

2. **Accessibility Service (Only for Per-App dB Overrides)**: In the **Gain & Apps** or **Setup & System** tab, open Accessibility Settings and enable **Thor Audio Per-App Switcher** before adding per-app overrides.

3. **Unrestricted Battery (Recommended)**: In Android App Info → Battery, set to **Unrestricted** so the OS does not kill the service mid-game.

## Licenses & Credits

* **This Project (`Thor Audio Unlocker`)**: Licensed under the **MIT License** (see [LICENSE](https://www.google.com/search?q=LICENSE)). Provided "as is", without warranty of any kind.

* **Android Platform / SDK APIs**: Uses only standard Android framework APIs (`android.media.audiofx.DynamicsProcessing`, `Virtualizer`, `LoudnessEnhancer`, `AccessibilityService`, `TileService`), part of the Android Open Source Project under the **Apache License 2.0**. No third-party runtime dependencies are bundled in the APK.

* **Build Toolchain**: Built with **Gradle** (Apache License 2.0) and **Android Gradle Plugin** (Apache License 2.0).

* **EQ Inspiration & Credits**: The **Similar to Joey Retro Profile** and **Stereo Widening (Lite Joey)** profiles are inspired by [Joey's Retro Handhelds' AYN Thor audio video](https://www.youtube.com/watch?v=kk5Q4DtMrME) and community tuning by **Amphi** and **JSmash97** on [r/AynThor](https://www.reddit.com/r/AynThor/comments/1p45t7j/you_need_to_change_your_audio_settings/).