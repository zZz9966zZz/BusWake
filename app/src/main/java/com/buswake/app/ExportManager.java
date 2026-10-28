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

    public static String timestamp() {
        return new SimpleDateFormat("yyyyMMdd-HHmm", Locale.US).format(new Date());
    }

    public static String buildBackupJson(Context c) {
        JSONObject root = new JSONObject();
        try {
            root.put("format", "BusWake backup");
            root.put("formatVersion", 1);
            root.put("exportedAt", new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssZ", Locale.US)
                    .format(new Date()));

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
            settings.put("wakeRadiusM", StopStore.getRadiusM(c));
            settings.put("locationIntervalMs", StopStore.getIntervalMs(c));
            settings.put("manualSpeedKmh", StopStore.getManualSpeed(c));
            settings.put("leadTimeSeconds", StopStore.getLeadTimeSeconds(c));
            settings.put("alarmSoundName", AlarmSoundStore.getDisplayName(c));
            settings.put("alarmSoundCustom", AlarmSoundStore.hasCustom(c));
            settings.put("alarmSoundAudioIncluded", false);
            root.put("settings", settings);

            JSONArray stops = new JSONArray();
            for (Stop s : StopStore.loadStops(c)) {
                stops.put(s.toJson());
            }
            root.put("stops", stops);

            JSONArray trips = new JSONArray();
            List<Trip> all = TripStore.loadAll(c);
            for (Trip t : all) {
                trips.put(t.toJson());
            }
            root.put("trips", trips);
            root.put("stopCount", stops.length());
            root.put("tripCount", trips.length());
        } catch (Exception e) {
            try {
                root.put("error", String.valueOf(e.getMessage()));
            } catch (Exception ignored) {
            }
        }
        return root.toString();
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
