package com.cursor.hr40;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.widget.TextView;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

public final class OnlineFeatures {
    private OnlineFeatures() {
    }

    public static String appTitle(String versionName) {
        return "运动检测 v" + versionName;
    }

    public static String headerUserName(Context context) {
        UserProfile profile = ProfileStore.load(context);
        if (profile == null || profile.name == null || profile.name.trim().isEmpty()) {
            return null;
        }
        return profile.name.trim();
    }

    public static void styleHeaderUserName(TextView view) {
        OnlineUi.styleBody(view);
        view.setGravity(android.view.Gravity.CENTER_HORIZONTAL);
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

    public static void onMainResume(MainActivity activity, Runnable onRefreshed) {
        OnlineSyncManager.refreshFromCloudAsync(activity, new OnlineSyncManager.SyncCallback() {
            @Override
            public void onSuccess(String message) {
                activity.runOnUiThread(onRefreshed);
            }

            @Override
            public void onError(String message) {
                activity.runOnUiThread(onRefreshed);
            }
        });
    }

    public static void refreshWorkoutList(Activity activity, Runnable onDone) {
        OnlineSyncManager.pullWorkoutsAsync(activity, new OnlineSyncManager.SyncCallback() {
            @Override
            public void onSuccess(String message) {
                activity.runOnUiThread(onDone);
            }

            @Override
            public void onError(String message) {
                activity.runOnUiThread(onDone);
            }
        });
    }

    public static void refreshExerciseList(Activity activity, Runnable onDone) {
        OnlineSyncManager.pullExercisesAsync(activity, new OnlineSyncManager.SyncCallback() {
            @Override
            public void onSuccess(String message) {
                activity.runOnUiThread(onDone);
            }

            @Override
            public void onError(String message) {
                activity.runOnUiThread(onDone);
            }
        });
    }

    public static void configureLogoutButton(MainActivity activity, MaterialButton button) {
        button.setText("退出登录");
        OnlineUi.styleButton(button);
        button.setOnClickListener(v -> new MaterialAlertDialogBuilder(activity)
                .setTitle("退出登录")
                .setMessage("确定要退出当前账户吗？")
                .setPositiveButton("退出", (dialog, which) -> logout(activity))
                .setNegativeButton("取消", null)
                .show());
    }

    private static void logout(MainActivity activity) {
        SupabaseSessionStore.clear(activity);
        Intent intent = new Intent(activity, AuthActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        activity.startActivity(intent);
        activity.finish();
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

    public static void addExercise(Activity activity, String name, Runnable onSuccess, Runnable onError) {
        OnlineSyncManager.addExerciseAsync(activity, name, new OnlineSyncManager.SyncCallback() {
            @Override
            public void onSuccess(String message) {
                activity.runOnUiThread(() -> {
                    toast(activity, message);
                    onSuccess.run();
                });
            }

            @Override
            public void onError(String message) {
                activity.runOnUiThread(() -> {
                    toast(activity, message);
                    onError.run();
                });
            }
        });
    }

    public static void deleteExercise(Activity activity, String name, Runnable onSuccess, Runnable onError) {
        OnlineSyncManager.deleteExerciseAsync(activity, name, new OnlineSyncManager.SyncCallback() {
            @Override
            public void onSuccess(String message) {
                activity.runOnUiThread(() -> {
                    toast(activity, message);
                    onSuccess.run();
                });
            }

            @Override
            public void onError(String message) {
                activity.runOnUiThread(() -> {
                    toast(activity, message);
                    onError.run();
                });
            }
        });
    }

    private static void toast(Activity activity, String message) {
        if (activity instanceof MainActivity) {
            ((MainActivity) activity).showToast(message);
        } else {
            android.widget.Toast.makeText(activity, message, android.widget.Toast.LENGTH_LONG).show();
        }
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
