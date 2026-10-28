package com.buswake.app;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
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
import android.widget.Toast;

import java.util.Locale;

public final class TripRecordService extends Service {
    public static final String ACTION_START = "com.buswake.app.action.TRIP_START";
    public static final String ACTION_STOP = "com.buswake.app.action.TRIP_STOP";
    private static final String CHANNEL_TRIP = "trip_record";
    private static final int NOTIF_TRIP = 3001;
    private static final long PERSIST_INTERVAL_MS = 5000;
    private static final long NOTIF_INTERVAL_MS = 5000;
    private static final long RECORD_GPS_INTERVAL_MS = 1000L;
    private static final long RECORD_NETWORK_INTERVAL_MS = 3000L;

    private LocationManager locationManager;
    private PowerManager powerManager;
    private NotificationManager notificationManager;
    private PowerManager.WakeLock wakeLock;
    private final Handler handler = new Handler(Looper.getMainLooper());

    private Trip trip;
    private boolean recording;
    private long startedAt;
    private long startedElapsed;
    private long lastElapsed;
    private double distanceM;
    private double speedKmh;
    private int pointCount;
    private long lastPersistAt;
    private long lastNotifyAt;
    private Location lastLocation;

    private final Runnable ticker = new Runnable() {
        @Override
        public void run() {
            if (!recording) {
                return;
            }
            updateNotification();
            handler.postDelayed(this, NOTIF_INTERVAL_MS);
        }
    };

    private final LocationListener listener = new LocationListener() {
        @Override
        public void onLocationChanged(Location location) {
            addPoint(location);
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
        createChannel();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        String action = intent != null ? intent.getAction() : null;
        if (ACTION_STOP.equals(action)) {
            finishRecording();
            return START_NOT_STICKY;
        }
        if (ACTION_START.equals(action)) {
            startRecording();
            return START_NOT_STICKY;
        }
        if (TripStore.isRecording(this)) {
            TripStore.clearLive(this);
        }
        stopSelf();
        return START_NOT_STICKY;
    }

    private void startRecording() {
        if (recording) {
            return;
        }
        if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED
                && checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            AppLog.e("Trip", "Start failed: no location permission");
            Toast.makeText(this, "需要定位权限才能记录行程", Toast.LENGTH_LONG).show();
            stopSelf();
            return;
        }

        recording = true;
        startedAt = System.currentTimeMillis();
        startedElapsed = SystemClock.elapsedRealtime();
        lastElapsed = 0;
        distanceM = 0;
        speedKmh = 0;
        pointCount = 0;
        lastLocation = null;
        trip = Trip.create(startedAt);
        TripStore.startRecording(this, startedAt);
        AppLog.i("Trip", "Recording started, sample interval: GPS=1000ms, network=3000ms");

        startForeground(NOTIF_TRIP, buildNotification(false));
        acquireWakeLock();
        requestLocationUpdates();

        Location best = bestLastKnown();
        if (best != null && System.currentTimeMillis() - best.getTime() < 15000) {
            addPoint(best);
        }
        handler.removeCallbacks(ticker);
        handler.postDelayed(ticker, NOTIF_INTERVAL_MS);
    }

