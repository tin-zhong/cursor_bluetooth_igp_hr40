package com.cursor.hr40;

import android.content.Context;

import org.json.JSONException;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class OnlineSyncManager {
    public interface SyncCallback {
        void onSuccess(String message);

        void onError(String message);
    }

    private OnlineSyncManager() {
    }

    public static void pullExercisesToLocal(Context context) throws IOException, JSONException, SupabaseApiClient.ApiException {
        SupabaseApiClient client = new SupabaseApiClient(context);
        List<SupabaseApiClient.CloudExercise> cloudExercises = client.fetchExercises();
        List<String> names = new ArrayList<>();
        for (SupabaseApiClient.CloudExercise exercise : cloudExercises) {
            String trimmed = exercise.name == null ? "" : exercise.name.trim();
            if (!trimmed.isEmpty() && !names.contains(trimmed)) {
                names.add(trimmed);
            }
        }
        Collections.sort(names, String.CASE_INSENSITIVE_ORDER);
        ExerciseStore.saveExercises(context, names);
    }

    public static void syncWorkout(Context context, WorkoutSession session)
            throws IOException, JSONException, SupabaseApiClient.ApiException {
        if (session == null || SyncStateStore.isWorkoutSynced(context, session.id)) {
            return;
        }
        new SupabaseApiClient(context).syncWorkout(session);
    }

    public static void syncPendingWorkouts(Context context) throws IOException, JSONException, SupabaseApiClient.ApiException {
        for (WorkoutSession session : WorkoutRepository.loadAll(context)) {
            if (!SyncStateStore.isWorkoutSynced(context, session.id)) {
                syncWorkout(context, session);
            }
        }
    }

    public static void runInitialSync(Context context, SyncCallback callback) {
        new Thread(() -> {
            try {
                pullExercisesToLocal(context);
                syncPendingWorkouts(context);
                if (callback != null) {
                    callback.onSuccess("云端数据已同步");
                }
            } catch (Exception e) {
                if (callback != null) {
                    callback.onError(e.getMessage() == null ? "同步失败" : e.getMessage());
                }
            }
        }).start();
    }

    public static void syncWorkoutAsync(Context context, WorkoutSession session, SyncCallback callback) {
        new Thread(() -> {
            try {
                syncWorkout(context, session);
                if (callback != null) {
                    callback.onSuccess("训练记录已同步到云端");
                }
            } catch (Exception e) {
                if (callback != null) {
                    callback.onError(e.getMessage() == null ? "同步失败" : e.getMessage());
                }
            }
        }).start();
    }

    public static void addExerciseAsync(Context context, String name, SyncCallback callback) {
        new Thread(() -> {
            try {
                String trimmed = name == null ? "" : name.trim();
                if (trimmed.isEmpty()) {
                    throw new SupabaseApiClient.ApiException("请输入动作名称");
                }
                new SupabaseApiClient(context).addExercise(trimmed);
                ExerciseStore.addExercise(context, trimmed);
                if (callback != null) {
                    callback.onSuccess("动作已添加");
                }
            } catch (Exception e) {
                if (callback != null) {
                    callback.onError(e.getMessage() == null ? "添加失败" : e.getMessage());
                }
            }
        }).start();
    }

    public static void deleteExerciseAsync(Context context, String name, SyncCallback callback) {
        new Thread(() -> {
            try {
                new SupabaseApiClient(context).deleteExerciseByName(name);
                ExerciseStore.deleteExercise(context, name);
                if (callback != null) {
                    callback.onSuccess("动作已删除");
                }
            } catch (Exception e) {
                if (callback != null) {
                    callback.onError(e.getMessage() == null ? "删除失败" : e.getMessage());
                }
            }
        }).start();
    }
}
