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
    public double speedLimitKmh;
    public boolean speedReviewPending;
    public final List<Point> points = new ArrayList<>();

    public static final class Point {
        public double lat;
        public double lng;
        public long elapsedMs;
        public double distanceM;
        public double speedKmh;
        public boolean overLimit;

        public Point(double lat, double lng, long elapsedMs, double distanceM, double speedKmh) {
            this(lat, lng, elapsedMs, distanceM, speedKmh, false);
        }

        public Point(double lat, double lng, long elapsedMs, double distanceM,
                     double speedKmh, boolean overLimit) {
            this.lat = lat;
            this.lng = lng;
            this.elapsedMs = elapsedMs;
            this.distanceM = distanceM;
            this.speedKmh = speedKmh;
            this.overLimit = overLimit;
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

    public int overLimitCount() {
        int count = 0;
        for (Point p : points) {
            if (p.overLimit) {
                count++;
            }
        }
        return count;
    }

    public boolean hasOverLimitPoints() {
        return overLimitCount() > 0;
    }

    /**
     * Average of the nearest valid neighbours. First/last points use the one
     * neighbour they have; a lone point keeps its own speed.
     */
    public double averageSpeedAround(int index) {
        if (points.isEmpty()) {
            return 0;
        }
        if (index < 0 || index >= points.size()) {
            return points.get(points.size() - 1).speedKmh;
        }
        double sum = 0;
        int count = 0;
        if (index > 0) {
            sum += points.get(index - 1).speedKmh;
            count++;
        }
        if (index + 1 < points.size()) {
            sum += points.get(index + 1).speedKmh;
            count++;
        }
        return count == 0 ? points.get(index).speedKmh : round1(sum / count);
    }

    public String title() {
        return new SimpleDateFormat("M月d日 HH:mm", Locale.CHINA).format(new Date(startedAt)) + " 行程";
    }

    public String subtitle() {
        return StopStore.formatDistance(totalDistanceM()) + " · " + formatDuration(durationMs() / 1000)
                + " · 均速 " + String.format(Locale.CHINA, "%.1f km/h", avgSpeedKmh());
    }

    public static double round1(double value) {
        return Math.round(value * 10.0) / 10.0;
    }

    public static String formatSpeed1(double speedKmh) {
        return String.format(Locale.CHINA, "%.1f km/h", speedKmh);
    }

    /**
     * Round speeds to one decimal and correct isolated speed spikes.
     * If a point is above 30 km/h and differs from the average of its neighbours
     * by more than 50%, the point keeps its position but adopts that average speed.
     */
    public void normalizeRecordedSpeeds() {
        for (Point p : points) {
            p.speedKmh = round1(Math.max(0, p.speedKmh));
        }
        for (int i = 1; i < points.size() - 1; i++) {
            Point p = points.get(i);
            if (p.overLimit) {
                // 超速点保留原始值，等用户结束后逐点确认。
                continue;
            }
            if (p.speedKmh <= 30.0) {
                continue;
            }
            double avg = round1((points.get(i - 1).speedKmh + points.get(i + 1).speedKmh) / 2.0);
            if (Math.abs(p.speedKmh - avg) > Math.abs(avg) * 0.5) {
                p.speedKmh = Math.max(0, avg);
            }
        }
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
            o.put("speedLimitKmh", round1(speedLimitKmh));
            o.put("speedReviewPending", speedReviewPending);
            JSONArray arr = new JSONArray();
            for (Point p : points) {
                JSONArray a = new JSONArray();
                a.put(p.lat);
                a.put(p.lng);
                a.put(p.elapsedMs);
                a.put(p.distanceM);
                a.put(p.speedKmh);
                a.put(p.overLimit ? 1 : 0);
                arr.put(a);
            }
            o.put("points", arr);
        } catch (Exception ignored) {
        }
        return o;
    }

    /**
     * Readable, self-describing export shape. Field names carry units and each
     * numeric value is paired with a units block at the trip level.
     */
    public JSONObject toExportJson() {
        JSONObject o = new JSONObject();
        try {
            o.put("id", id);
            o.put("startedAtIso", isoTime(startedAt));
            o.put("endedAtIso", isoTime(endedAt));
            o.put("startedAtEpochMs", startedAt);
            o.put("endedAtEpochMs", endedAt);
            o.put("durationSeconds", round1(durationMs() / 1000.0));
            o.put("distanceMeters", round1(totalDistanceM()));
            o.put("averageSpeedKmh", round1(avgSpeedKmh()));
            o.put("maximumSpeedKmh", round1(maxSpeedKmh()));
            o.put("speedLimitKmh", round1(speedLimitKmh));
            o.put("speedReviewPending", speedReviewPending);
            o.put("overLimitCount", overLimitCount());
            o.put("pointCount", points.size());
            JSONObject units = new JSONObject();
            units.put("distance", "m");
            units.put("speed", "km/h");
            units.put("time", "s");
            units.put("latitude", "degrees");
            units.put("longitude", "degrees");
            o.put("units", units);

            JSONArray format = new JSONArray();
            format.put("elapsedSeconds");
            format.put("distanceMeters");
            format.put("speedKmh");
            format.put("latitude");
            format.put("longitude");
            format.put("overLimit");
            o.put("pointFormat", format);

            JSONArray arr = new JSONArray();
            for (Point p : points) {
                JSONArray a = new JSONArray();
                a.put(round1(p.elapsedMs / 1000.0));
                a.put(round1(p.distanceM));
                a.put(round1(p.speedKmh));
                a.put(p.lat);
                a.put(p.lng);
                a.put(p.overLimit ? 1 : 0);
                arr.put(a);
            }
            o.put("points", arr);
        } catch (Exception ignored) {
        }
        return o;
    }

    private static String isoTime(long time) {
        return new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssZ", Locale.US).format(new Date(time));
    }

    public static Trip fromJson(JSONObject o) {
        Trip t = new Trip();
        t.id = o.optString("id", UUID.randomUUID().toString());
        t.startedAt = o.optLong("startedAt", System.currentTimeMillis());
        t.endedAt = o.optLong("endedAt", t.startedAt);
        t.speedLimitKmh = o.optDouble("speedLimitKmh", 0);
        t.speedReviewPending = o.optBoolean("speedReviewPending", false);
        JSONArray arr = o.optJSONArray("points");
        boolean compactExport = o.optJSONArray("pointFormat") != null;
        if (arr != null) {
            for (int i = 0; i < arr.length(); i++) {
                Object item = arr.opt(i);
                if (item instanceof JSONArray) {
                    JSONArray a = (JSONArray) item;
                    if (a.length() < 5) {
                        continue;
                    }
                    if (compactExport) {
                        long elapsedMs = Math.round(a.optDouble(0) * 1000.0);
                        double dist = a.optDouble(1);
                        double speed = round1(a.optDouble(2));
                        double lat = a.optDouble(3);
                        double lng = a.optDouble(4);
                        boolean over = a.length() > 5 && a.optInt(5, 0) != 0;
                        if (lat != 0 || lng != 0) {
                            t.points.add(new Point(lat, lng, elapsedMs, dist, speed, over));
                        }
                    } else {
                        boolean over = a.length() > 5 && a.optInt(5, 0) != 0;
                        t.points.add(new Point(
                                a.optDouble(0), a.optDouble(1), a.optLong(2),
                                a.optDouble(3), round1(a.optDouble(4)), over
                        ));
                    }
                } else if (item instanceof JSONObject) {
                    JSONObject p = (JSONObject) item;
                    double lat = p.optDouble("latitude", p.optDouble("lat", 0));
                    double lng = p.optDouble("longitude", p.optDouble("lng", 0));
                    long elapsedMs;
                    if (p.has("elapsedSeconds")) {
                        elapsedMs = Math.round(p.optDouble("elapsedSeconds") * 1000.0);
                    } else {
                        elapsedMs = p.optLong("elapsedMs", 0);
                    }
                    double dist = p.optDouble("distanceMeters", p.optDouble("distanceM", 0));
                    double speed = p.optDouble("speedKmh", p.optDouble("speed", 0));
                    if (lat != 0 || lng != 0) {
                        t.points.add(new Point(lat, lng, elapsedMs, dist, round1(speed),
                                p.optBoolean("overLimit", false)));
                    }
                }
            }
        }
        if (o.has("startedAtEpochMs")) {
            t.startedAt = o.optLong("startedAtEpochMs", t.startedAt);
        }
        if (o.has("endedAtEpochMs")) {
            t.endedAt = o.optLong("endedAtEpochMs", t.endedAt);
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
