package com.cursor.hr40;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

public final class WorkoutSession {
    public static final String TYPE_AEROBIC = "aerobic";
    public static final String TYPE_STRENGTH = "strength";

    public final String id;
    public final long startMillis;
    public long endMillis;
    public final String workoutType;
    private final ArrayList<HeartRateSample> samples = new ArrayList<>();
    private final ArrayList<StrengthSet> strengthSets = new ArrayList<>();

    public WorkoutSession(String id, long startMillis, long endMillis, String workoutType) {
        this.id = id;
        this.startMillis = startMillis;
        this.endMillis = endMillis;
        this.workoutType = TYPE_STRENGTH.equals(workoutType) ? TYPE_STRENGTH : TYPE_AEROBIC;
    }

    public static WorkoutSession startNow(String workoutType) {
        return new WorkoutSession(UUID.randomUUID().toString(), System.currentTimeMillis(), 0L, workoutType);
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

    public void addStrengthSet(StrengthSet set) {
        strengthSets.add(set);
    }

    public List<StrengthSet> strengthSets() {
        return Collections.unmodifiableList(strengthSets);
    }

    public JSONObject toJson() throws JSONException {
        JSONObject json = new JSONObject();
        json.put("id", id);
        json.put("startMillis", startMillis);
        json.put("endMillis", endMillis);
        json.put("workoutType", workoutType);
        JSONArray array = new JSONArray();
        for (HeartRateSample sample : samples) {
            array.put(sample.toJson());
        }
        json.put("samples", array);
        JSONArray strengthArray = new JSONArray();
        for (StrengthSet set : strengthSets) {
            strengthArray.put(set.toJson());
        }
        json.put("strengthSets", strengthArray);
        return json;
    }

    public static WorkoutSession fromJson(JSONObject json) {
        WorkoutSession session = new WorkoutSession(
                json.optString("id", UUID.randomUUID().toString()),
                json.optLong("startMillis"),
                json.optLong("endMillis"),
                json.optString("workoutType", TYPE_AEROBIC));
        JSONArray array = json.optJSONArray("samples");
        if (array != null) {
            for (int i = 0; i < array.length(); i++) {
                JSONObject item = array.optJSONObject(i);
                if (item != null) {
                    session.addSample(HeartRateSample.fromJson(item));
                }
            }
        }
        JSONArray strengthArray = json.optJSONArray("strengthSets");
        if (strengthArray != null) {
            for (int i = 0; i < strengthArray.length(); i++) {
                JSONObject item = strengthArray.optJSONObject(i);
                if (item != null) {
                    session.addStrengthSet(StrengthSet.fromJson(item));
                }
            }
        }
        return session;
    }
}
