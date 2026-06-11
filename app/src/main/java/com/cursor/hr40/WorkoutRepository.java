package com.cursor.hr40;

import android.content.Context;
import android.content.SharedPreferences;

import com.cursor.hr40.db.HeartRateSampleEntity;
import com.cursor.hr40.db.StrengthSetEntity;
import com.cursor.hr40.db.WorkoutDao;
import com.cursor.hr40.db.WorkoutDatabase;
import com.cursor.hr40.db.WorkoutRecordEntity;
import com.cursor.hr40.db.WorkoutRecordMapper;
import com.cursor.hr40.db.WorkoutWithDetails;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Workouts are buffered as JSON while in progress. When a session finishes,
 * the JSON snapshot is committed into Room and the JSON file is removed.
 */
public final class WorkoutRepository {
    private static final String DIR_NAME = "workouts";
    private static final String PREFS_META = "workout_storage";
    private static final String KEY_JSON_MIGRATED = "json_migrated_v1";

    private WorkoutRepository() {
    }

    /** Persist in-progress workout to a local JSON file. */
    public static void saveJson(Context context, WorkoutSession session) throws IOException, JSONException {
        saveJsonString(context, session.id, session.toJson().toString(2));
    }

    /**
     * Write a pre-serialized session JSON to disk. Callers that run off the main thread should
     * serialize the session on the owning thread first and pass the resulting string here, so the
     * background write never races with concurrent mutations of the session.
     */
    public static void saveJsonString(Context context, String sessionId, String json) throws IOException {
        File dir = workoutsDir(context);
        if (!dir.exists() && !dir.mkdirs()) {
            throw new IOException("Unable to create workout directory");
        }
        File file = new File(dir, sessionId + ".json");
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(file))) {
            writer.write(json);
        }
    }

    /** Write a completed workout into Room (used after finish). */
    public static void saveToDatabase(Context context, WorkoutSession session) {
        WorkoutDao dao = WorkoutDatabase.getInstance(context).workoutDao();

        WorkoutRecordEntity workout = new WorkoutRecordEntity();
        workout.id = session.id;
        workout.startMillis = session.startMillis;
        workout.endMillis = session.endMillis;
        workout.workoutType = session.workoutType;
        dao.upsertWorkout(workout);

        dao.deleteHeartRateSamplesBySession(session.id);
        List<HeartRateSampleEntity> sampleEntities =
                WorkoutRecordMapper.toSampleEntities(session.id, session.samples());
        if (!sampleEntities.isEmpty()) {
            dao.insertHeartRateSamples(sampleEntities);
        }

        dao.deleteStrengthSetsBySession(session.id);
        List<StrengthSetEntity> setEntities =
                WorkoutRecordMapper.toStrengthSetEntities(session.id, session.strengthSets());
        if (!setEntities.isEmpty()) {
            dao.insertStrengthSets(setEntities);
        }
    }

    /**
     * Finalize workout: write JSON, import into Room, then remove the JSON file.
     */
    public static void archiveFinishedSession(Context context, WorkoutSession session)
            throws IOException, JSONException {
        saveJson(context, session);
        saveToDatabase(context, session);
        deleteJson(context, session.id);
    }

    public static void deleteJson(Context context, String sessionId) {
        File file = new File(workoutsDir(context), sessionId + ".json");
        if (file.exists()) {
            file.delete();
        }
    }

    /**
     * Remove any leftover in-progress JSON buffers (e.g. from a previous session that
     * was never archived because the app was killed). Completed workouts already live in
     * Room, so dropping these stale buffers prevents old samples from leaking into a new
     * session's cached data.
     */
    public static void clearInProgressBuffers(Context context) {
        File dir = workoutsDir(context);
        File[] files = dir.listFiles((file, name) -> name.endsWith(".json"));
        if (files == null) {
            return;
        }
        for (File file : files) {
            file.delete();
        }
    }

    public static WorkoutSession loadLatest(Context context) {
        List<WorkoutSession> sessions = loadAll(context);
        return sessions.isEmpty() ? null : sessions.get(0);
    }

    /** Completed workouts stored in Room. */
    public static List<WorkoutSession> loadAll(Context context) {
        migrateLegacyJsonIfNeeded(context);
        List<WorkoutSession> sessions = new ArrayList<>();
        for (WorkoutWithDetails details : loadAllWithDetails(context)) {
            WorkoutSession session = WorkoutRecordMapper.toSession(details);
            if (session != null) {
                sessions.add(session);
            }
        }
        return sessions;
    }

    public static int deleteWorkoutsOlderThan(Context context, long cutoffMillis) {
        migrateLegacyJsonIfNeeded(context);
        return WorkoutDatabase.getInstance(context).workoutDao().deleteWorkoutsOlderThan(cutoffMillis);
    }

    public static int deleteWorkoutsByIds(Context context, List<String> sessionIds) {
        migrateLegacyJsonIfNeeded(context);
        if (sessionIds == null || sessionIds.isEmpty()) {
            return 0;
        }
        return WorkoutDatabase.getInstance(context).workoutDao().deleteWorkoutsByIds(sessionIds);
    }

    public static List<WorkoutWithDetails> loadAllWithDetails(Context context) {
        migrateLegacyJsonIfNeeded(context);
        return WorkoutDatabase.getInstance(context).workoutDao().loadAllWorkoutsWithDetails();
    }

    /** One-time import for JSON files left from older app versions. */
    public static void migrateLegacyJsonIfNeeded(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_META, Context.MODE_PRIVATE);
        if (prefs.getBoolean(KEY_JSON_MIGRATED, false)) {
            return;
        }

        File dir = workoutsDir(context);
        File[] files = dir.listFiles((file, name) -> name.endsWith(".json"));
        if (files != null && files.length > 0) {
            Arrays.sort(files, Comparator.comparingLong(File::lastModified));
            Set<String> existingIds = new HashSet<>();
            for (WorkoutWithDetails details : WorkoutDatabase.getInstance(context).workoutDao().loadAllWorkoutsWithDetails()) {
                if (details.workout != null) {
                    existingIds.add(details.workout.id);
                }
            }
            for (File file : files) {
                try {
                    WorkoutSession session = readJson(file);
                    if (session != null && session.endMillis > 0L && !existingIds.contains(session.id)) {
                        saveToDatabase(context, session);
                        existingIds.add(session.id);
                    }
                } catch (IOException ignored) {
                    // Skip invalid legacy files.
                }
                file.delete();
            }
        }

        prefs.edit().putBoolean(KEY_JSON_MIGRATED, true).apply();
    }

    private static WorkoutSession readJson(File file) throws IOException {
        StringBuilder builder = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            String line;
            while ((line = reader.readLine()) != null) {
                builder.append(line);
            }
        }
        try {
            return WorkoutSession.fromJson(new JSONObject(builder.toString()));
        } catch (JSONException e) {
            throw new IOException("Invalid workout file", e);
        }
    }

    private static File workoutsDir(Context context) {
        return new File(context.getFilesDir(), DIR_NAME);
    }
}
