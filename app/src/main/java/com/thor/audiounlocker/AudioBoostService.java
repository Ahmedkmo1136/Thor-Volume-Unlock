package com.thor.audiounlocker;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.os.Build;
import android.os.IBinder;
import android.service.quicksettings.TileService;

public final class AudioBoostService extends Service {

    public static final String ACTION_APPLY = "com.thor.audiounlocker.APPLY";
    public static final String ACTION_TOGGLE = "com.thor.audiounlocker.TOGGLE";
    public static final String ACTION_STOP = "com.thor.audiounlocker.STOP";
    public static final String ACTION_APP_SWITCH = "com.thor.audiounlocker.APP_SWITCH";
    public static final String EXTRA_PACKAGE = "pkg";

    private static final String CHANNEL_ID = "thor_boost_channel";
    private static final int NOTIF_ID = 1001;

    public interface StatusCallback {
        void onStatusChanged(String text);
    }

    private static volatile String lastStatus = "Idle";
    private static volatile StatusCallback uiCallback;

    private Prefs prefs;
    private AudioBoostEngine engine;
    private boolean inForeground = false;

    public static String getLastStatus() {
        return lastStatus;
    }

    public static void setStatusCallback(StatusCallback cb) {
        uiCallback = cb;
    }

    public static void startOrUpdate(Context context) {
        try {
            Intent i = new Intent(context, AudioBoostService.class);
            i.setAction(ACTION_APPLY);
            context.startForegroundService(i);
        } catch (Throwable ignored) {
        }
    }

    public static void notifyForegroundApp(Context context, String pkg) {
        try {
            Intent i = new Intent(context, AudioBoostService.class);
            i.setAction(ACTION_APP_SWITCH);
            i.putExtra(EXTRA_PACKAGE, pkg);
            context.startForegroundService(i);
        } catch (Throwable ignored) {
        }
    }

    public static void stopServiceNow(Context context) {
        try {
            Intent i = new Intent(context, AudioBoostService.class);
            i.setAction(ACTION_STOP);
            context.startService(i);
        } catch (Throwable ignored) {
        }
        requestTileSync(context);
    }

    public static void requestTileSync(Context context) {
        try {
            TileService.requestListeningState(
                context.getApplicationContext(),
                new ComponentName(context.getApplicationContext(), BoostTileService.class)
            );
        } catch (Throwable ignored) {
        }
    }

    @Override
    public void onCreate() {
        super.onCreate();
        prefs = new Prefs(this);
        createChannel();
        engine = new AudioBoostEngine(this, prefs, (statusLine, active, bypassed) -> {
            lastStatus = statusLine;
            if (inForeground) {
                updateNotification(statusLine);
            }
            requestTileSync(this);
            StatusCallback cb = uiCallback;
            if (cb != null) {
                cb.onStatusChanged(statusLine);
            }
        });
        engine.startListening();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        String action = (intent != null) ? intent.getAction() : null;

        if (ACTION_STOP.equals(action)) {
            prefs.setEnabled(false);
            engine.stop();
            inForeground = false;
            requestTileSync(this);
            stopForeground(STOP_FOREGROUND_REMOVE);
            stopSelf();
            return START_NOT_STICKY;
        }

        if (ACTION_TOGGLE.equals(action)) {
            prefs.setEnabled(!prefs.isEnabled());
        }

        if (!inForeground) {
            Notification n = buildNotification(lastStatus);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(NOTIF_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK);
            } else {
                startForeground(NOTIF_ID, n);
            }
            inForeground = true;
        }

        String pkg = ACTION_APP_SWITCH.equals(action)
            ? intent.getStringExtra(EXTRA_PACKAGE)
            : AppSwitchAccessibilityService.getLastRealPackage();
        engine.apply(pkg);
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        inForeground = false;
        if (engine != null) {
            engine.stop();
        }
        requestTileSync(this);
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private void createChannel() {
        NotificationChannel ch = new NotificationChannel(
            CHANNEL_ID,
            getString(R.string.notification_channel_name),
            NotificationManager.IMPORTANCE_LOW
        );
        ch.setShowBadge(false);
        NotificationManager nm = getSystemService(NotificationManager.class);
        if (nm != null) {
            nm.createNotificationChannel(ch);
        }
    }

    private void updateNotification(String text) {
        NotificationManager nm = getSystemService(NotificationManager.class);
        if (nm != null) {
            nm.notify(NOTIF_ID, buildNotification(text));
        }
    }

    private Notification buildNotification(String statusText) {
        PendingIntent openPi = PendingIntent.getActivity(
            this, 0,
            new Intent(this, MainActivity.class),
            PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT
        );
        PendingIntent togglePi = PendingIntent.getForegroundService(
            this, 1,
            new Intent(this, AudioBoostService.class).setAction(ACTION_TOGGLE),
            PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT
        );
        PendingIntent exitPi = PendingIntent.getService(
            this, 2,
            new Intent(this, AudioBoostService.class).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT
        );

        boolean enabled = prefs != null && prefs.isEnabled();
        String toggleLabel = enabled ? "Turn Off" : "Turn On";

        return new Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_speaker_boost)
            .setContentTitle(enabled ? "Thor Audio Unlocker (ON)" : "Thor Audio Unlocker (OFF)")
            .setContentText(statusText)
            .setContentIntent(openPi)
            .setOngoing(true)
            .addAction(new Notification.Action.Builder(null, toggleLabel, togglePi).build())
            .addAction(new Notification.Action.Builder(null, "Exit", exitPi).build())
            .build();
    }
}
