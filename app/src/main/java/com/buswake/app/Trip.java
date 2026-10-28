package com.buswake.app;

import org.json.JSONArray;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

public final class Trip {
    public String id;
    public long startedAt;
    public long endedAt;
    public final List<Point> points = new ArrayList<>();

    public static final class Point {
        public double lat;
        public double lng;
        public long elapsedMs;
        public double distanceM;
        public double speedKmh;

        public Point(double lat, double lng, long elapsedMs, double distanceM, double speedKmh) {
            this.lat = lat;
            this.lng = lng;
            this.elapsedMs = elapsedMs;
            this.distanceM = distanceM;
            this.speedKmh = speedKmh;
        }
    }

    public static Trip create(long startedAt) {
        Trip t = new Trip();
        t.id = UUID.randomUUID().toString();
        t.startedAt = startedAt;
        t.endedAt = startedAt;
        return t;
    }

    public void addPoint(Point p) {
        points.add(p);
    }

    public boolean hasRoute() {
        return points.size() >= 2 && totalDistanceM() >= 5;
    }

    public double totalDistanceM() {
        return points.isEmpty() ? 0 : points.get(points.size() - 1).distanceM;
    }

    public long durationMs() {
        if (endedAt > startedAt) {
            return endedAt - startedAt;
        }
        if (points.size() >= 2) {
            return points.get(points.size() - 1).elapsedMs;
        }
        return 0;
    }

    public double avgSpeedKmh() {
        long seconds = durationMs() / 1000;
        if (seconds <= 0) {
            return 0;
        }
        return totalDistanceM() / seconds * 3.6;
    }

    public double maxSpeedKmh() {
        double max = 0;
        for (Point p : points) {
            if (p.speedKmh > max) {
                max = p.speedKmh;
            }
        }
        return max;
    }

    public String title() {
        return new SimpleDateFormat("M月d日 HH:mm", Locale.CHINA).format(new Date(startedAt)) + " 行程";
    }

    public String subtitle() {
        return StopStore.formatDistance(totalDistanceM()) + " · " + formatDuration(durationMs() / 1000)
                + " · 均速 " + String.format(Locale.CHINA, "%.0f km/h", avgSpeedKmh());
    }

    public static String formatDuration(long seconds) {
        if (seconds < 0) {
            seconds = 0;
        }
        long h = seconds / 3600;
        long m = (seconds % 3600) / 60;
        long s = seconds % 60;
        if (h > 0) {
            return String.format(Locale.CHINA, "%d:%02d:%02d", h, m, s);
        }
        return String.format(Locale.CHINA, "%d:%02d", m, s);
    }

    public JSONObject toJson() {
        JSONObject o = new JSONObject();
        try {
            o.put("id", id);
            o.put("startedAt", startedAt);
            o.put("endedAt", endedAt);
            JSONArray arr = new JSONArray();
            for (Point p : points) {
                JSONArray a = new JSONArray();
                a.put(p.lat);
                a.put(p.lng);
                a.put(p.elapsedMs);
                a.put(p.distanceM);
                a.put(p.speedKmh);
                arr.put(a);
            }
            o.put("points", arr);
        } catch (Exception ignored) {
        }
        return o;
    }

    public static Trip fromJson(JSONObject o) {
        Trip t = new Trip();
        t.id = o.optString("id", UUID.randomUUID().toString());
        t.startedAt = o.optLong("startedAt", System.currentTimeMillis());
        t.endedAt = o.optLong("endedAt", t.startedAt);
        JSONArray arr = o.optJSONArray("points");
        if (arr != null) {
            for (int i = 0; i < arr.length(); i++) {
                JSONArray a = arr.optJSONArray(i);
                if (a == null || a.length() < 5) {
                    continue;
                }
                t.points.add(new Point(
                        a.optDouble(0), a.optDouble(1), a.optLong(2),
                        a.optDouble(3), a.optDouble(4)
                ));
            }
        }
        return t;
    }

    public String mapJson() {
        JSONArray arr = new JSONArray();
        for (Point p : points) {
            try {
                JSONArray a = new JSONArray();
                a.put(p.lat);
                a.put(p.lng);
                arr.put(a);
            } catch (Exception ignored) {
            }
        }
        return arr.toString();
    }
}
