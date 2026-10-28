package com.buswake.app;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

public final class StopStore {
    public static final String PREFS = "buswake_prefs";
    public static final String KEY_STOPS = "stops";
    public static final String KEY_SELECTED = "selected_stop_id";
    public static final String KEY_GUARD_RUNNING = "guard_running";
    public static final String KEY_ACTIVE_ID = "active_stop_id";
    public static final String KEY_LAST_DISTANCE = "last_distance";
    public static final String KEY_LAST_SPEED = "last_speed";
    public static final String KEY_LAST_ACCURACY = "last_accuracy";
    public static final String KEY_LAST_UPDATE = "last_update";
    public static final String KEY_ALARM_ACTIVE = "alarm_active";
    public static final String KEY_LAST_MAP_LAT = "last_map_lat";
    public static final String KEY_LAST_MAP_LNG = "last_map_lng";
    public static final String KEY_STOP_SEQUENCE = "stop_sequence";
    public static final String KEY_RADIUS_M = "wake_radius_m";
    public static final String KEY_INTERVAL_MS = "location_interval_ms";
    public static final String KEY_MANUAL_SPEED = "manual_speed_kmh";
    public static final String KEY_LEAD_TIME_SEC = "wake_lead_time_sec";

    public static final int DEFAULT_RADIUS_M = 2000;
    public static final int DEFAULT_INTERVAL_MS = 2000;
    public static final int MIN_RADIUS_M = 200;
    public static final int MAX_RADIUS_M = 20000;
    public static final int MIN_INTERVAL_MS = 1000;
    public static final int MAX_INTERVAL_MS = 60000;

    public static final float DEFAULT_MANUAL_SPEED = 80f;
    public static final float MIN_SPEED_KMH = 1f;
    public static final float MAX_SPEED_KMH = 150f;
    public static final int DEFAULT_LEAD_TIME_SECONDS = 90;
    public static final int MIN_LEAD_TIME_SECONDS = 10;
    public static final int MAX_LEAD_TIME_SECONDS = 600;

    private StopStore() {
    }

