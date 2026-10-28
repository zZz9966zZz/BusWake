package com.buswake.app;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.drawable.Icon;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.PowerManager;
import android.os.SystemClock;

import java.util.Locale;

public final class GuardService extends Service {
    public static final String ACTION_START = "com.buswake.app.action.START";
    public static final String ACTION_STOP = "com.buswake.app.action.STOP";
    public static final String ACTION_STOP_ALARM = "com.buswake.app.action.STOP_ALARM";
    public static final String ACTION_TEST = "com.buswake.app.action.TEST";
    public static final String ACTION_SETTINGS_CHANGED = "com.buswake.app.action.SETTINGS_CHANGED";
    public static final String EXTRA_STOP_ID = "stop_id";
    public static final String EXTRA_STOP_NAME = "stop_name";
    public static final String EXTRA_DISTANCE = "distance";
    public static final String EXTRA_TEST = "test";

    private static final String CHANNEL_GUARD = "guard";
    public static final String CHANNEL_ALARM = "alarm";
    private static final int NOTIF_GUARD = 1001;
    private static final int NOTIF_ALARM = 1002;

    private LocationManager locationManager;
    private PowerManager powerManager;
    private NotificationManager notificationManager;
    private PowerManager.WakeLock cpuWakeLock;
    private PowerManager.WakeLock screenWakeLock;
    private final Handler handler = new Handler(Looper.getMainLooper());

    private Stop activeStop;
    private boolean guardRunning;
    private boolean alarmActive;
    private boolean triggeredByGuard;
    private boolean testAlarm;
    private long guardStartedAt;
    private long lastNotificationAt;
    private double lastDistance = -1;
    private double lastSpeedKmh = -1;
    private int lastRequestedIntervalMs = -1;
    private Location previousLocation;

    private final Runnable stopRefresh = new Runnable() {
        @Override
        public void run() {
            // placeholder for a future periodic refresh
        }
    };

    private final LocationListener locationListener = new LocationListener() {
        @Override
        public void onLocationChanged(Location location) {
            if (location == null || alarmActive || !guardRunning) {
                return;
            }
            handleLocation(location, true);
        }

        @Override
        public void onStatusChanged(String provider, int status, Bundle extras) {
        }

        @Override
        public void onProviderEnabled(String provider) {
        }

        @Override
        public void onProviderDisabled(String provider) {
        }
    };

    @Override
    public void onCreate() {
        super.onCreate();
        locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
        powerManager = (PowerManager) getSystemService(Context.POWER_SERVICE);
        notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        createChannels();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        String action = intent != null ? intent.getAction() : null;

        if (ACTION_STOP.equals(action)) {
            stopEverything();
            return START_NOT_STICKY;
        }
        if (ACTION_STOP_ALARM.equals(action)) {
            stopAlarm();
            return START_NOT_STICKY;
        }
        if (ACTION_TEST.equals(action)) {
            startForeground(NOTIF_GUARD, buildGuardNotification("正在测试到站闹钟", "测试结束后可正常开始守护"));
            guardStartedAt = System.currentTimeMillis();
            triggerAlarm(new Stop("test", "测试站点", 0, 0, System.currentTimeMillis()), 0, true);
            return START_NOT_STICKY;
        }
        if (ACTION_SETTINGS_CHANGED.equals(action)) {
            if (guardRunning) {
                requestLocationUpdates();
                updateGuardNotification();
                return START_STICKY;
            }
            if (!alarmActive) {
                stopSelf();
            }
            return START_NOT_STICKY;
        }

        if (ACTION_START.equals(action)) {
            String stopId = intent.getStringExtra(EXTRA_STOP_ID);
            if (stopId == null) {
                stopId = StopStore.selectedId(this);
            }
            Stop stop = StopStore.findById(this, stopId);
            if (stop == null) {
                stopSelf();
                return START_NOT_STICKY;
            }
            beginGuard(stop);
            return START_STICKY;
        }

        // Null intent usually means the system restarted a sticky service.
        if (StopStore.isGuardRunning(this)) {
            Stop stop = StopStore.findById(this, StopStore.prefs(this).getString(StopStore.KEY_ACTIVE_ID, null));
            if (stop != null) {
                beginGuard(stop);
                return START_STICKY;
            }
        }
        stopSelf();
        return START_NOT_STICKY;
    }

