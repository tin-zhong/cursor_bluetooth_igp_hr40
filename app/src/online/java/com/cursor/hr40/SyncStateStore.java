package com.cursor.hr40;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.HashSet;
import java.util.Set;

public final class SyncStateStore {
    private static final String PREFS = "online_sync_state";
    private static final String KEY_SYNCED_WORKOUTS = "synced_workout_ids";

    private SyncStateStore() {
    }

    public static boolean isWorkoutSynced(Context context, String localSessionId) {
        return prefs(context).getStringSet(KEY_SYNCED_WORKOUTS, new HashSet<>()).contains(localSessionId);
    }

    public static void markWorkoutSynced(Context context, String localSessionId) {
        SharedPreferences preferences = prefs(context);
        Set<String> synced = new HashSet<>(preferences.getStringSet(KEY_SYNCED_WORKOUTS, new HashSet<>()));
        synced.add(localSessionId);
        preferences.edit().putStringSet(KEY_SYNCED_WORKOUTS, synced).apply();
    }

    public static void clear(Context context) {
        prefs(context).edit().clear().apply();
    }

    private static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }
}