    public static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public static List<Stop> loadStops(Context c) {
        ArrayList<Stop> result = new ArrayList<>();
        String raw = prefs(c).getString(KEY_STOPS, "[]");
        try {
            JSONArray arr = new JSONArray(raw);
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.optJSONObject(i);
                if (o != null) {
                    Stop s = Stop.fromJson(o);
                    if (s.lat != 0 || s.lng != 0) {
                        result.add(s);
                    }
                }
            }
        } catch (Exception ignored) {
        }
        return result;
    }

    public static void saveStops(Context c, List<Stop> stops) {
        JSONArray arr = new JSONArray();
        for (Stop s : stops) {
            arr.put(s.toJson());
        }
        prefs(c).edit().putString(KEY_STOPS, arr.toString()).apply();
    }

    public static Stop findById(Context c, String id) {
        if (id == null) {
            return null;
        }
        for (Stop s : loadStops(c)) {
            if (id.equals(s.id)) {
                return s;
            }
        }
        return null;
    }

    public static String selectedId(Context c) {
        return prefs(c).getString(KEY_SELECTED, null);
    }

    public static void select(Context c, String id) {
        prefs(c).edit().putString(KEY_SELECTED, id).apply();
    }

    public static boolean isGuardRunning(Context c) {
        return prefs(c).getBoolean(KEY_GUARD_RUNNING, false);
    }

    public static void setAlarmActive(Context c, boolean active) {
        prefs(c).edit().putBoolean(KEY_ALARM_ACTIVE, active).apply();
    }

    public static String formatDistance(double meters) {
        if (meters < 0) {
            return "--";
        }
        if (meters < 1000) {
            return String.format(java.util.Locale.CHINA, "%.0f 米", meters);
        }
        return String.format(java.util.Locale.CHINA, "%.1f 公里", meters / 1000.0);
    }

    public static int getRadiusM(Context c) {
        int value = prefs(c).getInt(KEY_RADIUS_M, DEFAULT_RADIUS_M);
        return Math.max(MIN_RADIUS_M, Math.min(MAX_RADIUS_M, value));
    }

    public static void setRadiusM(Context c, int meters) {
        int value = Math.max(MIN_RADIUS_M, Math.min(MAX_RADIUS_M, meters));
        prefs(c).edit().putInt(KEY_RADIUS_M, value).apply();
    }

    public static int getIntervalMs(Context c) {
        int value = prefs(c).getInt(KEY_INTERVAL_MS, DEFAULT_INTERVAL_MS);
        return Math.max(MIN_INTERVAL_MS, Math.min(MAX_INTERVAL_MS, value));
    }

    public static void setIntervalMs(Context c, int millis) {
        int value = Math.max(MIN_INTERVAL_MS, Math.min(MAX_INTERVAL_MS, millis));
        prefs(c).edit().putInt(KEY_INTERVAL_MS, value).apply();
    }

    public static String formatRadius(int meters) {
        if (meters < 1000) {
            return String.format(java.util.Locale.CHINA, "%d 米", meters);
        }
        if (meters % 1000 == 0) {
            return String.format(java.util.Locale.CHINA, "%d 公里", meters / 1000);
        }
        return String.format(java.util.Locale.CHINA, "%.1f 公里", meters / 1000.0);
    }

    public static String formatRadiusShort(int meters) {
        if (meters < 1000) {
            return String.format(java.util.Locale.CHINA, "%d M", meters);
        }
        if (meters % 1000 == 0) {
            return String.format(java.util.Locale.CHINA, "%d KM", meters / 1000);
        }
        return String.format(java.util.Locale.CHINA, "%.1f KM", meters / 1000.0);
    }

    public static int leadTimeSeconds(int meters, double speedKmh) {
        if (speedKmh <= 0) {
            return 0;
        }
        return (int) Math.round(meters / 1000.0 / speedKmh * 3600.0);
    }

    public static String formatKmInput(int meters) {
        if (meters % 1000 == 0) {
            return String.valueOf(meters / 1000);
        }
        return String.format(java.util.Locale.CHINA, "%.1f", meters / 1000.0);
    }

    public static float getManualSpeed(Context c) {
        float value = prefs(c).getFloat(KEY_MANUAL_SPEED, DEFAULT_MANUAL_SPEED);
        return Math.max(MIN_SPEED_KMH, Math.min(MAX_SPEED_KMH, value));
    }

    public static void setManualSpeed(Context c, float speedKmh) {
        float value = Math.max(MIN_SPEED_KMH, Math.min(MAX_SPEED_KMH, speedKmh));
        prefs(c).edit().putFloat(KEY_MANUAL_SPEED, value).apply();
    }

    public static int getLeadTimeSeconds(Context c) {
        int value = prefs(c).getInt(KEY_LEAD_TIME_SEC, DEFAULT_LEAD_TIME_SECONDS);
        return Math.max(MIN_LEAD_TIME_SECONDS, Math.min(MAX_LEAD_TIME_SECONDS, value));
    }

    public static void setLeadTimeSeconds(Context c, int seconds) {
        int value = Math.max(MIN_LEAD_TIME_SECONDS, Math.min(MAX_LEAD_TIME_SECONDS, seconds));
        prefs(c).edit().putInt(KEY_LEAD_TIME_SEC, value).apply();
    }

    public static int recommendDistanceM(double speedKmh, int leadSeconds) {
        if (speedKmh <= 0 || leadSeconds <= 0) {
            return 0;
        }
        double meters = speedKmh / 3.6 * leadSeconds;
        int rounded = (int) Math.round(meters / 100.0) * 100;
        return Math.max(MIN_RADIUS_M, Math.min(MAX_RADIUS_M, rounded));
    }

    public static String formatSpeed(double speedKmh) {
        if (speedKmh < 0) {
            return "--";
        }
        return String.format(java.util.Locale.CHINA, "%.0f km/h", speedKmh);
    }

    public static String formatLeadDuration(int seconds) {
        if (seconds < 60) {
            return seconds + " 秒";
        }
        int minutes = seconds / 60;
        int rest = seconds % 60;
        return rest == 0 ? minutes + " 分钟" : minutes + " 分 " + rest + " 秒";
    }
}
