package com.cursor.hr40;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONException;
import org.json.JSONObject;

public final class ProfileStore {
    private static final String PREFS_NAME = "profile_store";
    private static final String KEY_PROFILE_JSON = "profile_json";

    private ProfileStore() {
    }

    public static UserProfile load(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        String raw = prefs.getString(KEY_PROFILE_JSON, null);
        if (raw == null || raw.isEmpty()) {
            return null;
        }
        try {
            return UserProfile.fromJson(new JSONObject(raw));
        } catch (JSONException ignored) {
            return null;
        }
    }

    public static void save(Context context, UserProfile profile) throws JSONException {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putString(KEY_PROFILE_JSON, profile.toJson().toString())
                .apply();
    }
}
