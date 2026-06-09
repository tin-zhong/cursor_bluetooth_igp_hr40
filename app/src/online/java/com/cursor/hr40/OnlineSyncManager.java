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

    public interface UploadCallback {
        /**
         * @param allUploaded   true when every local workout now exists in the cloud
         * @param uploadedCount number of workouts uploaded during this run
         * @param cloudCount    total workouts stored in the cloud after this run
         */
        void onComplete(boolean allUploaded, int uploadedCount, int cloudCount);

        void onError(String message);
    }

    private OnlineSyncManager() {
    }

    /** True when at least one local workout has not been marked as synced to the cloud. */
    public static boolean hasPendingWorkouts(Context context) {
        for (WorkoutSession session : WorkoutRepository.loadAll(context)) {
            if (!SyncStateStore.isWorkoutSynced(context, session.id)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Inspect the cloud, then (re)upload every local workout that is missing there.
     * Workouts already present in the cloud are only marked synced locally.
     */
    public static void uploadMissingWorkoutsAsync(Context context, UploadCallback callback) {
        new Thread(() -> {
            try {
                SupabaseApiClient client = new SupabaseApiClient(context);
                java.util.Set<String> cloudIds = client.fetchWorkoutLocalIds();
                int uploaded = 0;
                for (WorkoutSession session : WorkoutRepository.loadAll(context)) {
                    if (cloudIds.contains(session.id)) {
                        SyncStateStore.markWorkoutSynced(context, session.id);
                    } else {
                        client.syncWorkout(session);
                        uploaded++;
                    }
                }
                int cloudCount = cloudIds.size() + uploaded;
                if (callback != null) {
                    callback.onComplete(true, uploaded, cloudCount);
                }
            } catch (Exception e) {
                if (callback != null) {
                    callback.onError(e.getMessage() == null ? "上传失败" : e.getMessage());
                }
            }
        }).start();
    }

    /** True when this specific local workout has already been synced to the cloud. */
    public static boolean isWorkoutSynced(Context context, String sessionId) {
        return SyncStateStore.isWorkoutSynced(context, sessionId);
    }

    /**
     * Upload a single workout if the cloud does not already have it. Workouts already present
     * in the cloud are only marked synced locally.
     */
    public static void uploadWorkoutAsync(Context context, WorkoutSession session, UploadCallback callback) {
        new Thread(() -> {
            try {
                if (session == null) {
                    throw new SupabaseApiClient.ApiException("未找到运动记录");
                }
                SupabaseApiClient client = new SupabaseApiClient(context);
                java.util.Set<String> cloudIds = client.fetchWorkoutLocalIds();
                int uploaded = 0;
                if (cloudIds.contains(session.id)) {
                    SyncStateStore.markWorkoutSynced(context, session.id);
                } else {
                    client.syncWorkout(session);
                    uploaded = 1;
                }
                if (callback != null) {
                    callback.onComplete(true, uploaded, cloudIds.size() + uploaded);
                }
            } catch (Exception e) {
                if (callback != null) {
                    callback.onError(e.getMessage() == null ? "上传失败" : e.getMessage());
                }
            }
        }).start();
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
        for (SupabaseApiClient.CloudWorkout cloudWorkout : client.fetchWorkouts()) {
            WorkoutSession session = cloudWorkout.toSession();
            WorkoutRepository.saveToDatabase(context, session);
            SyncStateStore.markWorkoutSynced(context, session.id);
        }
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
