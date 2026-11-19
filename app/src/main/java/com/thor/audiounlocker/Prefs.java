package com.thor.audiounlocker;

import android.content.Context;
import android.content.SharedPreferences;

public final class Prefs {
    public static final int SAFE_MAX_DB = 10;
    public static final int EXTRA_MAX_DB = 20;
    public static final int EQ_MIN_DB = -12;
    public static final int EQ_MAX_DB = 12;

    public static final float[] EQ_CUTOFFS_HZ = { 90f, 300f, 1000f, 3800f, 14000f };
    public static final String[] EQ_LABELS = {
        "60 Hz (Sub-Bass)",
        "230 Hz (Warmth)",
        "910 Hz (Vocals)",
        "3.6 kHz (Presence)",
        "12 kHz (Air / Stage)"
    };
    private static final int[] DEFAULT_EQ_DB = { -2, 1, 2, 3, 2 };

    private static final String NAME = "thor_boost_prefs_v3";
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
        if (!v && getGainDb() > SAFE_MAX_DB) {
            ed.putInt("gain_db", SAFE_MAX_DB);
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

    public boolean isStereoWideEnabled() {
        return sp.getBoolean("stereo_wide_enabled", true);
    }

    public void setStereoWideEnabled(boolean v) {
        sp.edit().putBoolean("stereo_wide_enabled", v).apply();
    }

    public int getStageWidthPercent() {
        int v = sp.getInt("stage_width_pct", 65);
        return Math.max(0, Math.min(100, v));
    }

    public void setStageWidthPercent(int pct) {
        int clamped = Math.max(0, Math.min(100, pct));
        sp.edit().putInt("stage_width_pct", clamped).apply();
    }

    public boolean isEqEnabled() {
        return sp.getBoolean("eq_enabled", true);
    }

    public void setEqEnabled(boolean v) {
        sp.edit().putBoolean("eq_enabled", v).apply();
    }

    public int getEqBandDb(int index) {
        if (index < 0 || index >= DEFAULT_EQ_DB.length) return 0;
        int val = sp.getInt("eq_band_" + index, DEFAULT_EQ_DB[index]);
        return Math.max(EQ_MIN_DB, Math.min(EQ_MAX_DB, val));
    }

    public void setEqBandDb(int index, int db) {
        if (index < 0 || index >= DEFAULT_EQ_DB.length) return;
        int clamped = Math.max(EQ_MIN_DB, Math.min(EQ_MAX_DB, db));
        sp.edit().putInt("eq_band_" + index, clamped).apply();
    }

    public boolean isLimiterEnabled() {
        return sp.getBoolean("limiter_enabled", true);
    }

    public void setLimiterEnabled(boolean v) {
        sp.edit().putBoolean("limiter_enabled", v).apply();
    }

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
