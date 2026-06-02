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
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Room-backed workout storage. Legacy JSON files are imported once, then removed.
 */
public final class WorkoutRepository {
    private static final String DIR_NAME = "workouts";
    private static final String PREFS_META = "workout_storage";
    private static final String KEY_JSON_MIGRATED = "json_migrated_v1";

    private WorkoutRepository() {
    }

    public static void save(Context context, WorkoutSession session) {
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

    public static WorkoutSession loadLatest(Context context) {
        List<WorkoutSession> sessions = loadAll(context);
        return sessions.isEmpty() ? null : sessions.get(0);
    }

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

    public static List<WorkoutWithDetails> loadAllWithDetails(Context context) {
        migrateLegacyJsonIfNeeded(context);
        return WorkoutDatabase.getInstance(context).workoutDao().loadAllWorkoutsWithDetails();
    }

    public static void migrateLegacyJsonIfNeeded(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_META, Context.MODE_PRIVATE);
        if (prefs.getBoolean(KEY_JSON_MIGRATED, false)) {
            return;
        }

        File dir = legacyWorkoutsDir(context);
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
                    WorkoutSession session = readLegacyJson(file);
                    if (session != null && !existingIds.contains(session.id)) {
                        save(context, session);
                        existingIds.add(session.id);
                    }
                } catch (IOException ignored) {
                    // Skip invalid legacy files.
                }
            }
            for (File file : files) {
                file.delete();
            }
        }

        prefs.edit().putBoolean(KEY_JSON_MIGRATED, true).apply();
    }

    private static WorkoutSession readLegacyJson(File file) throws IOException {
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
            throw new IOException("Invalid legacy workout file", e);
        }
    }

    private static File legacyWorkoutsDir(Context context) {
        return new File(context.getFilesDir(), DIR_NAME);
    }
}