    private void beginGuard(Stop stop) {
        activeStop = stop;
        guardRunning = true;
        alarmActive = false;
        triggeredByGuard = false;
        testAlarm = false;
        guardStartedAt = System.currentTimeMillis();
        lastDistance = -1;
        lastSpeedKmh = -1;
        previousLocation = null;

        SharedPreferences.Editor e = StopStore.prefs(this).edit();
        e.putBoolean(StopStore.KEY_GUARD_RUNNING, true);
        e.putBoolean(StopStore.KEY_ALARM_ACTIVE, false);
        e.putString(StopStore.KEY_ACTIVE_ID, stop.id);
        e.putString(StopStore.KEY_SELECTED, stop.id);
        e.putLong(StopStore.KEY_LAST_UPDATE, 0);
        e.putFloat(StopStore.KEY_LAST_DISTANCE, -1);
        e.putFloat(StopStore.KEY_LAST_SPEED, -1);
        e.apply();

        startForeground(NOTIF_GUARD, buildGuardNotification(
                "正在守护 · " + stop.name,
                "到站前 " + radiusLabel() + " 响铃震动"
        ));
        AppLog.i("Guard", "Guard active: " + stop.name
                + ", radius=" + StopStore.getRadiusM(this)
                + "m, interval=" + StopStore.getIntervalMs(this) + "ms");
        acquireCpuLock();
        requestLocationUpdates();
    }

    private void requestLocationUpdates() {
        if (Build.VERSION.SDK_INT >= 23
                && checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED
                && checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            stopEverything();
            return;
        }

        if (locationManager == null) {
            return;
        }

        stopLocationUpdates();
        int intervalMs = StopStore.getIntervalMs(this);
        lastRequestedIntervalMs = intervalMs;

        Location best = bestLastKnown();
        if (best != null) {
            handleLocation(best, false);
        }

        try {
            if (locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                locationManager.requestLocationUpdates(
                        LocationManager.GPS_PROVIDER, intervalMs, 0f, locationListener, Looper.getMainLooper());
            }
        } catch (Exception ignored) {
        }
        try {
            if (locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
                locationManager.requestLocationUpdates(
                        LocationManager.NETWORK_PROVIDER, intervalMs, 0f, locationListener, Looper.getMainLooper());
            }
        } catch (Exception ignored) {
        }
    }

    private Location bestLastKnown() {
        if (locationManager == null) {
            return null;
        }
        Location best = null;
        try {
            for (String provider : new String[]{LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER}) {
                if (!locationManager.isProviderEnabled(provider)) {
                    continue;
                }
                Location l = locationManager.getLastKnownLocation(provider);
                if (l != null && (best == null || l.getTime() > best.getTime())) {
                    best = l;
                }
            }
        } catch (Exception ignored) {
        }
        return best;
    }

