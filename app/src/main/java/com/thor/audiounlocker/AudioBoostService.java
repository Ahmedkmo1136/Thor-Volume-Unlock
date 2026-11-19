package com.thor.audiounlocker;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.os.Build;
import android.os.IBinder;

public final class AudioBoostService extends Service {

    public static final String ACTION_APPLY = "com.thor.audiounlocker.APPLY";
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

    public static String getLastStatus() {
        return lastStatus;
    }

    public static void setStatusCallback(StatusCallback cb) {
        uiCallback = cb;
    }

    public static void startOrUpdate(Context context) {
        Intent i = new Intent(context, AudioBoostService.class);
        i.setAction(ACTION_APPLY);
        context.startForegroundService(i);
    }

    public static void notifyForegroundApp(Context context, String pkg) {
        Intent i = new Intent(context, AudioBoostService.class);
        i.setAction(ACTION_APP_SWITCH);
        i.putExtra(EXTRA_PACKAGE, pkg);
        context.startForegroundService(i);
    }

    public static void stopServiceNow(Context context) {
        Intent i = new Intent(context, AudioBoostService.class);
        i.setAction(ACTION_STOP);
        context.startService(i);
    }

    @Override
    public void onCreate() {
        super.onCreate();
        prefs = new Prefs(this);
        createChannel();
        engine = new AudioBoostEngine(this, prefs, (statusLine, active, bypassed) -> {
            lastStatus = statusLine;
            updateNotification(statusLine);
            StatusCallback cb = uiCallback;
            if (cb != null) {
                cb.onStatusChanged(statusLine);
            }
        });
        engine.start();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && ACTION_STOP.equals(intent.getAction())) {
            prefs.setEnabled(false);
            engine.stop();
            stopForeground(STOP_FOREGROUND_REMOVE);
            stopSelf();
            return START_NOT_STICKY;
        }

        Notification n = buildNotification(lastStatus);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIF_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK);
        } else {
            startForeground(NOTIF_ID, n);
        }

        String pkg = (intent != null && ACTION_APP_SWITCH.equals(intent.getAction()))
            ? intent.getStringExtra(EXTRA_PACKAGE)
            : AppSwitchAccessibilityService.getLastRealPackage();
        engine.apply(pkg);
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        if (engine != null) {
            engine.stop();
        }
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
        PendingIntent stopPi = PendingIntent.getService(
            this, 1,
            new Intent(this, AudioBoostService.class).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT
        );

        return new Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_speaker_boost)
            .setContentTitle("Thor Audio Unlocker")
            .setContentText(statusText)
            .setContentIntent(openPi)
            .setOngoing(true)
            .addAction(new Notification.Action.Builder(null, "Turn Off", stopPi).build())
            .build();
    }
}
