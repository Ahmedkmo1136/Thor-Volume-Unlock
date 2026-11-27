package com.thor.audiounlocker;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class Prefs {
    public static final String PROF_NORMAL = "normal";
    public static final String PROF_LITER_JOEY = "liter_joey";
    public static final String PROF_JOEY = "joey";
    public static final String PROF_BASS = "bass";
    public static final String PROF_VOCAL = "vocal";
    public static final String PROF_CRISP = "crisp";
    public static final String PROF_THOR_CLEAN_LOUD = "thor_clean_loud";
    public static final String PROF_CUSTOM = "custom";

    public static final int SAFE_MAX_DB = 10;
    public static final int EXTRA_MAX_DB = 20;

    public static final float EQ_MIN_DB = -12.0f;
    public static final float EQ_MAX_DB = 12.0f;
    public static final int EQ_SLIDER_STEPS = 48; // 0.5 dB steps from -12.0 to +12.0 (0 dB centered)

    public static final float[] EQ_FREQS_HZ = {
        70f, 110f, 120f, 200f, 300f, 444f, 600f, 700f, 850f,
        1200f, 1860f, 2800f, 3800f, 5000f, 7000f, 9050f, 11000f, 12000f, 16000f
    };

    public static final String[] EQ_LABELS = {
        "70 Hz", "110 Hz", "120 Hz", "200 Hz", "300 Hz", "444 Hz", "600 Hz", "700 Hz", "850 Hz",
        "1.2 kHz", "1.86 kHz", "2.8 kHz", "3.8 kHz", "5 kHz", "7 kHz", "9.05 kHz", "11 kHz", "12 kHz", "16 kHz"
    };

    // 1. Normal (Flat) - Default
    public static final float[] PRESET_NORMAL_EQ = {
        0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f,
        0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f
    };

    // 2. Similar to Joey Retro Profile (re-scaled cleanly for Android IIR EQ without crushing sound)
    public static final float[] PRESET_SIMILAR_JOEY_EQ = {
        -8.0f, 2.5f, 3.0f, 3.0f, 2.0f, 0.0f, -2.0f, -3.5f, -3.5f,
        -4.5f, -5.0f, -4.5f, -5.5f, -2.0f, 1.0f, 3.5f, 4.5f, 3.5f, 2.0f
    };

    // 3. Stereo Widening (Lite Joey) - lighter tonal touch focused on open stereo stage
    public static final float[] PRESET_STEREO_LITE_EQ = {
        -4.0f, 1.5f, 1.5f, 1.5f, 1.0f, 0.0f, -1.0f, -1.5f, -1.5f,
        -2.0f, -2.0f, -1.5f, -2.0f, -0.5f, 1.0f, 2.5f, 3.0f, 2.5f, 1.5f
    };

    // 4. Bass & Punch (warm handheld low-end without sub-bass rattle)
    public static final float[] PRESET_BASS_PUNCH_EQ = {
        -4.0f, 4.0f, 4.5f, 4.0f, 2.5f, 1.0f, 0.0f, -0.5f, -0.5f,
        0.0f, 0.5f, 1.0f, 1.5f, 1.5f, 2.0f, 2.0f, 2.0f, 1.5f, 1.0f
    };

    // 5. Vocal & Dialogue (brings speech & RPG narration forward)
    public static final float[] PRESET_VOCAL_EQ = {
        -5.0f, -2.0f, -1.5f, -0.5f, 0.5f, 1.5f, 2.5f, 3.5f, 4.0f,
        4.5f, 4.0f, 3.5f, 2.5f, 1.5f, 1.0f, 0.5f, 0.5f, 0.0f, -0.5f
    };

    // 6. Crisp Retro (clean clarity for retro & indie games)
    public static final float[] PRESET_CRISP_RETRO_EQ = {
        -3.0f, 2.0f, 2.0f, 1.5f, 1.0f, 0.5f, 0.0f, 0.0f, 0.5f,
        1.0f, 1.5f, 2.0f, 2.5f, 3.0f, 3.5f, 3.5f, 4.0f, 3.0f, 2.0f
    };

    // 7. Thor Clean Loud (Loud, wide, zero-clipping curve tuned for AYN Thor speakers)
    public static final float[] PRESET_THOR_CLEAN_LOUD_EQ = {
        -6.0f, -3.0f, -1.5f, 1.5f, 2.0f, 0.0f, -1.0f, -0.5f, 0.5f,
        1.5f, 2.5f, 3.0f, 2.5f, 1.0f, 0.0f, 1.5f, 2.0f, 1.5f, 1.0f
    };

    private static final String NAME = "thor_boost_prefs_v72";
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
        int val = sp.getInt("gain_db", 6);
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

    // --- Independent 19-Band Equalizer Settings (Default: Normal / Flat) ---
    public boolean isEqEnabled() {
        return sp.getBoolean("eq_enabled", true);
    }

    public void setEqEnabled(boolean v) {
        sp.edit().putBoolean("eq_enabled", v).apply();
    }

    public int getEqMakeupDb() {
        int val = sp.getInt("eq_makeup_db", 0);
        return Math.max(0, Math.min(12, val));
    }

    public void setEqMakeupDb(int db) {
        int clamped = Math.max(0, Math.min(12, db));
        sp.edit().putInt("eq_makeup_db", clamped).apply();
    }

    public float getEqBandDb(int index) {
        if (index < 0 || index >= EQ_FREQS_HZ.length) return 0f;
        float val = sp.getFloat("eq19_band_" + index, PRESET_NORMAL_EQ[index]);
        return Math.max(EQ_MIN_DB, Math.min(EQ_MAX_DB, val));
    }

    public void setEqBandDb(int index, float db) {
        if (index < 0 || index >= EQ_FREQS_HZ.length) return;
        float clamped = Math.max(EQ_MIN_DB, Math.min(EQ_MAX_DB, db));
        sp.edit().putFloat("eq19_band_" + index, clamped).apply();
    }

    // --- Independent Stereo Widener Settings ---
    public boolean isStereoWideEnabled() {
        return sp.getBoolean("stereo_wide_enabled", false);
    }

    public void setStereoWideEnabled(boolean v) {
        sp.edit().putBoolean("stereo_wide_enabled", v).apply();
    }

    public int getStageWidthPercent() {
        int v = sp.getInt("stage_width_pct", 0);
        return Math.max(0, Math.min(100, v));
    }

    public void setStageWidthPercent(int pct) {
        int clamped = Math.max(0, Math.min(100, pct));
        sp.edit().putInt("stage_width_pct", clamped).apply();
    }

    // --- Multiband Compressor (MBC) ---
    public boolean isMbcEnabled() {
        return sp.getBoolean("mbc_enabled", true);
    }

    public void setMbcEnabled(boolean v) {
        sp.edit().putBoolean("mbc_enabled", v).apply();
    }

    // --- Independent Output Limiter Settings ---
    public boolean isLimiterEnabled() {
        return sp.getBoolean("limiter_enabled", true);
    }

    public void setLimiterEnabled(boolean v) {
        sp.edit().putBoolean("limiter_enabled", v).apply();
    }

    public float getLimiterThresholdDb() {
        float val = sp.getFloat("limiter_threshold_db", -1.5f);
        return Math.max(-6.0f, Math.min(0.0f, val));
    }

    public void setLimiterThresholdDb(float db) {
        float clamped = Math.max(-6.0f, Math.min(0.0f, db));
        sp.edit().putFloat("limiter_threshold_db", clamped).apply();
    }

    public int getLimiterReleaseMs() {
        int val = sp.getInt("limiter_release_ms", 60);
        return Math.max(10, Math.min(500, val));
    }

    public void setLimiterReleaseMs(int ms) {
        int clamped = Math.max(10, Math.min(500, ms));
        sp.edit().putInt("limiter_release_ms", clamped).apply();
    }

    public String getActiveProfile() {
        return sp.getString("active_profile", PROF_NORMAL);
    }

    public void setActiveProfile(String profileId) {
        sp.edit().putString("active_profile", profileId).apply();
    }

    public void saveCurrentAsCustom() {
        SharedPreferences.Editor ed = sp.edit();
        ed.putString("active_profile", PROF_CUSTOM);
        ed.putBoolean("custom_saved", true);
        ed.putBoolean("custom_eq_enabled", isEqEnabled());
        ed.putInt("custom_eq_makeup_db", getEqMakeupDb());
        for (int i = 0; i < EQ_FREQS_HZ.length; i++) {
            ed.putFloat("custom_eq19_" + i, getEqBandDb(i));
        }
        ed.putBoolean("custom_stereo_on", isStereoWideEnabled());
        ed.putInt("custom_stage_width", getStageWidthPercent());
        ed.putBoolean("custom_mbc_on", isMbcEnabled());
        ed.putBoolean("custom_limiter_on", isLimiterEnabled());
        ed.putFloat("custom_limiter_thresh", getLimiterThresholdDb());
        ed.putInt("custom_limiter_rel", getLimiterReleaseMs());
        ed.apply();
    }

    public void restoreCustomIfSaved() {
        if (!sp.getBoolean("custom_saved", false)) {
            setActiveProfile(PROF_CUSTOM);
            return;
        }
        SharedPreferences.Editor ed = sp.edit();
        ed.putString("active_profile", PROF_CUSTOM);
        ed.putBoolean("eq_enabled", sp.getBoolean("custom_eq_enabled", isEqEnabled()));
        ed.putInt("eq_makeup_db", sp.getInt("custom_eq_makeup_db", getEqMakeupDb()));
        for (int i = 0; i < EQ_FREQS_HZ.length; i++) {
            ed.putFloat("eq19_band_" + i, sp.getFloat("custom_eq19_" + i, getEqBandDb(i)));
        }
        ed.putBoolean("stereo_wide_enabled", sp.getBoolean("custom_stereo_on", isStereoWideEnabled()));
        ed.putInt("stage_width_pct", sp.getInt("custom_stage_width", getStageWidthPercent()));
        ed.putBoolean("mbc_enabled", sp.getBoolean("custom_mbc_on", isMbcEnabled()));
        ed.putBoolean("limiter_enabled", sp.getBoolean("custom_limiter_on", isLimiterEnabled()));
        ed.putFloat("limiter_threshold_db", sp.getFloat("custom_limiter_thresh", getLimiterThresholdDb()));
        ed.putInt("limiter_release_ms", sp.getInt("custom_limiter_rel", getLimiterReleaseMs()));
        ed.apply();
    }

    public void applyPresetTemplate(
        String profileId,
        float[] eqBands,
        int makeupDb,
        boolean mbcOn,
        boolean stereoOn,
        int stageWidthPct,
        boolean limiterOn,
        float limiterThresholdDb,
        int limiterReleaseMs
    ) {
        SharedPreferences.Editor ed = sp.edit();
        ed.putString("active_profile", profileId);
        ed.putBoolean("eq_enabled", true);
        ed.putInt("eq_makeup_db", Math.max(0, Math.min(12, makeupDb)));
        for (int i = 0; i < EQ_FREQS_HZ.length && i < eqBands.length; i++) {
            ed.putFloat("eq19_band_" + i, Math.max(EQ_MIN_DB, Math.min(EQ_MAX_DB, eqBands[i])));
        }
        ed.putBoolean("mbc_enabled", mbcOn);
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
