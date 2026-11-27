package com.thor.audiounlocker;

import android.content.Context;
import android.media.AudioDeviceCallback;
import android.media.AudioDeviceInfo;
import android.media.AudioManager;
import android.media.audiofx.DynamicsProcessing;
import android.media.audiofx.LoudnessEnhancer;
import android.os.Handler;
import android.os.Looper;

public final class AudioBoostEngine {

    public interface Listener {
        void onStatus(String statusLine, boolean active, boolean bypassed);
    }

    private static final int PRE_EQ_BANDS = Prefs.EQ_FREQS_HZ.length;
    private static final int MBC_BANDS = 3;
    private static final int POST_EQ_BANDS = 4;
    private static final float[] WIDENER_FREQS = { 4000f, 6300f, 10000f, 16000f };

    private final AudioManager audioManager;
    private final Prefs prefs;
    private final Listener listener;

    private DynamicsProcessing dp;
    private LoudnessEnhancer fallbackLe;
    private boolean callbackRegistered = false;
    private String currentForegroundPkg = null;

    // Track structural config of existing DynamicsProcessing to update in-place without audio pops
    private boolean dpHasPreEq = false;
    private boolean dpHasMbc = false;
    private boolean dpHasPostEq = false;
    private boolean dpHasLimiter = false;
    private String lastAppliedSignature = null;

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

    public void startListening() {
        if (!callbackRegistered && audioManager != null) {
            audioManager.registerAudioDeviceCallback(deviceCallback, new Handler(Looper.getMainLooper()));
            callbackRegistered = true;
        }
    }

    public void stop() {
        if (callbackRegistered && audioManager != null) {
            audioManager.unregisterAudioDeviceCallback(deviceCallback);
            callbackRegistered = false;
        }
        releaseAll();
        lastAppliedSignature = null;
        notifyListener("Off", false, false);
    }

