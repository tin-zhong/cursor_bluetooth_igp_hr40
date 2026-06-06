package com.cursor.hr40;

import android.content.Context;
import android.content.SharedPreferences;

public final class SupabaseSessionStore {
    private static final String PREFS = "supabase_session";
    private static final String KEY_ACCESS_TOKEN = "access_token";
    private static final String KEY_REFRESH_TOKEN = "refresh_token";
    private static final String KEY_USER_ID = "user_id";
    private static final String KEY_EMAIL = "email";

    private SupabaseSessionStore() {
    }

    public static boolean hasSession(Context context) {
        return !getAccessToken(context).isEmpty();
    }

    public static String getAccessToken(Context context) {
        return prefs(context).getString(KEY_ACCESS_TOKEN, "");
    }

    public static String getRefreshToken(Context context) {
        return prefs(context).getString(KEY_REFRESH_TOKEN, "");
    }

    public static String getUserId(Context context) {
        return prefs(context).getString(KEY_USER_ID, "");
    }

    public static String getEmail(Context context) {
        return prefs(context).getString(KEY_EMAIL, "");
    }

    public static void save(
            Context context, String accessToken, String refreshToken, String userId, String email) {
        prefs(context)
                .edit()
                .putString(KEY_ACCESS_TOKEN, accessToken)
                .putString(KEY_REFRESH_TOKEN, refreshToken)
                .putString(KEY_USER_ID, userId)
                .putString(KEY_EMAIL, email)
                .apply();
    }

    public static void clear(Context context) {
        prefs(context).edit().clear().apply();
    }

    private static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }
}
