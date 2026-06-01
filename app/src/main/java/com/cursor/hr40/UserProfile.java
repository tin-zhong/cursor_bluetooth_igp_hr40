package com.cursor.hr40;

import org.json.JSONException;
import org.json.JSONObject;

public final class UserProfile {
    public static final String SEX_MALE = "male";
    public static final String SEX_FEMALE = "female";

    public final String name;
    public final int heightCm;
    public final double weightKg;
    public final int age;
    public final String sex;
    public final long createdAtMillis;

    public UserProfile(String name, int heightCm, double weightKg, int age, String sex, long createdAtMillis) {
        this.name = name;
        this.heightCm = heightCm;
        this.weightKg = weightKg;
        this.age = age;
        this.sex = sex;
        this.createdAtMillis = createdAtMillis;
    }

    public int estimatedMaxHeartRate() {
        return Math.max(120, 220 - age);
    }

    public JSONObject toJson() throws JSONException {
        JSONObject json = new JSONObject();
        json.put("name", name);
        json.put("heightCm", heightCm);
        json.put("weightKg", weightKg);
        json.put("age", age);
        json.put("sex", sex);
        json.put("createdAtMillis", createdAtMillis);
        return json;
    }

    public static UserProfile fromJson(JSONObject json) {
        return new UserProfile(
                json.optString("name", ""),
                json.optInt("heightCm", 170),
                json.optDouble("weightKg", 70.0),
                json.optInt("age", 30),
                json.optString("sex", SEX_MALE),
                json.optLong("createdAtMillis", System.currentTimeMillis()));
    }
}