    private void handleLocation(Location location, boolean possiblyTrigger) {
        if (activeStop == null) {
            return;
        }
        int desiredInterval = StopStore.getIntervalMs(this);
        if (guardRunning && !alarmActive && lastRequestedIntervalMs > 0
                && desiredInterval != lastRequestedIntervalMs) {
            handler.post(this::requestLocationUpdates);
        }
        long now = System.currentTimeMillis();
        long age = now - location.getTime();
        if (age > 120000L && possiblyTrigger) {
            return;
        }

        float[] results = new float[1];
        Location.distanceBetween(location.getLatitude(), location.getLongitude(),
                activeStop.lat, activeStop.lng, results);
        double distance = results[0];
        lastDistance = distance;

        double speed = -1;
        if (location.hasSpeed()) {
            speed = location.getSpeed() * 3.6;
        } else if (previousLocation != null) {
            long dt = location.getTime() - previousLocation.getTime();
            if (dt > 500 && dt < 60000) {
                float[] d = new float[1];
                Location.distanceBetween(previousLocation.getLatitude(), previousLocation.getLongitude(),
                        location.getLatitude(), location.getLongitude(), d);
                speed = d[0] / (dt / 1000.0) * 3.6;
            }
        }
        if (speed >= 0) {
            lastSpeedKmh = speed;
        }
        if (previousLocation == null || location.getTime() > previousLocation.getTime()) {
            previousLocation = location;
        }

        SharedPreferences.Editor e = StopStore.prefs(this).edit();
        e.putFloat(StopStore.KEY_LAST_DISTANCE, (float) distance);
        e.putFloat(StopStore.KEY_LAST_SPEED, (float) lastSpeedKmh);
        e.putFloat(StopStore.KEY_LAST_ACCURACY, location.hasAccuracy() ? location.getAccuracy() : -1);
        e.putLong(StopStore.KEY_LAST_UPDATE, now);
        e.apply();

        if (now - lastNotificationAt > 10000) {
            lastNotificationAt = now;
            updateGuardNotification();
        }

        if (possiblyTrigger && distance <= StopStore.getRadiusM(this)
                && now - guardStartedAt > 1500L) {
            triggerAlarm(activeStop, distance, false);
        }
    }

