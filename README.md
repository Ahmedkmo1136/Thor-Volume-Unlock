# Thor Audio Unlocker v2 (Zero-Bloat Edition)

Ultra-lightweight Android speaker boost service for the **AYN Thor** with **zero external libraries** (pure Android framework Java, ~80 KB APK, minimal RAM footprint).

## Features
- **0 dB to +40 dB Gain Slider** with quick-jump buttons (+10, +20, +30, +40 dB)
- **Overdrive Stack**: Combines `DynamicsProcessing` + `LoudnessEnhancer` on global session 0 to push past OEM dB caps
- **Auto-Start on Boot**: Restores boost automatically via `BOOT_COMPLETED`
- **Near-Zero RAM / CPU Overhead**: No Jetpack Compose or AndroidX runtime; tap **Apply & Close UI** to unload the UI completely while the native `audioserver` DSP stays active
- **Output Limiter Toggle**: Off by default for unrestricted loudness; toggle on to reduce clipping
- **Auto-Bypass on Headphones / Bluetooth** + Quick Settings Tile

## Easiest Way to Build the APK on PC (No Software to Install)
1. Unzip this file.
2. Open https://github.com/new on your PC, create a new repo (check "Add a README file").
3. Click **Add file -> Upload files**.
4. Open the unzipped `ThorAudioUnlocker` folder, press `Ctrl+A`, and drag **all files and folders** (including `.github`) into the browser, then click **Commit changes**.
5. Click the **Actions** tab, wait ~1 minute, click the finished run, and download **ThorAudioUnlocker-APK** at the bottom.
