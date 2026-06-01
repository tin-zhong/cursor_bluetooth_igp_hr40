package com.cursor.hr40;

import org.json.JSONException;
import org.json.JSONObject;

public final class HeartRateSample {
    public final long timestampMillis;
    public final int bpm;
    public final boolean contactSupported;
    public final boolean contactDetected;
    public final Integer energyExpendedKj;
    public final int rrIntervalCount;

    public HeartRateSample(
            long timestampMillis,
            int bpm,
            boolean contactSupported,
            boolean contactDetected,
            Integer energyExpendedKj,
            int rrIntervalCount) {
        this.timestampMillis = timestampMillis;
        this.bpm = bpm;
        this.contactSupported = contactSupported;
        this.contactDetected = contactDetected;
        this.energyExpendedKj = energyExpendedKj;
        this.rrIntervalCount = rrIntervalCount;
    }

    public JSONObject toJson() throws JSONException {
        JSONObject json = new JSONObject();
        json.put("timestampMillis", timestampMillis);
        json.put("bpm", bpm);
        json.put("contactSupported", contactSupported);
        json.put("contactDetected", contactDetected);
        if (energyExpendedKj != null) {
            json.put("energyExpendedKj", energyExpendedKj);
        }
        json.put("rrIntervalCount", rrIntervalCount);
        return json;
    }

    public static HeartRateSample fromJson(JSONObject json) {
        Integer energy = json.has("energyExpendedKj") ? json.optInt("energyExpendedKj") : null;
        return new HeartRateSample(
                json.optLong("timestampMillis"),
                json.optInt("bpm"),
                json.optBoolean("contactSupported"),
                json.optBoolean("contactDetected"),
                energy,
                json.optInt("rrIntervalCount"));
    }
}
