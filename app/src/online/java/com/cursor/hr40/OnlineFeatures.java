package com.cursor.hr40;

import android.content.Context;
import android.content.Intent;
import com.google.android.material.button.MaterialButton;

public final class OnlineFeatures {
    private OnlineFeatures() {
    }

    public static String appTitle(String versionName) {
        return "HR40 在线运动监测 v" + versionName;
    }

    public static String profileButtonLabel() {
        return "用户管理";
    }

    public static void configureProfileButton(MainActivity activity, MaterialButton button) {
        button.setText(profileButtonLabel());
        OnlineUi.styleButton(button);
        button.setOnClickListener(v ->
                activity.startActivity(new Intent(activity, UserManagementActivity.class)));
    }

    public static void onMainReady(MainActivity activity, Runnable onReady) {
        OnlineSyncManager.runInitialSync(activity, new OnlineSyncManager.SyncCallback() {
            @Override
            public void onSuccess(String message) {
                activity.runOnUiThread(() -> {
                    activity.showToast(message);
                    onReady.run();
                });
            }

            @Override
            public void onError(String message) {
                activity.runOnUiThread(() -> {
                    activity.showToast("云端同步失败: " + message);
                    onReady.run();
                });
            }
        });
    }

    public static void onWorkoutArchived(Context context, WorkoutSession session) {
        if (session == null) {
            return;
        }
        OnlineSyncManager.syncWorkoutAsync(context, session, new OnlineSyncManager.SyncCallback() {
            @Override
            public void onSuccess(String message) {
                if (context instanceof MainActivity) {
                    ((MainActivity) context).runOnUiThread(() -> ((MainActivity) context).showToast(message));
                }
            }

            @Override
            public void onError(String message) {
                if (context instanceof MainActivity) {
                    ((MainActivity) context).runOnUiThread(() ->
                            ((MainActivity) context).showToast("云端同步失败: " + message));
                }
            }
        });
    }

    public static void addExercise(MainActivity activity, String name, Runnable onSuccess, Runnable onError) {
        OnlineSyncManager.addExerciseAsync(activity, name, new OnlineSyncManager.SyncCallback() {
            @Override
            public void onSuccess(String message) {
                activity.runOnUiThread(() -> {
                    activity.showToast(message);
                    onSuccess.run();
                });
            }

            @Override
            public void onError(String message) {
                activity.runOnUiThread(() -> {
                    activity.showToast(message);
                    onError.run();
                });
            }
        });
    }

    public static void deleteExercise(MainActivity activity, String name, Runnable onSuccess, Runnable onError) {
        OnlineSyncManager.deleteExerciseAsync(activity, name, new OnlineSyncManager.SyncCallback() {
            @Override
            public void onSuccess(String message) {
                activity.runOnUiThread(() -> {
                    activity.showToast(message);
                    onSuccess.run();
                });
            }

            @Override
            public void onError(String message) {
                activity.runOnUiThread(() -> {
                    activity.showToast(message);
                    onError.run();
                });
            }
        });
    }

    public static void onProfileSaved(Context context, UserProfile profile) {
        if (profile == null) {
            return;
        }
        new Thread(() -> {
            try {
                SupabaseApiClient.CloudProfile cloudProfile = new SupabaseApiClient.CloudProfile(
                        profile.name,
                        profile.sex,
                        profile.age,
                        profile.heightCm,
                        profile.weightKg,
                        true);
                new SupabaseApiClient(context).upsertProfile(cloudProfile);
            } catch (Exception ignored) {
            }
        }).start();
    }
}
