package com.cursor.hr40;

import android.content.Context;

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
import java.util.List;

public final class WorkoutRepository {
    private static final String DIR_NAME = "workouts";

    private WorkoutRepository() {
    }

    public static File save(Context context, WorkoutSession session) throws IOException, JSONException {
        File dir = workoutsDir(context);
        if (!dir.exists() && !dir.mkdirs()) {
            throw new IOException("Unable to create workout directory");
        }
        File file = new File(dir, session.id + ".json");
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(file))) {
            writer.write(session.toJson().toString(2));
        }
        return file;
    }

    public static WorkoutSession loadLatest(Context context) throws IOException {
        List<WorkoutSession> sessions = loadAll(context);
        return sessions.isEmpty() ? null : sessions.get(0);
    }

    public static List<WorkoutSession> loadAll(Context context) throws IOException {
        File dir = workoutsDir(context);
        File[] files = dir.listFiles((file, name) -> name.endsWith(".json"));
        if (files == null || files.length == 0) {
            return new ArrayList<>();
        }
        Arrays.sort(files, Comparator.comparingLong(File::lastModified).reversed());
        List<WorkoutSession> sessions = new ArrayList<>();
        for (File file : files) {
            sessions.add(read(file));
        }
        return sessions;
    }

    private static WorkoutSession read(File file) throws IOException {
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
