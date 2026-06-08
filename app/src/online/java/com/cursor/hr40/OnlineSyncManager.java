package com.cursor.hr40;

import android.content.Context;

import org.json.JSONException;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

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

    public static void pullProfileToLocal(Context context) throws IOException, JSONException, SupabaseApiClient.ApiException {
        SupabaseApiClient.CloudProfile profile = new SupabaseApiClient(context).fetchProfile();
        if (profile != null && profile.profileCompleted) {
            ProfileStore.save(context, profile.toLocalProfile());
        }
    }

    public static void pullWorkoutsToLocal(Context context) throws IOException, JSONException, SupabaseApiClient.ApiException {
        SupabaseApiClient client = new SupabaseApiClient(context);
        Set<String> cloudIds = new HashSet<>();
        for (SupabaseApiClient.CloudWorkout cloudWorkout : client.fetchWorkouts()) {
            WorkoutSession session = cloudWorkout.toSession();
            WorkoutRepository.saveToDatabase(context, session);
            SyncStateStore.markWorkoutSynced(context, session.id);
            cloudIds.add(session.id);
        }
        // 对账：本地标记已同步但云端不存在 → 视为云端被删除，本地一并清除
        List<String> toDelete = new ArrayList<>();
        for (String syncedId : SyncStateStore.syncedWorkoutIds(context)) {
            if (!cloudIds.contains(syncedId)) {
                toDelete.add(syncedId);
            }
        }
        if (!toDelete.isEmpty()) {
            WorkoutRepository.deleteWorkoutsByIds(context, toDelete);
            for (String id : toDelete) {
                SyncStateStore.unmarkWorkoutSynced(context, id);
            }
        }
    }

    public static void deleteWorkout(Context context, String sessionId)
            throws IOException, SupabaseApiClient.ApiException {
        if (sessionId == null || sessionId.isEmpty()) {
            return;
        }
        new SupabaseApiClient(context).deleteWorkoutByLocalId(sessionId);
        WorkoutRepository.deleteWorkoutsByIds(context, Collections.singletonList(sessionId));
        SyncStateStore.unmarkWorkoutSynced(context, sessionId);
    }

    public static void deleteWorkoutAsync(Context context, String sessionId, SyncCallback callback) {
        new Thread(() -> {
            try {
                deleteWorkout(context, sessionId);
                if (callback != null) {
                    callback.onSuccess("运动记录已删除");
                }
            } catch (Exception e) {
                if (callback != null) {
                    callback.onError(e.getMessage() == null ? "删除失败" : e.getMessage());
                }
            }
        }).start();
    }

    public static void pullTrainingPlanToLocal(Context context) throws IOException, JSONException, SupabaseApiClient.ApiException {
        List<TrainingPlanItem> items = new SupabaseApiClient(context).fetchTrainingPlan();
        TrainingPlanStore.save(context, items);
    }

    public static void refreshFromCloud(Context context) throws IOException, JSONException, SupabaseApiClient.ApiException {
        pullProfileToLocal(context);
        pullExercisesToLocal(context);
        pullTrainingPlanToLocal(context);
        pullWorkoutsToLocal(context);
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
                refreshFromCloud(context);
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

    public static void refreshFromCloudAsync(Context context, SyncCallback callback) {
        new Thread(() -> {
            try {
                refreshFromCloud(context);
                if (callback != null) {
                    callback.onSuccess(null);
                }
            } catch (Exception e) {
                if (callback != null) {
                    callback.onError(e.getMessage() == null ? "同步失败" : e.getMessage());
                }
            }
        }).start();
    }

    public static void pullExercisesAsync(Context context, SyncCallback callback) {
        new Thread(() -> {
            try {
                pullExercisesToLocal(context);
                if (callback != null) {
                    callback.onSuccess(null);
                }
            } catch (Exception e) {
                if (callback != null) {
                    callback.onError(e.getMessage() == null ? "同步失败" : e.getMessage());
                }
            }
        }).start();
    }

    public static void pullWorkoutsAsync(Context context, SyncCallback callback) {
        new Thread(() -> {
            try {
                pullWorkoutsToLocal(context);
                if (callback != null) {
                    callback.onSuccess(null);
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
