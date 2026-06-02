package com.cursor.hr40;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class ExerciseStore {
    private static final String FILE_NAME = "exercises.json";
    private static final String PREFS_NAME = "hr40_exercise_prefs";
    private static final String KEY_WEIGHT_UNIT = "weight_unit";

    private ExerciseStore() {
    }

    public static List<String> loadExercises(Context context) throws IOException {
        File file = exerciseFile(context);
        if (!file.exists()) {
            return new ArrayList<>();
        }
        StringBuilder builder = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            String line;
            while ((line = reader.readLine()) != null) {
                builder.append(line);
            }
        }
        if (builder.length() == 0) {
            return new ArrayList<>();
        }
        try {
            JSONObject json = new JSONObject(builder.toString());
            JSONArray array = json.optJSONArray("exercises");
            if (array == null) {
                return new ArrayList<>();
            }
            List<String> exercises = new ArrayList<>();
            for (int i = 0; i < array.length(); i++) {
                String name = array.optString(i, "").trim();
                if (!name.isEmpty() && !exercises.contains(name)) {
                    exercises.add(name);
                }
            }
            return exercises;
        } catch (JSONException e) {
            throw new IOException("Invalid exercise file", e);
        }
    }

    public static void saveExercises(Context context, List<String> exercises) throws IOException, JSONException {
        File file = exerciseFile(context);
        File parent = file.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs()) {
            throw new IOException("Unable to create exercise directory");
        }
        List<String> unique = new ArrayList<>();
        for (String exercise : exercises) {
            String trimmed = exercise == null ? "" : exercise.trim();
            if (!trimmed.isEmpty() && !unique.contains(trimmed)) {
                unique.add(trimmed);
            }
        }
        Collections.sort(unique, String.CASE_INSENSITIVE_ORDER);
        JSONObject json = new JSONObject();
        JSONArray array = new JSONArray();
        for (String exercise : unique) {
            array.put(exercise);
        }
        json.put("exercises", array);
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(file))) {
            writer.write(json.toString(2));
        }
    }

    public static void addExercise(Context context, String exerciseName) throws IOException, JSONException {
        List<String> exercises = loadExercises(context);
        String trimmed = exerciseName == null ? "" : exerciseName.trim();
        if (trimmed.isEmpty()) {
            return;
        }
        if (!exercises.contains(trimmed)) {
            exercises.add(trimmed);
            saveExercises(context, exercises);
        }
    }

    public static String loadWeightUnit(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        return prefs.getString(KEY_WEIGHT_UNIT, StrengthSet.UNIT_KG);
    }

    public static void saveWeightUnit(Context context, String unit) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putString(KEY_WEIGHT_UNIT, StrengthSet.UNIT_LB.equals(unit) ? StrengthSet.UNIT_LB : StrengthSet.UNIT_KG)
                .apply();
    }

    private static File exerciseFile(Context context) {
        return new File(context.getFilesDir(), FILE_NAME);
    }
}
