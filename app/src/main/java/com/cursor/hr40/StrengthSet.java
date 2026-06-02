package com.cursor.hr40;

import org.json.JSONException;
import org.json.JSONObject;

public final class StrengthSet {
    public static final String UNIT_KG = "kg";
    public static final String UNIT_LB = "lb";

    public final String exerciseName;
    public final double weight;
    public final String weightUnit;
    public final int reps;
    public final long timestampMillis;

    public StrengthSet(String exerciseName, double weight, String weightUnit, int reps, long timestampMillis) {
        this.exerciseName = exerciseName;
        this.weight = weight;
        this.weightUnit = weightUnit;
        this.reps = reps;
        this.timestampMillis = timestampMillis;
    }

    public JSONObject toJson() throws JSONException {
        JSONObject json = new JSONObject();
        json.put("exerciseName", exerciseName);
        json.put("weight", weight);
        json.put("weightUnit", weightUnit);
        json.put("reps", reps);
        json.put("timestampMillis", timestampMillis);
        return json;
    }

    public static StrengthSet fromJson(JSONObject json) {
        return new StrengthSet(
                json.optString("exerciseName", ""),
                json.optDouble("weight"),
                json.optString("weightUnit", UNIT_KG),
                json.optInt("reps"),
                json.optLong("timestampMillis"));
    }

    public String displayWeight() {
        if (UNIT_LB.equals(weightUnit)) {
            return String.format(java.util.Locale.US, "%.1f lb", weight);
        }
        return String.format(java.util.Locale.US, "%.1f kg", weight);
    }
}
