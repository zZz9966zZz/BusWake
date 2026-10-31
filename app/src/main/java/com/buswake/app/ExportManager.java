package com.buswake.app;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.net.Uri;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public final class ExportManager {
    private ExportManager() {
    }

    public static Intent createDocument(String mime, String title) {
        Intent i = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType(mime);
        i.putExtra(Intent.EXTRA_TITLE, title);
        return i;
    }

    public static Intent openDocument(String mime) {
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType(mime);
        return i;
    }

    public static String timestamp() {
        return new SimpleDateFormat("yyyyMMdd-HHmm", Locale.US).format(new Date());
    }

    private static String isoTime(long time) {
        return new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssZ", Locale.US).format(new Date(time));
    }

    private static JSONObject metric(Object value, String unit) throws Exception {
        JSONObject o = new JSONObject();
        o.put("value", value);
        o.put("unit", unit);
        return o;
    }

    public static String buildBackupJson(Context c) {
        JSONObject root = new JSONObject();
        try {
            root.put("format", "BusWake backup");
            root.put("formatVersion", 2);
            root.put("exportedAt", isoTime(System.currentTimeMillis()));
            root.put("units", units());

            JSONObject app = new JSONObject();
            app.put("package", c.getPackageName());
            try {
                PackageInfo info = c.getPackageManager().getPackageInfo(c.getPackageName(), 0);
                app.put("versionName", info.versionName);
                app.put("versionCode", info.versionCode);
            } catch (Exception ignored) {
            }
            root.put("app", app);

            JSONObject settings = new JSONObject();
            settings.put("wakeRadius", metric(StopStore.getRadiusM(c), "m"));
            settings.put("locationInterval", metric(StopStore.getIntervalMs(c), "ms"));
            settings.put("tripInterval", metric(StopStore.getTripIntervalMs(c), "ms"));
            settings.put("manualSpeed", metric(Trip.round1(StopStore.getManualSpeed(c)), "km/h"));
            settings.put("leadTime", metric(StopStore.getLeadTimeSeconds(c), "s"));
            JSONObject sound = new JSONObject();
            sound.put("name", AlarmSoundStore.getDisplayName(c));
            sound.put("custom", AlarmSoundStore.hasCustom(c));
            sound.put("audioIncluded", false);
            settings.put("alarmSound", sound);
            root.put("settings", settings);

            JSONArray stops = new JSONArray();
            for (Stop s : StopStore.loadStops(c)) {
                JSONObject item = new JSONObject();
                item.put("id", s.id);
                item.put("name", s.name);
                item.put("latitude", s.lat);
                item.put("longitude", s.lng);
                item.put("createdAtIso", isoTime(s.createdAt));
                item.put("createdAtEpochMs", s.createdAt);
                item.put("units", units());
                stops.put(item);
            }
            root.put("stops", stops);

            JSONArray trips = new JSONArray();
            List<Trip> all = TripStore.loadAll(c);
            for (Trip t : all) {
                trips.put(t.toExportJson());
            }
            root.put("trips", trips);
            root.put("summary", summary(stops.length(), trips.length()));
        } catch (Exception e) {
            try {
                root.put("error", String.valueOf(e.getMessage()));
            } catch (Exception ignored) {
            }
        }
        try {
            return root.toString(2);
        } catch (Exception e) {
            return root.toString();
        }
    }

    private static JSONObject units() throws Exception {
        JSONObject units = new JSONObject();
        units.put("distance", "m");
        units.put("speed", "km/h");
        units.put("time", "s");
        units.put("latitude", "degrees");
        units.put("longitude", "degrees");
        units.put("timestamp", "ISO 8601");
        return units;
    }

    private static JSONObject summary(int stops, int trips) throws Exception {
        JSONObject o = new JSONObject();
        o.put("stopCount", stops);
        o.put("tripCount", trips);
        o.put("alarmSoundAudioIncluded", false);
        return o;
    }

    public static boolean writeText(Context c, Uri uri, String text) {
        if (uri == null || text == null) {
            return false;
        }
        try (OutputStream out = c.getContentResolver().openOutputStream(uri, "wt")) {
            if (out == null) {
                return false;
            }
            out.write(text.getBytes(StandardCharsets.UTF_8));
            out.flush();
            return true;
        } catch (Exception e) {
            AppLog.e("Export", "write failed", e);
            return false;
        }
    }
}