    public void apply(String foregroundPkg) {
        if (foregroundPkg != null) {
            this.currentForegroundPkg = foregroundPkg;
        }

        String externalDevice = findExternalOutputName();

        if (!prefs.isEnabled()) {
            String sig = "OFF|" + externalDevice;
            if (sig.equals(lastAppliedSignature)) return;
            lastAppliedSignature = sig;
            releaseAll();
            notifyListener("Off (" + (externalDevice == null ? "Built-in Speakers" : externalDevice) + ")", false, false);
            return;
        }

        if (prefs.isSpeakerOnly() && externalDevice != null) {
            String sig = "BYPASS|" + externalDevice;
            if (sig.equals(lastAppliedSignature)) return;
            lastAppliedSignature = sig;
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
        boolean mbcOn = prefs.isMbcEnabled();
        boolean wideOn = prefs.isStereoWideEnabled();
        int widthPct = prefs.getStageWidthPercent();
        boolean limiterOn = prefs.isLimiterEnabled();
        float limiterThreshold = prefs.getLimiterThresholdDb();
        int limiterRelease = prefs.getLimiterReleaseMs();

        StringBuilder sigBuilder = new StringBuilder(128);
        sigBuilder.append("ON|").append(scopeLabel).append('|').append(gainDb).append('|')
            .append(eqOn).append('|').append(makeupDb).append('|')
            .append(mbcOn).append('|')
            .append(wideOn).append('|').append(widthPct).append('|')
            .append(limiterOn).append('|').append(limiterThreshold).append('|').append(limiterRelease);
        if (eqOn) {
            for (int i = 0; i < PRE_EQ_BANDS; i++) {
                sigBuilder.append('|').append(prefs.getEqBandDb(i));
            }
        }
        String sig = sigBuilder.toString();
        if (sig.equals(lastAppliedSignature) && (dp != null || fallbackLe != null)) {
            return;
        }

        boolean dpOk = applyDynamicsProcessing(
            gainDb, makeupDb, eqOn, mbcOn, wideOn, widthPct, limiterOn, limiterThreshold, limiterRelease
        );

        if (dpOk) {
            releaseLoudnessEnhancer();
            lastAppliedSignature = sig;
            String eqTag = eqOn ? " • 19-Band EQ" : "";
            String mbcTag = mbcOn ? " • MBC" : "";
            String stageTag = (wideOn && widthPct > 0) ? (" • Stage " + widthPct + "%") : "";
            String limTag = limiterOn ? (" • Lim " + limiterRelease + "ms") : "";
            notifyListener("[" + scopeLabel + "] +" + gainDb + " dB" + eqTag + mbcTag + stageTag + limTag, true, false);
        } else {
            boolean leOk = applyLoudnessEnhancer(gainDb * 100);
            if (leOk) {
                lastAppliedSignature = sig;
                notifyListener("[" + scopeLabel + "] +" + gainDb + " dB (Fallback)", true, false);
            } else {
                lastAppliedSignature = null;
                notifyListener("Error: Android rejected global audio effect", false, false);
            }
        }
    }

    private boolean applyDynamicsProcessing(
        int gainDb,
        int makeupDb,
        boolean eqOn,
        boolean mbcOn,
        boolean wideOn,
        int widthPct,
        boolean limiterOn,
        float limiterThresholdDb,
        int limiterReleaseMs
    ) {
        try {
            boolean usePostEq = wideOn && widthPct > 0;

            // Preamp headroom trim: subtract max positive PreEQ boost so PreEQ never clips into MBC/Limiter
            float eqShift = eqOn ? (float) makeupDb : 0.0f;
            float maxPositiveBoost = 0.0f;
            if (eqOn) {
                for (int i = 0; i < PRE_EQ_BANDS; i++) {
                    float bandTotal = prefs.getEqBandDb(i) + eqShift;
                    if (bandTotal > maxPositiveBoost) {
                        maxPositiveBoost = bandTotal;
                    }
                }
            }
            float inputGain = (float) gainDb - maxPositiveBoost;

            if (dp == null || dpHasPreEq != eqOn || dpHasMbc != mbcOn || dpHasPostEq != usePostEq || dpHasLimiter != limiterOn) {
                releaseDynamicsProcessing();
                DynamicsProcessing.Config.Builder b = new DynamicsProcessing.Config.Builder(
                    DynamicsProcessing.VARIANT_FAVOR_FREQUENCY_RESOLUTION,
                    2,
                    eqOn, eqOn ? PRE_EQ_BANDS : 0,
                    mbcOn, mbcOn ? MBC_BANDS : 0,
                    usePostEq, usePostEq ? POST_EQ_BANDS : 0,
                    limiterOn
                );
                b.setPreferredFrameDuration(10.0f);
                b.setInputGainAllChannelsTo(inputGain);

                if (eqOn) {
                    b.setPreEqAllChannelsTo(buildPreEq(eqShift));
                }
                if (mbcOn) {
                    b.setMbcAllChannelsTo(buildMbc());
                }
                if (usePostEq) {
                    b.setPostEqByChannelIndex(0, buildWidenerPostEq(widthPct, true));
                    b.setPostEqByChannelIndex(1, buildWidenerPostEq(widthPct, false));
                }
                if (limiterOn) {
                    b.setLimiterAllChannelsTo(buildLimiter(limiterThresholdDb, limiterReleaseMs));
                }

                dp = new DynamicsProcessing(Integer.MAX_VALUE, 0, b.build());
                dpHasPreEq = eqOn;
                dpHasMbc = mbcOn;
                dpHasPostEq = usePostEq;
                dpHasLimiter = limiterOn;
                dp.setEnabled(true);
                return true;
            }

            // Fast in-place parameter update (zero audio pop, zero native reallocation)
            dp.setInputGainAllChannelsTo(inputGain);
            if (eqOn) {
                dp.setPreEqAllChannelsTo(buildPreEq(eqShift));
            }
            if (mbcOn) {
                dp.setMbcAllChannelsTo(buildMbc());
            }
            if (usePostEq) {
                dp.setPostEqByChannelIndex(0, buildWidenerPostEq(widthPct, true));
                dp.setPostEqByChannelIndex(1, buildWidenerPostEq(widthPct, false));
            }
            if (limiterOn) {
                dp.setLimiterAllChannelsTo(buildLimiter(limiterThresholdDb, limiterReleaseMs));
            }
            if (!dp.getEnabled()) {
                dp.setEnabled(true);
            }
            return true;
        } catch (Throwable ignored) {
            releaseDynamicsProcessing();
            return false;
        }
    }

    private DynamicsProcessing.Eq buildPreEq(float eqShift) {
        DynamicsProcessing.Eq preEq = new DynamicsProcessing.Eq(true, true, PRE_EQ_BANDS);
        for (int i = 0; i < PRE_EQ_BANDS; i++) {
            float gain = clamp(prefs.getEqBandDb(i) + eqShift, -32f, 15f);
            preEq.setBand(i, new DynamicsProcessing.EqBand(true, Prefs.EQ_FREQS_HZ[i], gain));
        }
        return preEq;
    }

    private DynamicsProcessing.Mbc buildMbc() {
        DynamicsProcessing.Mbc mbc = new DynamicsProcessing.Mbc(true, true, MBC_BANDS);
        // Band 0: Low (0 - 250 Hz) -> controls bass excursion so small speakers don't rattle
        mbc.setBand(0, new DynamicsProcessing.MbcBand(
            true, 250f, 10f, 80f, 3.5f, -14f, 4f, -80f, 1.0f, 0f, 2.0f));
        // Band 1: Mid (250 Hz - 4 kHz) -> lifts quiet dialogue, retro synth, and SFX
        mbc.setBand(1, new DynamicsProcessing.MbcBand(
            true, 4000f, 15f, 100f, 2.5f, -18f, 4f, -80f, 1.0f, 0f, 4.0f));
        // Band 2: High (4 kHz - 20 kHz) -> adds air/presence without harsh sibilance
        mbc.setBand(2, new DynamicsProcessing.MbcBand(
            true, 20000f, 5f, 60f, 2.5f, -16f, 4f, -80f, 1.0f, 0f, 3.0f));
        return mbc;
    }

    private DynamicsProcessing.Eq buildWidenerPostEq(int widthPct, boolean isLeft) {
        float clampedWiden = Math.max(0f, Math.min(100f, (float) widthPct)) / 100f;
        float maxTiltDb = 2.5f * clampedWiden;
        DynamicsProcessing.Eq postEq = new DynamicsProcessing.Eq(true, true, POST_EQ_BANDS);
        for (int i = 0; i < POST_EQ_BANDS; i++) {
            float sign = (i % 2 == 0) ? 1f : -1f;
            if (!isLeft) sign = -sign;
            postEq.setBand(i, new DynamicsProcessing.EqBand(true, WIDENER_FREQS[i], sign * maxTiltDb));
        }
        return postEq;
    }

    private DynamicsProcessing.Limiter buildLimiter(float thresholdDb, int releaseMs) {
        return new DynamicsProcessing.Limiter(
            true, true, 0, 1.0f, (float) releaseMs, 15.0f, thresholdDb, 0.0f
        );
    }

    private boolean applyLoudnessEnhancer(int targetMb) {
        try {
            if (fallbackLe == null) {
                fallbackLe = new LoudnessEnhancer(0);
            }
            fallbackLe.setTargetGain(Math.max(0, Math.min(2000, targetMb)));
            if (!fallbackLe.getEnabled()) {
                fallbackLe.setEnabled(true);
            }
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
        dpHasPreEq = false;
        dpHasMbc = false;
        dpHasPostEq = false;
        dpHasLimiter = false;
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
