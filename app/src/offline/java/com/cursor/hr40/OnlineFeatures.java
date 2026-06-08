package com.cursor.hr40;

import android.app.Activity;
import android.content.Context;
import android.view.View;
import android.widget.TextView;

import com.google.android.material.button.MaterialButton;

import org.json.JSONException;

import java.io.IOException;

public final class OnlineFeatures {
    private OnlineFeatures() {
    }

    public static String appTitle(String versionName) {
        return "运动检测 v" + versionName;
    }

    public static boolean showFileManagement() {
        return true;
    }

    public static boolean showHistoryManagement() {
        return true;
    }

    public static boolean runMaintenanceCleanup() {
        return true;
    }

    public static boolean workoutDetailShowsDateFilter() {
        return true;
    }

    public static String headerUserName(Context context) {
        UserProfile profile = ProfileStore.load(context);
        if (profile == null || profile.name == null || profile.name.trim().isEmpty()) {
            return null;
        }
        return profile.name.trim();
    }

    public static void styleHeaderUserName(TextView view) {
        view.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 15f);
        view.setGravity(android.view.Gravity.CENTER_HORIZONTAL);
    }

    public static String profileButtonLabel() {
        return "编辑运动人员资料";
    }

    public static void configureProfileButton(MainActivity activity, MaterialButton button) {
        button.setText(profileButtonLabel());
        button.setAllCaps(false);
        button.setOnClickListener(v -> activity.openProfileEditor());
    }

    public static void onMainResume(MainActivity activity, Runnable onRefreshed) {
        onRefreshed.run();
    }

    public static void refreshExerciseList(Activity activity, Runnable onDone) {
        onDone.run();
    }

    public static void refreshWorkoutList(Activity activity, Runnable onDone) {
        onDone.run();
    }

    public static void configureLogoutButton(MainActivity activity, MaterialButton button) {
        button.setVisibility(View.GONE);
    }

    public static void onMainReady(MainActivity activity, Runnable onReady) {
        onReady.run();
    }

    public static void onWorkoutArchived(Context context, WorkoutSession session) {
    }

    public static void addExercise(Activity activity, String name, Runnable onSuccess, Runnable onError) {
        try {
            ExerciseStore.addExercise(activity, name);
            onSuccess.run();
        } catch (IOException | JSONException e) {
            onError.run();
        }
    }

    public static void deleteExercise(Activity activity, String name, Runnable onSuccess, Runnable onError) {
        try {
            ExerciseStore.deleteExercise(activity, name);
            onSuccess.run();
        } catch (IOException | JSONException e) {
            onError.run();
        }
    }

    public static void onProfileSaved(Context context, UserProfile profile) {
    }

    public static void deleteWorkout(Activity activity, String sessionId, Runnable onSuccess, Runnable onError) {
        try {
            WorkoutRepository.deleteWorkoutsByIds(activity, java.util.Collections.singletonList(sessionId));
            android.widget.Toast.makeText(activity, "运动记录已删除", android.widget.Toast.LENGTH_SHORT).show();
            onSuccess.run();
        } catch (Exception e) {
            android.widget.Toast.makeText(activity, "删除失败: " + e.getMessage(), android.widget.Toast.LENGTH_LONG).show();
            onError.run();
        }
    }
}
