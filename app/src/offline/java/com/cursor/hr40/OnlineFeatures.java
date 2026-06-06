package com.cursor.hr40;

import android.content.Context;
import android.widget.LinearLayout;

import org.json.JSONException;

import java.io.IOException;

public final class OnlineFeatures {
    private OnlineFeatures() {
    }

    public static String appTitle(String versionName) {
        return "HR40 离线运动监测 v" + versionName;
    }

    public static void attachAccountButton(MainActivity activity, LinearLayout root) {
    }

    public static void onMainReady(MainActivity activity, Runnable onReady) {
        onReady.run();
    }

    public static void onWorkoutArchived(Context context, WorkoutSession session) {
    }

    public static void addExercise(MainActivity activity, String name, Runnable onSuccess, Runnable onError) {
        try {
            ExerciseStore.addExercise(activity, name);
            onSuccess.run();
        } catch (IOException | JSONException e) {
            onError.run();
        }
    }

    public static void deleteExercise(MainActivity activity, String name, Runnable onSuccess, Runnable onError) {
        try {
            ExerciseStore.deleteExercise(activity, name);
            onSuccess.run();
        } catch (IOException | JSONException e) {
            onError.run();
        }
    }

    public static void onProfileSaved(Context context, UserProfile profile) {
    }
}
