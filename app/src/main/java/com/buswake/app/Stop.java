package com.buswake.app;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.UUID;

public final class Stop {
    public String id;
    public String name;
    public double lat;
    public double lng;
    public long createdAt;

    public Stop(String id, String name, double lat, double lng, long createdAt) {
        this.id = id;
        this.name = name;
        this.lat = lat;
        this.lng = lng;
        this.createdAt = createdAt;
    }

    public static Stop create(String name, double lat, double lng) {
        return new Stop(UUID.randomUUID().toString(), name, lat, lng, System.currentTimeMillis());
    }

    public JSONObject toJson() {
        JSONObject o = new JSONObject();
        try {
            o.put("id", id);
            o.put("name", name);
            o.put("lat", lat);
            o.put("lng", lng);
            o.put("createdAt", createdAt);
        } catch (JSONException ignored) {
        }
        return o;
    }

    public static Stop fromJson(JSONObject o) {
        return new Stop(
                o.optString("id", UUID.randomUUID().toString()),
                o.optString("name", "下车站点"),
                o.optDouble("lat", 0),
                o.optDouble("lng", 0),
                o.optLong("createdAt", System.currentTimeMillis())
        );
    }
}
