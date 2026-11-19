package com.thor.audiounlocker;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class Prefs {
    public static final int SAFE_MAX_DB = 10;
    public static final int EXTRA_MAX_DB = 20;

    public static final float EQ_MIN_DB = -32.0f;
    public static final float EQ_MAX_DB = 12.0f;
    public static final int EQ_SLIDER_STEPS = 88; // 0.5 dB steps from -32.0 to +12.0

    public static final float[] EQ_FREQS_HZ = {
        70f, 110f, 120f, 200f, 300f, 444f, 600f, 700f, 850f,
        1200f, 1860f, 2800f, 3800f, 5000f, 7000f, 9050f, 11000f, 12000f, 16000f
    };

    public static final String[] EQ_LABELS = {
        "70 Hz", "110 Hz", "120 Hz", "200 Hz", "300 Hz", "444 Hz", "600 Hz", "700 Hz", "850 Hz",
        "1.2 kHz", "1.86 kHz", "2.8 kHz", "3.8 kHz", "5 kHz", "7 kHz", "9.05 kHz", "11 kHz", "12 kHz", "16 kHz"
    };

    // Preset templates (selecting a preset copies its values into the independent settings below)
    public static final float[] PRESET_JOEY_EQ = {
        -32.0f, -4.5f, -4.0f, -4.0f, -5.0f, -9.0f, -13.5f, -17.0f, -17.0f,
        -18.5f, -20.0f, -19.5f, -21.5f, -13.0f, -6.0f, -0.5f, 0.5f, -0.5f, -3.0f
    };
    public static final float[] PRESET_WIDE_EQ = {
        -6.0f, -2.0f, -1.0f, 0.5f, 1.0f, 1.0f, 1.5f, 2.0f, 2.0f,
        2.5f, 2.5f, 3.0f, 3.0f, 2.5f, 2.5f, 2.5f, 3.0f, 2.5f, 1.5f
    };
    public static final float[] PRESET_VOCAL_EQ = {
        -8.0f, -3.0f, -2.0f, 0.0f, 0.5f, 1.5f, 2.5f, 3.5f, 4.0f,
        4.0f, 3.5f, 3.0f, 2.5f, 1.5f, 1.0f, 0.5f, 0.5f, 0.0f, -1.0f
    };
    public static final float[] PRESET_FLAT_EQ = {
        0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f,
        0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f
    };

    private static final String NAME = "thor_boost_prefs_v71";
    private static final String KEY_PER_APP_PKGS = "per_app_packages";
    private final SharedPreferences sp;

    public Prefs(Context context) {
        this.sp = context.getApplicationContext().getSharedPreferences(NAME, Context.MODE_PRIVATE);
    }

    public boolean isEnabled() {
        return sp.getBoolean("enabled", true);
    }

    public void setEnabled(boolean v) {
        sp.edit().putBoolean("enabled", v).apply();
    }

    public boolean isExtraGainUnlocked() {
        return sp.getBoolean("extra_gain_unlocked", false);
    }

    public void setExtraGainUnlocked(boolean v) {
        SharedPreferences.Editor ed = sp.edit().putBoolean("extra_gain_unlocked", v);
        if (!v) {
            if (getGainDb() > SAFE_MAX_DB) {
                ed.putInt("gain_db", SAFE_MAX_DB);
            }
            for (String pkg : getPerAppPackages()) {
                int appDb = sp.getInt("per_app_db_" + pkg, 8);
                if (appDb > SAFE_MAX_DB) {
                    ed.putInt("per_app_db_" + pkg, SAFE_MAX_DB);
                }
            }
        }
        ed.apply();
    }

    public int getCurrentMaxDb() {
        return isExtraGainUnlocked() ? EXTRA_MAX_DB : SAFE_MAX_DB;
    }

    public int getGainDb() {
        int val = sp.getInt("gain_db", 8);
        return Math.max(0, Math.min(getCurrentMaxDb(), val));
    }

    public void setGainDb(int db) {
        int clamped = Math.max(0, Math.min(getCurrentMaxDb(), db));
        sp.edit().putInt("gain_db", clamped).apply();
    }

    // --- Per-App Overrides ---
    public boolean isPerAppOverridesEnabled() {
        return sp.getBoolean("per_app_overrides_enabled", true);
    }

    public void setPerAppOverridesEnabled(boolean v) {
        sp.edit().putBoolean("per_app_overrides_enabled", v).apply();
    }

    public List<String> getPerAppPackages() {
        Set<String> raw = sp.getStringSet(KEY_PER_APP_PKGS, null);
        if (raw == null) return new ArrayList<>();
        List<String> list = new ArrayList<>(raw);
        Collections.sort(list, (a, b) -> getPerAppLabel(a).compareToIgnoreCase(getPerAppLabel(b)));
        return list;
    }

    public void setPerAppGain(String pkg, String label, int db) {
        Set<String> existing = sp.getStringSet(KEY_PER_APP_PKGS, null);
        Set<String> updated = (existing == null) ? new HashSet<>() : new HashSet<>(existing);
        updated.add(pkg);
        int clamped = Math.max(0, Math.min(getCurrentMaxDb(), db));
        sp.edit()
            .putStringSet(KEY_PER_APP_PKGS, updated)
            .putString("per_app_label_" + pkg, label)
            .putInt("per_app_db_" + pkg, clamped)
            .apply();
    }

    public void removePerApp(String pkg) {
        Set<String> existing = sp.getStringSet(KEY_PER_APP_PKGS, null);
        if (existing == null) return;
        Set<String> updated = new HashSet<>(existing);
        updated.remove(pkg);
        sp.edit()
            .putStringSet(KEY_PER_APP_PKGS, updated)
            .remove("per_app_label_" + pkg)
            .remove("per_app_db_" + pkg)
            .apply();
    }

    public int getPerAppGainDb(String pkg) {
        if (pkg == null) return -1;
        Set<String> existing = sp.getStringSet(KEY_PER_APP_PKGS, null);
        if (existing == null || !existing.contains(pkg)) return -1;
        int val = sp.getInt("per_app_db_" + pkg, 8);
        return Math.max(0, Math.min(getCurrentMaxDb(), val));
    }

    public String getPerAppLabel(String pkg) {
        return sp.getString("per_app_label_" + pkg, pkg);
    }

    // --- Independent 19-Band Equalizer Settings ---
    public boolean isEqEnabled() {
        return sp.getBoolean("eq_enabled", true);
    }

    public void setEqEnabled(boolean v) {
        sp.edit().putBoolean("eq_enabled", v).apply();
    }

    public int getEqMakeupDb() {
        int val = sp.getInt("eq_makeup_db", 16);
        return Math.max(0, Math.min(20, val));
    }

    public void setEqMakeupDb(int db) {
        int clamped = Math.max(0, Math.min(20, db));
        sp.edit().putInt("eq_makeup_db", clamped).apply();
    }

    public float getEqBandDb(int index) {
        if (index < 0 || index >= EQ_FREQS_HZ.length) return 0f;
        float val = sp.getFloat("eq19_band_" + index, PRESET_JOEY_EQ[index]);
        return Math.max(EQ_MIN_DB, Math.min(EQ_MAX_DB, val));
    }

    public void setEqBandDb(int index, float db) {
        if (index < 0 || index >= EQ_FREQS_HZ.length) return;
        float clamped = Math.max(EQ_MIN_DB, Math.min(EQ_MAX_DB, db));
        sp.edit().putFloat("eq19_band_" + index, clamped).apply();
    }

    // --- Independent Stereo Widener Settings ---
    public boolean isStereoWideEnabled() {
        return sp.getBoolean("stereo_wide_enabled", true);
    }

    public void setStereoWideEnabled(boolean v) {
        sp.edit().putBoolean("stereo_wide_enabled", v).apply();
    }

    public int getStageWidthPercent() {
        int v = sp.getInt("stage_width_pct", 40);
        return Math.max(0, Math.min(100, v));
    }

    public void setStageWidthPercent(int pct) {
        int clamped = Math.max(0, Math.min(100, pct));
        sp.edit().putInt("stage_width_pct", clamped).apply();
    }

    // --- Independent Output Limiter Settings ---
    public boolean isLimiterEnabled() {
        return sp.getBoolean("limiter_enabled", true);
    }

    public void setLimiterEnabled(boolean v) {
        sp.edit().putBoolean("limiter_enabled", v).apply();
    }

    public float getLimiterThresholdDb() {
        float val = sp.getFloat("limiter_threshold_db", -0.1f);
        return Math.max(-6.0f, Math.min(0.0f, val));
    }

    public void setLimiterThresholdDb(float db) {
        float clamped = Math.max(-6.0f, Math.min(0.0f, db));
        sp.edit().putFloat("limiter_threshold_db", clamped).apply();
    }

    public int getLimiterReleaseMs() {
        int val = sp.getInt("limiter_release_ms", 500);
        return Math.max(10, Math.min(500, val));
    }

    public void setLimiterReleaseMs(int ms) {
        int clamped = Math.max(10, Math.min(500, ms));
        sp.edit().putInt("limiter_release_ms", clamped).apply();
    }

    // Apply a preset template into all independent settings
    public void applyPresetTemplate(
        float[] eqBands,
        int makeupDb,
        boolean stereoOn,
        int stageWidthPct,
        boolean limiterOn,
        float limiterThresholdDb,
        int limiterReleaseMs
    ) {
        SharedPreferences.Editor ed = sp.edit();
        ed.putBoolean("eq_enabled", true);
        ed.putInt("eq_makeup_db", Math.max(0, Math.min(20, makeupDb)));
        for (int i = 0; i < EQ_FREQS_HZ.length && i < eqBands.length; i++) {
            ed.putFloat("eq19_band_" + i, Math.max(EQ_MIN_DB, Math.min(EQ_MAX_DB, eqBands[i])));
        }
        ed.putBoolean("stereo_wide_enabled", stereoOn);
        ed.putInt("stage_width_pct", Math.max(0, Math.min(100, stageWidthPct)));
        ed.putBoolean("limiter_enabled", limiterOn);
        ed.putFloat("limiter_threshold_db", Math.max(-6.0f, Math.min(0.0f, limiterThresholdDb)));
        ed.putInt("limiter_release_ms", Math.max(10, Math.min(500, limiterReleaseMs)));
        ed.apply();
    }

    // --- System ---
    public boolean isSpeakerOnly() {
        return sp.getBoolean("speaker_only", true);
    }

    public void setSpeakerOnly(boolean v) {
        sp.edit().putBoolean("speaker_only", v).apply();
    }

    public boolean isStartOnBoot() {
        return sp.getBoolean("start_on_boot", true);
    }

    public void setStartOnBoot(boolean v) {
        sp.edit().putBoolean("start_on_boot", v).apply();
    }
}
