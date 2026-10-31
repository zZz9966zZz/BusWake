package com.buswake.app;

import android.content.Context;
import android.net.Uri;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class BackupManager {
    private static final int MAX_BYTES = 100 * 1024 * 1024;

    private BackupManager() {
    }

    public static final class BackupData {
        public int radiusM;
        public int intervalMs;
        public float manualSpeed;
        public int leadSeconds;
        public final List<Stop> stops = new ArrayList<>();
        public final List<Trip> trips = new ArrayList<>();
    }

    public static String readText(Context c, Uri uri) throws Exception {
        if (uri == null) {
            throw new IllegalArgumentException("没有选择文件");
        }
        try (InputStream in = c.getContentResolver().openInputStream(uri)) {
            if (in == null) {
                throw new IllegalArgumentException("无法打开文件");
            }
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buffer = new byte[8192];
            int total = 0;
            int n;
            while ((n = in.read(buffer)) > 0) {
                total += n;
                if (total > MAX_BYTES) {
                    throw new IllegalArgumentException("备份文件超过 100 MB");
                }
                out.write(buffer, 0, n);
            }
            return out.toString(StandardCharsets.UTF_8.name());
        }
    }

    public static BackupData parse(Context c, String json) throws Exception {
        JSONObject root = new JSONObject(json);
        String format = root.optString("format", "");
        if (!format.isEmpty() && !"BusWake backup".equals(format)) {
            throw new IllegalArgumentException("不是 BusWake 备份文件");
        }
        BackupData data = new BackupData();
        data.radiusM = StopStore.getRadiusM(c);
        data.intervalMs = StopStore.getIntervalMs(c);
        data.manualSpeed = StopStore.getManualSpeed(c);
        data.leadSeconds = StopStore.getLeadTimeSeconds(c);

        JSONObject settings = root.optJSONObject("settings");
        if (settings != null) {
            data.radiusM = clamp((int) Math.round(metric(settings, "wakeRadius", "wakeRadiusM", data.radiusM)),
                    StopStore.MIN_RADIUS_M, StopStore.MAX_RADIUS_M);
            data.intervalMs = clamp((int) Math.round(metric(settings, "locationInterval",
                            "locationIntervalMs", data.intervalMs)),
                    StopStore.MIN_INTERVAL_MS, StopStore.MAX_INTERVAL_MS);
            data.manualSpeed = (float) Math.max(StopStore.MIN_SPEED_KMH,
                    Math.min(StopStore.MAX_SPEED_KMH,
                            metric(settings, "manualSpeed", "manualSpeedKmh", data.manualSpeed)));
            data.leadSeconds = clamp((int) Math.round(metric(settings, "leadTime",
                            "leadTimeSeconds", data.leadSeconds)),
                    StopStore.MIN_LEAD_TIME_SECONDS, StopStore.MAX_LEAD_TIME_SECONDS);
        }

        JSONArray stops = root.optJSONArray("stops");
        if (stops != null) {
            for (int i = 0; i < stops.length(); i++) {
                JSONObject o = stops.optJSONObject(i);
                if (o == null) {
                    continue;
                }
                double lat = o.optDouble("latitude", o.optDouble("lat", Double.NaN));
                double lng = o.optDouble("longitude", o.optDouble("lng", Double.NaN));
                if (Double.isNaN(lat) || Double.isNaN(lng)
                        || lat < -90 || lat > 90 || lng < -180 || lng > 180
                        || (lat == 0 && lng == 0)) {
                    continue;
                }
                String id = o.optString("id", UUID.randomUUID().toString());
                String name = o.optString("name", "下车站点");
                long created = o.optLong("createdAtEpochMs", o.optLong("createdAt",
                        System.currentTimeMillis()));
                if (id.isEmpty()) {
                    id = UUID.randomUUID().toString();
                }
                data.stops.add(new Stop(id, name, lat, lng, created));
            }
        }

        JSONArray trips = root.optJSONArray("trips");
        if (trips != null) {
            for (int i = 0; i < trips.length(); i++) {
                JSONObject o = trips.optJSONObject(i);
                if (o == null) {
                    continue;
                }
                Trip trip = Trip.fromJson(o);
                if (trip.points.size() >= 2) {
                    data.trips.add(trip);
                }
            }
        }
        return data;
    }

    public static boolean apply(Context c, BackupData data) {
        if (data == null) {
            return false;
        }
        if (!TripStore.replaceAll(c, data.trips)) {
            return false;
        }
        StopStore.saveStops(c, data.stops);
        StopStore.setRadiusM(c, data.radiusM);
        StopStore.setIntervalMs(c, data.intervalMs);
        StopStore.setManualSpeed(c, data.manualSpeed);
        StopStore.setLeadTimeSeconds(c, data.leadSeconds);
        if (data.stops.isEmpty()) {
            StopStore.select(c, null);
        } else {
            StopStore.select(c, data.stops.get(0).id);
        }
        AppLog.i("Restore", "Backup restored: stops=" + data.stops.size()
                + ", trips=" + data.trips.size());
        return true;
    }

    private static double metric(JSONObject settings, String newKey, String oldKey, double fallback) {
        JSONObject item = settings.optJSONObject(newKey);
        if (item != null) {
            return item.optDouble("value", fallback);
        }
        return settings.optDouble(oldKey, fallback);
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}
