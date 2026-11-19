package com.thor.audiounlocker;

import android.accessibilityservice.AccessibilityService;
import android.view.accessibility.AccessibilityEvent;

public final class AppSwitchAccessibilityService extends AccessibilityService {

    private static volatile boolean connected = false;
    private static volatile String lastRealPackage = null;
    private Prefs prefs;
    private int lastDispatchedGain = Integer.MIN_VALUE;
    private boolean lastWasOverride = false;

    public static boolean isConnected() {
        return connected;
    }

    public static String getLastRealPackage() {
        return lastRealPackage;
    }

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        prefs = new Prefs(this);
        connected = true;
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        if (event == null || event.getEventType() != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            return;
        }
        CharSequence pkgSeq = event.getPackageName();
        if (pkgSeq == null || pkgSeq.length() == 0) return;

        String pkg = pkgSeq.toString();
        if (pkg.equals(lastRealPackage)) return;

        if (pkg.equals("com.android.systemui")
            || pkg.equals("android")
            || pkg.equals(getPackageName())
            || pkg.contains("inputmethod")
            || pkg.contains("keyboard")) {
            return;
        }

        lastRealPackage = pkg;
        if (prefs == null) prefs = new Prefs(this);
        if (!prefs.isEnabled() || !prefs.isPerAppOverridesEnabled()) return;

        int overrideDb = prefs.getPerAppGainDb(pkg);
        boolean isOverride = overrideDb >= 0;
        int effectiveGain = isOverride ? overrideDb : prefs.getGainDb();

        // Skip waking the audio service if switching between two apps with identical gain
        if (effectiveGain == lastDispatchedGain && isOverride == lastWasOverride && !isOverride) {
            return;
        }
        lastDispatchedGain = effectiveGain;
        lastWasOverride = isOverride;

        AudioBoostService.notifyForegroundApp(this, pkg);
    }

    @Override
    public void onInterrupt() {
    }

    @Override
    public void onDestroy() {
        connected = false;
        super.onDestroy();
    }
}
