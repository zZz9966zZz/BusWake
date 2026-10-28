package com.buswake.app;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public final class TripStore {
    private static final String PREFS = "trip_record_state";
    private static final String KEY_RECORDING = "recording";
    private static final String KEY_STARTED = "started_at";
    private static final String KEY_DISTANCE = "distance_m";
    private static final String KEY_SPEED = "speed_kmh";
    private static final String KEY_POINTS = "point_count";
    private static final String KEY_UPDATED = "updated_at";
    private static final String KEY_LAST_SAVED_ID = "last_saved_id";
    private static final String KEY_SAVED_COUNTER = "saved_counter";
    private static final String FILE_NAME = "trips.json";
    private static final Object LOCK = new Object();

    private TripStore() {
    }

    public static SharedPreferences state(Context c) {
        return c.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public static List<Trip> loadAll(Context c) {
        synchronized (LOCK) {
            ArrayList<Trip> trips = new ArrayList<>();
            File file = new File(c.getFilesDir(), FILE_NAME);
            if (!file.exists()) {
                return trips;
            }
            try (FileInputStream in = new FileInputStream(file);
                 InputStreamReader reader = new InputStreamReader(in, StandardCharsets.UTF_8)) {
                StringBuilder sb = new StringBuilder();
                char[] buf = new char[4096];
                int n;
                while ((n = reader.read(buf)) > 0) {
                    sb.append(buf, 0, n);
                }
                JSONArray arr = new JSONArray(sb.toString());
                for (int i = 0; i < arr.length(); i++) {
                    JSONObject o = arr.optJSONObject(i);
                    if (o != null) {
                        try {
                            trips.add(Trip.fromJson(o));
                        } catch (Exception ignored) {
                        }
                    }
                }
            } catch (Exception ignored) {
            }
            Collections.sort(trips, new Comparator<Trip>() {
                @Override
                public int compare(Trip a, Trip b) {
                    return Long.compare(b.startedAt, a.startedAt);
                }
            });
            return trips;
        }
    }

    public static Trip findById(Context c, String id) {
        if (id == null) {
            return null;
        }
        for (Trip t : loadAll(c)) {
            if (id.equals(t.id)) {
                return t;
            }
        }
        return null;
    }

    public static boolean add(Context c, Trip trip) {
        if (trip == null || !trip.hasRoute()) {
            return false;
        }
        synchronized (LOCK) {
            ArrayList<Trip> trips = new ArrayList<>(loadAll(c));
            trips.add(0, trip);
            return writeAll(c, trips);
        }
    }

    public static boolean delete(Context c, String id) {
        synchronized (LOCK) {
            ArrayList<Trip> trips = new ArrayList<>(loadAll(c));
            for (int i = 0; i < trips.size(); i++) {
                if (trips.get(i).id.equals(id)) {
                    trips.remove(i);
                    return writeAll(c, trips);
                }
            }
            return false;
        }
    }

    private static boolean writeAll(Context c, List<Trip> trips) {
        JSONArray arr = new JSONArray();
        for (Trip t : trips) {
            arr.put(t.toJson());
        }
        File file = new File(c.getFilesDir(), FILE_NAME);
        File tmp = new File(c.getFilesDir(), FILE_NAME + ".tmp");
        try (FileOutputStream out = new FileOutputStream(tmp);
             OutputStreamWriter writer = new OutputStreamWriter(out, StandardCharsets.UTF_8)) {
            writer.write(arr.toString());
            writer.flush();
            if (file.exists() && !file.delete()) {
                return false;
            }
            return tmp.renameTo(file);
        } catch (Exception e) {
            return false;
        }
    }

    public static boolean isRecording(Context c) {
        return state(c).getBoolean(KEY_RECORDING, false);
    }

    public static long recordingStartedAt(Context c) {
        return state(c).getLong(KEY_STARTED, 0);
    }

    public static float recordingDistanceM(Context c) {
        return state(c).getFloat(KEY_DISTANCE, 0);
    }

    public static float recordingSpeedKmh(Context c) {
        return state(c).getFloat(KEY_SPEED, 0);
    }

    public static int recordingPointCount(Context c) {
        return state(c).getInt(KEY_POINTS, 0);
    }

    public static long recordingUpdatedAt(Context c) {
        return state(c).getLong(KEY_UPDATED, 0);
    }

    public static String lastSavedId(Context c) {
        return state(c).getString(KEY_LAST_SAVED_ID, "");
    }

    public static int savedCounter(Context c) {
        return state(c).getInt(KEY_SAVED_COUNTER, 0);
    }

    public static void startRecording(Context c, long startedAt) {
        state(c).edit()
                .putBoolean(KEY_RECORDING, true)
                .putLong(KEY_STARTED, startedAt)
                .putFloat(KEY_DISTANCE, 0)
                .putFloat(KEY_SPEED, 0)
                .putInt(KEY_POINTS, 0)
                .putLong(KEY_UPDATED, System.currentTimeMillis())
                .apply();
    }

    public static void updateRecording(Context c, double distanceM, double speedKmh, int pointCount) {
        state(c).edit()
                .putFloat(KEY_DISTANCE, (float) distanceM)
                .putFloat(KEY_SPEED, (float) speedKmh)
                .putInt(KEY_POINTS, pointCount)
                .putLong(KEY_UPDATED, System.currentTimeMillis())
                .apply();
    }

    public static void stopRecording(Context c) {
        state(c).edit().putBoolean(KEY_RECORDING, false).apply();
    }

    public static void markSaved(Context c, String id) {
        state(c).edit()
                .putString(KEY_LAST_SAVED_ID, id == null ? "" : id)
                .putInt(KEY_SAVED_COUNTER, savedCounter(c) + 1)
                .putBoolean(KEY_RECORDING, false)
                .apply();
    }

    public static void clearLive(Context c) {
        state(c).edit()
                .putBoolean(KEY_RECORDING, false)
                .putFloat(KEY_DISTANCE, 0)
                .putFloat(KEY_SPEED, 0)
                .putInt(KEY_POINTS, 0)
                .apply();
    }
}
