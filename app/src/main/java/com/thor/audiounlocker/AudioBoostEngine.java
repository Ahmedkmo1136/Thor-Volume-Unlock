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

    private final AudioManager audioManager;
    private final Prefs prefs;
    private final Listener listener;

    private DynamicsProcessing dp;
    private LoudnessEnhancer le;
    private boolean callbackRegistered = false;

    private final AudioDeviceCallback deviceCallback = new AudioDeviceCallback() {
        @Override
        public void onAudioDevicesAdded(AudioDeviceInfo[] addedDevices) {
            apply();
        }

        @Override
        public void onAudioDevicesRemoved(AudioDeviceInfo[] removedDevices) {
            apply();
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
        apply();
    }

    public void stop() {
        if (callbackRegistered && audioManager != null) {
            audioManager.unregisterAudioDeviceCallback(deviceCallback);
            callbackRegistered = false;
        }
        releaseAll();
        notifyListener("Off", false, false);
    }

    public void apply() {
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
        boolean limiterOn = prefs.isLimiterEnabled();
        boolean overdrive = prefs.isOverdriveStack();

        boolean dpOk = applyDynamicsProcessing(gainDb, limiterOn);
        boolean leOk = false;

        if (!dpOk) {
            // Fallback: LoudnessEnhancer handles the full boost
            leOk = applyLoudnessEnhancer(Math.min(3500, gainDb * 100));
        } else if (overdrive && gainDb > 0) {
            // Stack LoudnessEnhancer on top of DynamicsProcessing to punch past OEM HAL caps
            int extraMb = Math.min(2500, (gainDb * 100) / 2);
            leOk = applyLoudnessEnhancer(extraMb);
        } else {
            releaseLoudnessEnhancer();
        }

        if (dpOk && leOk) {
            notifyListener("Active: +" + gainDb + " dB (DP + Overdrive)", true, false);
        } else if (dpOk) {
            notifyListener("Active: +" + gainDb + " dB (DynamicsProcessing)", true, false);
        } else if (leOk) {
            notifyListener("Active: +" + gainDb + " dB (LoudnessEnhancer)", true, false);
        } else {
            notifyListener("Error: Android rejected global audio effect", false, false);
        }
    }

    private boolean applyDynamicsProcessing(int gainDb, boolean limiterOn) {
        try {
            releaseDynamicsProcessing();
            DynamicsProcessing.Config.Builder b = new DynamicsProcessing.Config.Builder(
                DynamicsProcessing.VARIANT_FAVOR_FREQUENCY_RESOLUTION,
                2,
                false, 0,
                false, 0,
                false, 0,
                limiterOn
            );
            b.setPreferredFrameDuration(10.0f);
            b.setInputGainAllChannelsTo((float) gainDb);

            if (limiterOn) {
                DynamicsProcessing.Limiter lim = new DynamicsProcessing.Limiter(
                    true, true, 0, 1.0f, 50.0f, 10.0f, -0.5f, 0.0f
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

    private boolean applyLoudnessEnhancer(int targetMb) {
        try {
            if (le == null) {
                le = new LoudnessEnhancer(0);
            }
            le.setTargetGain(Math.max(0, targetMb));
            le.setEnabled(true);
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
    }

    private void releaseLoudnessEnhancer() {
        if (le != null) {
            try {
                le.setEnabled(false);
                le.release();
            } catch (Throwable ignored) {
            }
            le = null;
        }
    }

    private void notifyListener(String text, boolean active, boolean bypassed) {
        if (listener != null) {
            listener.onStatus(text, active, bypassed);
        }
    }
}
