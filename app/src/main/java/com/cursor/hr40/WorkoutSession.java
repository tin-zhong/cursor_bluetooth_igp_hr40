package com.cursor.hr40;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

public final class WorkoutSession {
    public final String id;
    public final long startMillis;
    public long endMillis;
    private final ArrayList<HeartRateSample> samples = new ArrayList<>();

    public WorkoutSession(String id, long startMillis, long endMillis) {
        this.id = id;
        this.startMillis = startMillis;
        this.endMillis = endMillis;
    }

    public static WorkoutSession startNow() {
        return new WorkoutSession(UUID.randomUUID().toString(), System.currentTimeMillis(), 0L);
    }

    public boolean isActive() {
        return endMillis <= 0L;
    }

    public long durationMillis() {
        long end = isActive() ? System.currentTimeMillis() : endMillis;
        return Math.max(0L, end - startMillis);
    }

    public void finish() {
        if (isActive()) {
            endMillis = System.currentTimeMillis();
        }
    }

    public void addSample(HeartRateSample sample) {
        samples.add(sample);
    }

    public List<HeartRateSample> samples() {
        return Collections.unmodifiableList(samples);
    }

    public JSONObject toJson() throws JSONException {
        JSONObject json = new JSONObject();
        json.put("id", id);
        json.put("startMillis", startMillis);
        json.put("endMillis", endMillis);
        JSONArray array = new JSONArray();
        for (HeartRateSample sample : samples) {
            array.put(sample.toJson());
        }
        json.put("samples", array);
        return json;
    }

    public static WorkoutSession fromJson(JSONObject json) {
        WorkoutSession session = new WorkoutSession(
                json.optString("id", UUID.randomUUID().toString()),
                json.optLong("startMillis"),
                json.optLong("endMillis"));
        JSONArray array = json.optJSONArray("samples");
        if (array != null) {
            for (int i = 0; i < array.length(); i++) {
                JSONObject item = array.optJSONObject(i);
                if (item != null) {
                    session.addSample(HeartRateSample.fromJson(item));
                }
            }
        }
        return session;
    }
}
