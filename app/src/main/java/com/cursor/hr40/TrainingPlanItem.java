package com.cursor.hr40;

import org.json.JSONException;
import org.json.JSONObject;

public final class TrainingPlanItem {
    public static final int DEFAULT_REST_SECONDS = 60;
    public static final int DEFAULT_SUGGESTED_REPS = 8;

    public final String exerciseName;
    public final int plannedSets;
    public final int suggestedReps;
    public final int restSeconds;
    public final int position;

    public TrainingPlanItem(String exerciseName, int plannedSets, int suggestedReps, int restSeconds, int position) {
        this.exerciseName = exerciseName == null ? "" : exerciseName.trim();
        this.plannedSets = Math.max(0, plannedSets);
        this.suggestedReps = Math.max(0, Math.min(1000, suggestedReps));
        this.restSeconds = Math.max(0, Math.min(3600, restSeconds));
        this.position = position;
    }

    public JSONObject toJson() throws JSONException {
        JSONObject json = new JSONObject();
        json.put("exerciseName", exerciseName);
        json.put("plannedSets", plannedSets);
        json.put("suggestedReps", suggestedReps);
        json.put("restSeconds", restSeconds);
        json.put("position", position);
        return json;
    }

    public static TrainingPlanItem fromJson(JSONObject json) {
        return new TrainingPlanItem(
                json.optString("exerciseName", ""),
                json.optInt("plannedSets", 1),
                json.optInt("suggestedReps", DEFAULT_SUGGESTED_REPS),
                json.optInt("restSeconds", DEFAULT_REST_SECONDS),
                json.optInt("position", 0));
    }
}