    private void triggerAlarm(Stop stop, double distance, boolean test) {
        if (alarmActive) {
            return;
        }
        alarmActive = true;
        testAlarm = test;
        triggeredByGuard = !test;
        AppLog.i("Alarm", (test ? "Test alarm" : "Stop alarm")
                + " at " + stop.name + ", distance=" + Math.round(distance) + "m");

        if (!test) {
            guardRunning = false;
            stopLocationUpdates();
            StopStore.prefs(this).edit()
                    .putBoolean(StopStore.KEY_GUARD_RUNNING, false)
                    .putBoolean(StopStore.KEY_ALARM_ACTIVE, true)
                    .apply();
        } else {
            StopStore.setAlarmActive(this, true);
        }

        AlarmController.start(this);
        showAlarmNotification(stop, distance, test);
        acquireScreenLock();
        updateGuardNotificationForAlarm(stop);

        Intent alarm = new Intent(this, AlarmActivity.class);
        alarm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                | Intent.FLAG_ACTIVITY_CLEAR_TOP
                | Intent.FLAG_ACTIVITY_SINGLE_TOP
                | Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS);
        alarm.putExtra(EXTRA_STOP_NAME, stop.name);
        alarm.putExtra(EXTRA_DISTANCE, distance);
        alarm.putExtra(EXTRA_TEST, test);
        try {
            startActivity(alarm);
        } catch (Exception ignored) {
        }
    }

    private void stopAlarm() {
        AlarmController.stop();
        alarmActive = false;
        if (notificationManager != null) {
            notificationManager.cancel(NOTIF_ALARM);
        }
        StopStore.setAlarmActive(this, false);
        releaseScreenLock();

        if (triggeredByGuard) {
            stopEverything();
        } else if (testAlarm && !guardRunning) {
            stopEverything();
        } else {
            if (guardRunning) {
                updateGuardNotification();
            } else {
                stopEverything();
            }
        }
    }

    private void stopEverything() {
        guardRunning = false;
        alarmActive = false;
        AppLog.i("Guard", "Guard stopped");
        AlarmController.stop();
        stopLocationUpdates();
        releaseCpuLock();
        releaseScreenLock();
        StopStore.prefs(this).edit()
                .putBoolean(StopStore.KEY_GUARD_RUNNING, false)
                .putBoolean(StopStore.KEY_ALARM_ACTIVE, false)
                .apply();
        if (notificationManager != null) {
            notificationManager.cancel(NOTIF_ALARM);
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE);
        } else {
            stopForeground(true);
        }
        stopSelf();
    }

    private void stopLocationUpdates() {
        if (locationManager != null) {
            try {
                locationManager.removeUpdates(locationListener);
            } catch (Exception ignored) {
            }
        }
    }

    private void acquireCpuLock() {
        if (powerManager == null || (cpuWakeLock != null && cpuWakeLock.isHeld())) {
            return;
        }
        cpuWakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "BusWake:guard");
        cpuWakeLock.setReferenceCounted(false);
        try {
            cpuWakeLock.acquire(12 * 60 * 60 * 1000L);
        } catch (Exception ignored) {
        }
    }

    private void releaseCpuLock() {
        if (cpuWakeLock != null) {
            try {
                if (cpuWakeLock.isHeld()) {
                    cpuWakeLock.release();
                }
            } catch (Exception ignored) {
            }
            cpuWakeLock = null;
        }
    }

    private void acquireScreenLock() {
        if (powerManager == null) {
            return;
        }
        try {
            screenWakeLock = powerManager.newWakeLock(
                    PowerManager.FULL_WAKE_LOCK
                            | PowerManager.ACQUIRE_CAUSES_WAKEUP
                            | PowerManager.ON_AFTER_RELEASE,
                    "BusWake:alarm");
            screenWakeLock.setReferenceCounted(false);
            screenWakeLock.acquire(30000L);
        } catch (Exception ignored) {
        }
    }

    private void releaseScreenLock() {
        if (screenWakeLock != null) {
            try {
                if (screenWakeLock.isHeld()) {
                    screenWakeLock.release();
                }
            } catch (Exception ignored) {
            }
            screenWakeLock = null;
        }
    }

    private void createChannels() {
        if (notificationManager == null || Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return;
        }
        NotificationChannel guard = new NotificationChannel(
                CHANNEL_GUARD, getString(R.string.channel_guard), NotificationManager.IMPORTANCE_LOW);
        guard.setDescription(getString(R.string.channel_guard_desc));
        guard.setShowBadge(false);
        guard.setSound(null, null);

        NotificationChannel alarm = new NotificationChannel(
                CHANNEL_ALARM, getString(R.string.channel_alarm), NotificationManager.IMPORTANCE_HIGH);
        alarm.setDescription(getString(R.string.channel_alarm_desc));
        alarm.setSound(null, null);
        alarm.enableVibration(false);
        alarm.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);
        alarm.setShowBadge(true);
        notificationManager.createNotificationChannels(java.util.Arrays.asList(guard, alarm));
    }

    private PendingIntent mainPendingIntent() {
        Intent i = new Intent(this, MainActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        return PendingIntent.getActivity(this, 10, i,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    private PendingIntent stopGuardPendingIntent() {
        Intent i = new Intent(this, GuardService.class).setAction(ACTION_STOP);
        return PendingIntent.getService(this, 11, i,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    private PendingIntent stopAlarmPendingIntent() {
        Intent i = new Intent(this, GuardService.class).setAction(ACTION_STOP_ALARM);
        return PendingIntent.getService(this, 12, i,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    private PendingIntent alarmActivityPendingIntent(Stop stop, double distance, boolean test) {
        Intent i = new Intent(this, AlarmActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP)
                .putExtra(EXTRA_STOP_NAME, stop.name)
                .putExtra(EXTRA_DISTANCE, distance)
                .putExtra(EXTRA_TEST, test);
        return PendingIntent.getActivity(this, 13, i,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    private Notification buildGuardNotification(String title, String text) {
        Notification.Builder b;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            b = new Notification.Builder(this, CHANNEL_GUARD);
        } else {
            b = new Notification.Builder(this);
        }
        b.setSmallIcon(R.drawable.ic_stat_pin)
                .setContentTitle(title)
                .setContentText(text)
                .setSubText("到站前 " + radiusLabel() + " 提醒")
                .setContentIntent(mainPendingIntent())
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setCategory(Notification.CATEGORY_SERVICE)
                .setColor(0xFF38BDF8)
                .setShowWhen(false);
        b.addAction(new Notification.Action.Builder(
                Icon.createWithResource(this, R.drawable.ic_stat_pin), "停止守护", stopGuardPendingIntent()).build());
        return b.build();
    }

    private void updateGuardNotification() {
        if (!guardRunning || activeStop == null || notificationManager == null) {
            return;
        }
        String text;
        if (lastDistance >= 0) {
            text = String.format(Locale.CHINA, "距 %s %s", activeStop.name, StopStore.formatDistance(lastDistance));
            if (lastSpeedKmh >= 0) {
                text += String.format(Locale.CHINA, " · 约 %.0f km/h", lastSpeedKmh);
            }
        } else {
            text = "正在获取定位…";
        }
        notificationManager.notify(NOTIF_GUARD, buildGuardNotification("到站叫醒 · 守护中", text));
    }

    private void updateGuardNotificationForAlarm(Stop stop) {
        if (notificationManager == null) {
            return;
        }
        String title = testAlarm ? "正在测试到站闹钟" : "闹钟已响 · " + stop.name;
        String text = testAlarm ? "如果听到声音并感到震动，说明叫醒功能正常"
                : "已进入 " + radiusLabel() + " 范围，请准备下车";
        notificationManager.notify(NOTIF_GUARD, buildGuardNotification(title, text));
    }

    private void showAlarmNotification(Stop stop, double distance, boolean test) {
        if (notificationManager == null) {
            return;
        }
        Notification.Builder b;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            b = new Notification.Builder(this, CHANNEL_ALARM);
        } else {
            b = new Notification.Builder(this);
        }
        String title = test ? "测试闹钟 · 正在响铃" : "该下车了！";
        String text = test
                ? "如果听到声音并感到震动，说明叫醒功能正常"
                : String.format(Locale.CHINA, "已进入 %s %s 范围（距离 %s）",
                stop.name, radiusLabel(), StopStore.formatDistance(distance));

        b.setSmallIcon(R.drawable.ic_stat_pin)
                .setContentTitle(title)
                .setContentText(text)
                .setStyle(new Notification.BigTextStyle().bigText(text))
                .setContentIntent(alarmActivityPendingIntent(stop, distance, test))
                .setFullScreenIntent(alarmActivityPendingIntent(stop, distance, test), true)
                .setCategory(Notification.CATEGORY_ALARM)
                .setPriority(Notification.PRIORITY_MAX)
                .setVisibility(Notification.VISIBILITY_PUBLIC)
                .setOngoing(true)
                .setAutoCancel(false)
                .setShowWhen(true)
                .setColor(0xFFFBBF24);
        b.addAction(new Notification.Action.Builder(
                Icon.createWithResource(this, R.drawable.ic_stat_pin), "我醒了 · 停止闹钟", stopAlarmPendingIntent()).build());
        notificationManager.notify(NOTIF_ALARM, b.build());
    }

    private String radiusLabel() {
        return StopStore.formatRadius(StopStore.getRadiusM(this));
    }

    @Override
    public void onTaskRemoved(Intent rootIntent) {
        if (!guardRunning && !alarmActive) {
            stopSelf();
        }
        super.onTaskRemoved(rootIntent);
    }

    @Override
    public void onDestroy() {
        stopLocationUpdates();
        releaseCpuLock();
        releaseScreenLock();
        if (guardRunning) {
            StopStore.prefs(this).edit().putBoolean(StopStore.KEY_GUARD_RUNNING, false).apply();
        }
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
