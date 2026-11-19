package com.thor.audiounlocker;

import android.content.Context;
import android.content.SharedPreferences;

public final class Prefs {
    public static final int MAX_GAIN_DB = 40;
    private static final String NAME = "thor_boost_prefs";

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

    public int getGainDb() {
        int val = sp.getInt("gain_db", 12);
        return Math.max(0, Math.min(MAX_GAIN_DB, val));
    }

    public void setGainDb(int db) {
        int clamped = Math.max(0, Math.min(MAX_GAIN_DB, db));
        sp.edit().putInt("gain_db", clamped).apply();
    }

    public boolean isOverdriveStack() {
        return sp.getBoolean("overdrive_stack", true);
    }

    public void setOverdriveStack(boolean v) {
        sp.edit().putBoolean("overdrive_stack", v).apply();
    }

    public boolean isLimiterEnabled() {
        return sp.getBoolean("limiter_enabled", false);
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
