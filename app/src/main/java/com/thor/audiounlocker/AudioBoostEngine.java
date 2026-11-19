package com.thor.audiounlocker;

import android.content.Context;
import android.media.AudioDeviceCallback;
import android.media.AudioDeviceInfo;
import android.media.AudioManager;
import android.media.audiofx.DynamicsProcessing;
import android.media.audiofx.LoudnessEnhancer;
import android.media.audiofx.Virtualizer;
import android.os.Handler;
import android.os.Looper;

public final class AudioBoostEngine {

    public interface Listener {
        void onStatus(String statusLine, boolean active, boolean bypassed);
    }

    private final AudioManager audioManager;
    private final Prefs prefs;
    private final Listener listener;

    private DynamicsProcessing dp;
    private Virtualizer virtualizer;
    private LoudnessEnhancer fallbackLe;
    private boolean callbackRegistered = false;
    private String currentForegroundPkg = null;

    private final AudioDeviceCallback deviceCallback = new AudioDeviceCallback() {
        @Override
        public void onAudioDevicesAdded(AudioDeviceInfo[] addedDevices) {
            apply(currentForegroundPkg);
        }

        @Override
        public void onAudioDevicesRemoved(AudioDeviceInfo[] removedDevices) {
            apply(currentForegroundPkg);
        }
    };

    public AudioBoostEngine(Context context, Prefs prefs, Listener listener) {
        this.audioManager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
        this.prefs = prefs;
        this.listener = listener;
    }

    public void start() {
        if (!callbackRegistered && audioManager != null) {
            audioManager.registerAudioDeviceCallback(deviceCallback, new Handler(Looper.getMainLooper()));
            callbackRegistered = true;
        }
        apply(AppSwitchAccessibilityService.getLastRealPackage());
    }

    public void stop() {
        if (callbackRegistered && audioManager != null) {
            audioManager.unregisterAudioDeviceCallback(deviceCallback);
            callbackRegistered = false;
        }
        releaseAll();
        notifyListener("Off", false, false);
    }

    public void apply(String foregroundPkg) {
        if (foregroundPkg != null) {
            this.currentForegroundPkg = foregroundPkg;
        }

        String externalDevice = findExternalOutputName();

        if (!prefs.isEnabled()) {
            releaseAll();
            notifyListener("Off (" + (externalDevice == null ? "Built-in Speakers" : externalDevice) + ")", false, false);
            return;
        }

        if (prefs.isSpeakerOnly() && externalDevice != null) {
            releaseAll();
            notifyListener("Paused for " + externalDevice, false, true);
            return;
        }

        int gainDb = prefs.getGainDb();
        String scopeLabel = "Global";
        if (prefs.isPerAppOverridesEnabled() && this.currentForegroundPkg != null) {
            int overrideDb = prefs.getPerAppGainDb(this.currentForegroundPkg);
            if (overrideDb >= 0) {
                gainDb = overrideDb;
                scopeLabel = prefs.getPerAppLabel(this.currentForegroundPkg);
            }
        }

        boolean eqOn = prefs.isEqEnabled();
        int makeupDb = eqOn ? prefs.getEqMakeupDb() : 0;
        boolean wideOn = prefs.isStereoWideEnabled();
        int widthPct = prefs.getStageWidthPercent();
        boolean limiterOn = prefs.isLimiterEnabled();
        float limiterThreshold = prefs.getLimiterThresholdDb();
        int limiterRelease = prefs.getLimiterReleaseMs();

        boolean dpOk = applyDynamicsProcessing(
            gainDb, makeupDb, eqOn, wideOn, widthPct, limiterOn, limiterThreshold, limiterRelease
        );
        boolean virtOk = wideOn && widthPct > 0 && applyVirtualizer(widthPct);
        if (!wideOn || widthPct == 0) {
            releaseVirtualizer();
        }

        if (dpOk) {
            releaseLoudnessEnhancer();
            String eqTag = eqOn ? " • 19-Band EQ" : "";
            String stageTag = wideOn ? (" • Stage " + widthPct + "%") : "";
            String limTag = limiterOn ? (" • Lim " + limiterRelease + "ms") : "";
            notifyListener("[" + scopeLabel + "] +" + gainDb + " dB" + eqTag + stageTag + limTag, true, false);
        } else {
            boolean leOk = applyLoudnessEnhancer(gainDb * 100);
            if (leOk) {
                notifyListener("[" + scopeLabel + "] +" + gainDb + " dB (Fallback)", true, false);
            } else {
                notifyListener("Error: Android rejected global audio effect", false, false);
            }
        }
    }

