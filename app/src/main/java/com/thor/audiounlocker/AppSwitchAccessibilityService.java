package com.thor.audiounlocker;

import android.accessibilityservice.AccessibilityService;
import android.view.accessibility.AccessibilityEvent;

public final class AppSwitchAccessibilityService extends AccessibilityService {

    private static volatile boolean connected = false;
    private static volatile String lastRealPackage = null;

    public static boolean isConnected() {
        return connected;
    }

    public static String getLastRealPackage() {
        return lastRealPackage;
    }

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
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
        // Ignore transient overlays (SystemUI, volume panels, keyboards, and this settings UI)
        if (pkg.equals("com.android.systemui")
            || pkg.equals("android")
            || pkg.equals(getPackageName())
            || pkg.contains("inputmethod")
            || pkg.contains("keyboard")) {
            return;
        }

        if (!pkg.equals(lastRealPackage)) {
            lastRealPackage = pkg;
            Prefs prefs = new Prefs(this);
            if (prefs.isEnabled() && prefs.isPerAppOverridesEnabled()) {
                AudioBoostService.notifyForegroundApp(this, pkg);
            }
        }
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
