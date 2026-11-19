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

    // --- Per-App Gain Settings ---
    public boolean isPerAppMode() {
        return sp.getBoolean("per_app_mode", false);
    }

    public void setPerAppMode(boolean v) {
        sp.edit().putBoolean("per_app_mode", v).apply();
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

    // --- Stereo Stage & EQ ---
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
