package com.cursor.hr40;

import org.json.JSONException;
import org.json.JSONObject;

public final class TrainingPlanItem {
    public final String exerciseName;
    public final int plannedSets;
    public final int position;

    public TrainingPlanItem(String exerciseName, int plannedSets, int position) {
        this.exerciseName = exerciseName == null ? "" : exerciseName.trim();
        this.plannedSets = Math.max(0, plannedSets);
        this.position = position;
    }

    public JSONObject toJson() throws JSONException {
        JSONObject json = new JSONObject();
        json.put("exerciseName", exerciseName);
        json.put("plannedSets", plannedSets);
        json.put("position", position);
        return json;
    }

    public static TrainingPlanItem fromJson(JSONObject json) {
        return new TrainingPlanItem(
                json.optString("exerciseName", ""),
                json.optInt("plannedSets", 1),
                json.optInt("position", 0));
    }
}
