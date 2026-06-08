package com.cursor.hr40;

import android.content.Context;

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
import java.util.Comparator;
import java.util.List;

public final class TrainingPlanStore {
    private static final String FILE_NAME = "training_plan.json";

    private TrainingPlanStore() {
    }

    public static List<TrainingPlanItem> load(Context context) {
        File file = new File(context.getFilesDir(), FILE_NAME);
        if (!file.exists()) {
            return new ArrayList<>();
        }
        StringBuilder builder = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            String line;
            while ((line = reader.readLine()) != null) {
                builder.append(line);
            }
        } catch (IOException e) {
            return new ArrayList<>();
        }
        if (builder.length() == 0) {
            return new ArrayList<>();
        }
        try {
            JSONArray array = new JSONObject(builder.toString()).optJSONArray("items");
            if (array == null) {
                return new ArrayList<>();
            }
            List<TrainingPlanItem> items = new ArrayList<>();
            for (int i = 0; i < array.length(); i++) {
                items.add(TrainingPlanItem.fromJson(array.getJSONObject(i)));
            }
            Collections.sort(items, Comparator.comparingInt(it -> it.position));
            return items;
        } catch (JSONException e) {
            return new ArrayList<>();
        }
    }

    public static void save(Context context, List<TrainingPlanItem> items) throws IOException, JSONException {
        File file = new File(context.getFilesDir(), FILE_NAME);
        JSONArray array = new JSONArray();
        for (TrainingPlanItem item : items) {
            array.put(item.toJson());
        }
        JSONObject json = new JSONObject();
        json.put("items", array);
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(file))) {
            writer.write(json.toString(2));
        }
    }
}
