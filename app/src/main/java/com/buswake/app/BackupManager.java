package com.buswake.app;

import android.content.Context;
import android.net.Uri;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public final class BackupManager {
    private static final int MAX_BYTES = 100 * 1024 * 1024;
    private static final int MAX_ENTRY_BYTES = 40 * 1024 * 1024;

    private BackupManager() {
    }

    public static final class BackupData {
        public int radiusM;
        public int intervalMs;
        public int tripIntervalMs;
        public float manualSpeed;
        public int leadSeconds;
        /** True when this backup contains App settings and stops. */
        public boolean hasSettingsStops;
        /** True when this backup contains at least one trip. */
        public boolean hasTrips;
        /** Old single-file full backup; applying it replaces all trips. */
        public boolean legacyFull;
        public final List<Stop> stops = new ArrayList<>();
        public final List<Trip> trips = new ArrayList<>();
    }

    public static byte[] readBytes(Context c, Uri uri) throws Exception {
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
            return out.toByteArray();
        }
    }

    public static String readText(Context c, Uri uri) throws Exception {
        return new String(readBytes(c, uri), StandardCharsets.UTF_8);
    }

    public static BackupData readBackup(Context c, Uri uri) throws Exception {
        return parseBytes(c, readBytes(c, uri));
    }

    public static BackupData parseBytes(Context c, byte[] raw) throws Exception {
        if (raw == null || raw.length == 0) {
            throw new IllegalArgumentException("备份文件为空");
        }
        if (isZip(raw)) {
            return parseZip(c, raw);
        }
        return parse(c, new String(raw, StandardCharsets.UTF_8));
    }

    private static boolean isZip(byte[] raw) {
        return raw.length >= 4
                && raw[0] == 0x50 && raw[1] == 0x4B
                && (raw[2] == 0x03 || raw[2] == 0x05 || raw[2] == 0x07);
    }

    public static BackupData parse(Context c, String json) throws Exception {
        JSONObject root = new JSONObject(json);
        String kind = root.optString("kind", "");
        String format = root.optString("format", "");

        BackupData data = new BackupData();
        if ("settings-stops".equals(kind) || "BusWake settings-stops".equals(format)) {
            data.hasSettingsStops = true;
            readSettingsStops(c, root, data);
            return data;
        }
        if ("trip".equals(kind) || "BusWake trip".equals(format)) {
            data.hasTrips = true;
            readTrip(root, data);
            return data;
        }
        if (format.isEmpty() || "BusWake backup".equals(format)) {
            data.hasSettingsStops = true;
            data.hasTrips = true;
            data.legacyFull = true;
            readSettingsStops(c, root, data);
            readTrips(root, data);
            return data;
        }
        throw new IllegalArgumentException("不是 BusWake 备份文件");
    }

    private static BackupData parseZip(Context c, byte[] raw) throws Exception {
        BackupData merged = new BackupData();
        boolean any = false;
        try (ZipInputStream zip = new ZipInputStream(
                new ByteArrayInputStream(raw), StandardCharsets.UTF_8)) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                if (entry.isDirectory()) {
                    continue;
                }
                String name = entry.getName();
                String lower = name.toLowerCase(java.util.Locale.US);
                if (!lower.endsWith(".json") || lower.endsWith("/manifest.json")
                        || "manifest.json".equals(lower)) {
                    continue;
                }
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                byte[] buffer = new byte[8192];
                int total = 0;
                int n;
                while ((n = zip.read(buffer)) > 0) {
                    total += n;
                    if (total > MAX_ENTRY_BYTES) {
                        throw new IllegalArgumentException("压缩包内单项超过 40 MB");
                    }
                    out.write(buffer, 0, n);
                }
                try {
                    BackupData part = parse(c, new String(out.toByteArray(), StandardCharsets.UTF_8));
                    merge(merged, part);
                    any = true;
                } catch (Exception ignored) {
                    // Ignore unknown JSON entries; a valid manifest is optional.
                }
            }
        }
        if (!any) {
            throw new IllegalArgumentException("压缩包中没有可识别的 BusWake 备份");
        }
        return merged;
    }

    private static void merge(BackupData target, BackupData part) {
        if (part.hasSettingsStops) {
            target.hasSettingsStops = true;
            target.radiusM = part.radiusM;
            target.intervalMs = part.intervalMs;
            target.tripIntervalMs = part.tripIntervalMs;
            target.manualSpeed = part.manualSpeed;
            target.leadSeconds = part.leadSeconds;
            target.stops.clear();
            target.stops.addAll(part.stops);
        }
        if (part.hasTrips) {
            target.hasTrips = true;
            target.trips.addAll(part.trips);
        }
        target.legacyFull = target.legacyFull || part.legacyFull;
    }

    private static void readSettingsStops(Context c, JSONObject root, BackupData data) {
        data.radiusM = StopStore.getRadiusM(c);
        data.intervalMs = StopStore.getIntervalMs(c);
        data.tripIntervalMs = StopStore.getTripIntervalMs(c);
        data.manualSpeed = StopStore.getManualSpeed(c);
        data.leadSeconds = StopStore.getLeadTimeSeconds(c);

        JSONObject settings = root.optJSONObject("settings");
        if (settings != null) {
            data.radiusM = clamp((int) Math.round(metric(settings, "wakeRadius", "wakeRadiusM", data.radiusM)),
                    StopStore.MIN_RADIUS_M, StopStore.MAX_RADIUS_M);
            data.intervalMs = clamp((int) Math.round(metric(settings, "locationInterval",
                            "locationIntervalMs", data.intervalMs)),
                    StopStore.MIN_INTERVAL_MS, StopStore.MAX_INTERVAL_MS);
            data.tripIntervalMs = clamp((int) Math.round(metric(settings, "tripInterval",
                            "tripIntervalMs", data.tripIntervalMs)),
                    StopStore.MIN_TRIP_INTERVAL_MS, StopStore.MAX_TRIP_INTERVAL_MS);
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
    }

    private static void readTrip(JSONObject root, BackupData data) {
        JSONObject object = root.optJSONObject("trip");
        if (object == null) {
            object = root;
        }
        Trip trip = Trip.fromJson(object);
        if (trip.points.size() < 2) {
            throw new IllegalArgumentException("行程数据不足");
        }
        data.trips.add(trip);
    }

    private static void readTrips(JSONObject root, BackupData data) {
        JSONArray trips = root.optJSONArray("trips");
        if (trips == null) {
            return;
        }
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

    /**
     * Legacy full-backup behavior: replace all trips and settings.
     */
    public static boolean apply(Context c, BackupData data) {
        if (data == null) {
            return false;
        }
        if (!TripStore.replaceAll(c, data.trips)) {
            return false;
        }
        applySettingsStops(c, data);
        AppLog.i("Restore", "Legacy backup restored: stops=" + data.stops.size()
                + ", trips=" + data.trips.size());
        return true;
    }

    /**
     * Restore only the parts present in the imported file/archive. Trips are
     * merged by id; other existing trips are never deleted.
     */
    public static boolean applySelection(Context c, BackupData data) {
        if (data == null) {
            return false;
        }
        if (data.legacyFull) {
            return apply(c, data);
        }
        if (data.hasSettingsStops) {
            applySettingsStops(c, data);
        }
        if (data.hasTrips && !TripStore.upsertAll(c, data.trips)) {
            return false;
        }
        AppLog.i("Restore", "Selected backup restored: settingsStops=" + data.hasSettingsStops
                + ", trips=" + data.trips.size());
        return true;
    }

    private static void applySettingsStops(Context c, BackupData data) {
        StopStore.saveStops(c, data.stops);
        StopStore.setRadiusM(c, data.radiusM);
        StopStore.setIntervalMs(c, data.intervalMs);
        StopStore.setTripIntervalMs(c, data.tripIntervalMs);
        StopStore.setManualSpeed(c, data.manualSpeed);
        StopStore.setLeadTimeSeconds(c, data.leadSeconds);
        if (data.stops.isEmpty()) {
            StopStore.select(c, null);
        } else {
            StopStore.select(c, data.stops.get(0).id);
        }
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