    private void requestLocationUpdates() {
        if (locationManager == null) {
            return;
        }
        try {
            locationManager.removeUpdates(listener);
        } catch (Exception ignored) {
        }
        try {
            if (locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                locationManager.requestLocationUpdates(
                        LocationManager.GPS_PROVIDER, RECORD_GPS_INTERVAL_MS, 0f, listener, Looper.getMainLooper());
            }
        } catch (Exception ignored) {
        }
        try {
            if (locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
                locationManager.requestLocationUpdates(
                        LocationManager.NETWORK_PROVIDER, RECORD_NETWORK_INTERVAL_MS, 0f, listener, Looper.getMainLooper());
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

    private void addPoint(Location location) {
        if (!recording || trip == null || location == null) {
            return;
        }
        if (location.getLatitude() == 0 && location.getLongitude() == 0) {
            return;
        }
        if (location.hasAccuracy() && location.getAccuracy() > 200f) {
            return;
        }

        long nowElapsed = SystemClock.elapsedRealtime();
        long relative = nowElapsed - startedElapsed;
        if (lastLocation != null) {
            long dt = nowElapsed - lastElapsed;
            if (dt < 600) {
                return;
            }
            float[] result = new float[1];
            Location.distanceBetween(lastLocation.getLatitude(), lastLocation.getLongitude(),
                    location.getLatitude(), location.getLongitude(), result);
            double segmentM = result[0];
            double segmentKmh = segmentM / (dt / 1000.0) * 3.6;
            if (segmentKmh > 200) {
                return;
            }
            distanceM += segmentM;
            if (location.hasSpeed()) {
                speedKmh = location.getSpeed() * 3.6;
            } else {
                speedKmh = segmentKmh;
            }
            if (speedKmh < 0) {
                speedKmh = 0;
            }
            if (speedKmh > 200) {
                speedKmh = 200;
            }
        } else {
            speedKmh = location.hasSpeed() ? Math.max(0, location.getSpeed() * 3.6) : 0;
        }

        trip.addPoint(new Trip.Point(location.getLatitude(), location.getLongitude(),
                relative, distanceM, speedKmh));
        pointCount++;
        lastLocation = location;
        lastElapsed = nowElapsed;

        long now = System.currentTimeMillis();
        if (now - lastPersistAt >= PERSIST_INTERVAL_MS || pointCount == 1) {
            lastPersistAt = now;
            TripStore.updateRecording(this, distanceM, speedKmh, pointCount);
        }
        if (now - lastNotifyAt >= NOTIF_INTERVAL_MS || pointCount == 1) {
            lastNotifyAt = now;
            updateNotification();
        }
    }

    private void updateNotification() {
        if (!recording || notificationManager == null) {
            return;
        }
        notificationManager.notify(NOTIF_TRIP, buildNotification(true));
    }

    private Notification buildNotification(boolean updateText) {
        Notification.Builder b;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            b = new Notification.Builder(this, CHANNEL_TRIP);
        } else {
            b = new Notification.Builder(this);
        }
        long seconds = Math.max(0, (System.currentTimeMillis() - startedAt) / 1000);
        String text = updateText
                ? String.format(Locale.CHINA, "%s · %s · 当前 %s",
                StopStore.formatDistance(distanceM), Trip.formatDuration(seconds),
                StopStore.formatSpeed(speedKmh))
                : "正在获取定位…";

        Intent open = new Intent(this, TripListActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent openPi = PendingIntent.getActivity(this, 41, open,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Intent stop = new Intent(this, TripRecordService.class).setAction(ACTION_STOP);
        PendingIntent stopPi = PendingIntent.getService(this, 42, stop,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        b.setSmallIcon(R.drawable.ic_stat_pin)
                .setContentTitle("正在记录行程")
                .setContentText(text)
                .setSubText("手动结束后保存路线与速度")
                .setContentIntent(openPi)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setCategory(Notification.CATEGORY_SERVICE)
                .setColor(0xFFF87171)
                .setShowWhen(false);
        b.addAction(new Notification.Action.Builder(
                Icon.createWithResource(this, R.drawable.ic_stat_pin), "结束记录", stopPi).build());
        return b.build();
    }

    private void createChannel() {
        if (notificationManager == null || Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return;
        }
        NotificationChannel channel = new NotificationChannel(
                CHANNEL_TRIP, getString(R.string.channel_trip), NotificationManager.IMPORTANCE_LOW);
        channel.setDescription(getString(R.string.channel_trip_desc));
        channel.setShowBadge(false);
        channel.setSound(null, null);
        notificationManager.createNotificationChannel(channel);
    }

    private void finishRecording() {
        if (!recording) {
            TripStore.clearLive(this);
            stopRecordForeground();
            stopSelf();
            return;
        }
        recording = false;
        handler.removeCallbacks(ticker);
        try {
            locationManager.removeUpdates(listener);
        } catch (Exception ignored) {
        }
        releaseWakeLock();
        trip.endedAt = System.currentTimeMillis();
        boolean saved = TripStore.add(this, trip);
        if (saved) {
            TripStore.markSaved(this, trip.id);
            AppLog.i("Trip", "Trip saved: " + Math.round(trip.totalDistanceM())
                    + "m, " + (trip.durationMs() / 1000) + "s, " + trip.points.size() + " points");
            Toast.makeText(this, "行程已保存", Toast.LENGTH_SHORT).show();
        } else {
            TripStore.clearLive(this);
            AppLog.w("Trip", "Trip discarded: too short");
            Toast.makeText(this, "行程太短，未保存", Toast.LENGTH_SHORT).show();
        }
        trip = null;
        stopRecordForeground();
        stopSelf();
    }

    private void stopRecordForeground() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE);
        } else {
            stopForeground(true);
        }
    }

    private void acquireWakeLock() {
        if (powerManager == null) {
            return;
        }
        if (wakeLock != null && wakeLock.isHeld()) {
            return;
        }
        wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "BusWake:trip");
        wakeLock.setReferenceCounted(false);
        try {
            wakeLock.acquire(8 * 60 * 60 * 1000L);
        } catch (Exception ignored) {
        }
    }

    private void releaseWakeLock() {
        if (wakeLock != null) {
            try {
                if (wakeLock.isHeld()) {
                    wakeLock.release();
                }
            } catch (Exception ignored) {
            }
            wakeLock = null;
        }
    }

    @Override
    public void onTaskRemoved(Intent rootIntent) {
        if (!recording) {
            stopSelf();
        }
        super.onTaskRemoved(rootIntent);
    }

    @Override
    public void onDestroy() {
        handler.removeCallbacks(ticker);
        try {
            if (locationManager != null) {
                locationManager.removeUpdates(listener);
            }
        } catch (Exception ignored) {
        }
        releaseWakeLock();
        if (recording && trip != null) {
            recording = false;
            trip.endedAt = System.currentTimeMillis();
            if (TripStore.add(this, trip)) {
                TripStore.markSaved(this, trip.id);
            } else {
                TripStore.clearLive(this);
            }
            trip = null;
        }
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