    private boolean applyDynamicsProcessing(
        int gainDb,
        int makeupDb,
        boolean eqOn,
        boolean wideOn,
        int widthPct,
        boolean limiterOn,
        float limiterThresholdDb,
        int limiterReleaseMs
    ) {
        try {
            releaseDynamicsProcessing();

            boolean usePostEq = eqOn || (wideOn && widthPct > 0);
            int bandCount = Prefs.EQ_FREQS_HZ.length;

            DynamicsProcessing.Config.Builder b = new DynamicsProcessing.Config.Builder(
                DynamicsProcessing.VARIANT_FAVOR_FREQUENCY_RESOLUTION,
                2,
                false, 0,
                false, 0,
                usePostEq, usePostEq ? bandCount : 0,
                limiterOn
            );
            b.setPreferredFrameDuration(10.0f);
            b.setInputGainAllChannelsTo((float) (gainDb + makeupDb));

            if (usePostEq) {
                float tilt = (wideOn && widthPct > 0) ? (widthPct / 100.0f) * 2.0f : 0.0f;
                DynamicsProcessing.Eq eqLeft = new DynamicsProcessing.Eq(true, true, bandCount);
                DynamicsProcessing.Eq eqRight = new DynamicsProcessing.Eq(true, true, bandCount);

                for (int i = 0; i < bandCount; i++) {
                    float freq = Prefs.EQ_FREQS_HZ[i];
                    float base = eqOn ? prefs.getEqBandDb(i) : 0.0f;
                    float leftOffset = 0.0f;
                    if (freq >= 850f && freq <= 1860f) leftOffset = +tilt * 0.75f;
                    else if (freq >= 2800f && freq <= 5000f) leftOffset = -tilt * 0.75f;
                    else if (freq >= 9050f) leftOffset = +tilt * 0.75f;

                    eqLeft.setBand(i, new DynamicsProcessing.EqBand(true, freq, clamp(base + leftOffset, -36f, 15f)));
                    eqRight.setBand(i, new DynamicsProcessing.EqBand(true, freq, clamp(base - leftOffset, -36f, 15f)));
                }

                b.setPostEqByChannelIndex(0, eqLeft);
                b.setPostEqByChannelIndex(1, eqRight);
            }

            if (limiterOn) {
                DynamicsProcessing.Limiter lim = new DynamicsProcessing.Limiter(
                    true, true, 0, 1.0f, (float) limiterReleaseMs, 10.0f, limiterThresholdDb, 0.0f
                );
                b.setLimiterAllChannelsTo(lim);
            }

            dp = new DynamicsProcessing(Integer.MAX_VALUE, 0, b.build());
            dp.setEnabled(true);
            return true;
        } catch (Throwable ignored) {
            releaseDynamicsProcessing();
            return false;
        }
    }

    private boolean applyVirtualizer(int widthPct) {
        try {
            if (virtualizer == null) {
                virtualizer = new Virtualizer(Integer.MAX_VALUE, 0);
            }
            if (virtualizer.getStrengthSupported()) {
                short strength = (short) Math.max(0, Math.min(1000, widthPct * 10));
                virtualizer.setStrength(strength);
            }
            virtualizer.setEnabled(true);
            return true;
        } catch (Throwable ignored) {
            releaseVirtualizer();
            return false;
        }
    }

    private boolean applyLoudnessEnhancer(int targetMb) {
        try {
            if (fallbackLe == null) {
                fallbackLe = new LoudnessEnhancer(0);
            }
            fallbackLe.setTargetGain(Math.max(0, Math.min(2000, targetMb)));
            fallbackLe.setEnabled(true);
            return true;
        } catch (Throwable ignored) {
            releaseLoudnessEnhancer();
            return false;
        }
    }

    private String findExternalOutputName() {
        if (audioManager == null) return null;
        AudioDeviceInfo[] devices = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS);
        for (AudioDeviceInfo d : devices) {
            int type = d.getType();
            if (type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP
                || type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO
                || type == AudioDeviceInfo.TYPE_BLE_HEADSET
                || type == AudioDeviceInfo.TYPE_BLE_SPEAKER) {
                CharSequence name = d.getProductName();
                return (name != null && name.length() > 0) ? name.toString() : "Bluetooth Audio";
            }
            if (type == AudioDeviceInfo.TYPE_WIRED_HEADPHONES
                || type == AudioDeviceInfo.TYPE_WIRED_HEADSET) {
                return "3.5mm Headphones";
            }
            if (type == AudioDeviceInfo.TYPE_USB_DEVICE
                || type == AudioDeviceInfo.TYPE_USB_HEADSET) {
                return "USB-C Audio";
            }
            if (type == AudioDeviceInfo.TYPE_HDMI
                || type == AudioDeviceInfo.TYPE_HDMI_ARC) {
                return "HDMI Audio";
            }
        }
        return null;
    }

    private float clamp(float val, float min, float max) {
        return Math.max(min, Math.min(max, val));
    }

    private void releaseAll() {
        releaseDynamicsProcessing();
        releaseVirtualizer();
        releaseLoudnessEnhancer();
    }

    private void releaseDynamicsProcessing() {
        if (dp != null) {
            try {
                dp.setEnabled(false);
                dp.release();
            } catch (Throwable ignored) {
            }
            dp = null;
        }
    }

    private void releaseVirtualizer() {
        if (virtualizer != null) {
            try {
                virtualizer.setEnabled(false);
                virtualizer.release();
            } catch (Throwable ignored) {
            }
            virtualizer = null;
        }
    }

    private void releaseLoudnessEnhancer() {
        if (fallbackLe != null) {
            try {
                fallbackLe.setEnabled(false);
                fallbackLe.release();
            } catch (Throwable ignored) {
            }
            fallbackLe = null;
        }
    }

    private void notifyListener(String text, boolean active, boolean bypassed) {
        if (listener != null) {
            listener.onStatus(text, active, bypassed);
        }
    }
}
