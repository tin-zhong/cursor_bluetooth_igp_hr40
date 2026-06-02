package com.cursor.hr40;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.MediaStore;

import com.cursor.hr40.db.HeartRateSampleEntity;
import com.cursor.hr40.db.StrengthSetEntity;
import com.cursor.hr40.db.WorkoutWithDetails;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public final class RawDataExporter {
    private RawDataExporter() {
    }

    public static Uri export(Context context, List<WorkoutWithDetails> workouts) throws IOException {
        String fileName = "hr40_raw_workouts_" + new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US)
                .format(new Date()) + ".json";
        String json = toJson(workouts);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            return writeToDownloads(context, fileName, json);
        }
        File file = new File(context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS), fileName);
        try (OutputStream output = new FileOutputStream(file)) {
            output.write(json.getBytes());
        }
        return Uri.fromFile(file);
    }

    private static String toJson(List<WorkoutWithDetails> workouts) throws IOException {
        JSONArray root = new JSONArray();
        try {
            for (WorkoutWithDetails details : workouts) {
                if (details == null || details.workout == null) {
                    continue;
                }
                JSONObject item = new JSONObject();
                item.put("id", details.workout.id);
                item.put("startMillis", details.workout.startMillis);
                item.put("endMillis", details.workout.endMillis);
                item.put("workoutType", details.workout.workoutType);

                JSONArray samples = new JSONArray();
                for (HeartRateSampleEntity sample : details.samples) {
                    JSONObject sampleJson = new JSONObject();
                    sampleJson.put("timestampMillis", sample.timestampMillis);
                    sampleJson.put("bpm", sample.bpm);
                    sampleJson.put("contactSupported", sample.contactSupported);
                    sampleJson.put("contactDetected", sample.contactDetected);
                    if (sample.energyExpendedKj != null) {
                        sampleJson.put("energyExpendedKj", sample.energyExpendedKj);
                    }
                    sampleJson.put("rrIntervalCount", sample.rrIntervalCount);
                    samples.put(sampleJson);
                }
                item.put("samples", samples);

                JSONArray sets = new JSONArray();
                for (StrengthSetEntity set : details.strengthSets) {
                    JSONObject setJson = new JSONObject();
                    setJson.put("exerciseName", set.exerciseName);
                    setJson.put("weight", set.weight);
                    setJson.put("weightUnit", set.weightUnit);
                    setJson.put("reps", set.reps);
                    setJson.put("timestampMillis", set.timestampMillis);
                    sets.put(setJson);
                }
                item.put("strengthSets", sets);
                root.put(item);
            }
            return root.toString(2);
        } catch (JSONException e) {
            throw new IOException("原始数据序列化失败", e);
        }
    }

    private static Uri writeToDownloads(Context context, String fileName, String json) throws IOException {
        ContentResolver resolver = context.getContentResolver();
        ContentValues values = new ContentValues();
        values.put(MediaStore.MediaColumns.DISPLAY_NAME, fileName);
        values.put(MediaStore.MediaColumns.MIME_TYPE, "application/json");
        values.put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/HR40");
        values.put(MediaStore.MediaColumns.IS_PENDING, 1);

        Uri uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values);
        if (uri == null) {
            throw new IOException("Unable to create raw data file in Downloads");
        }
        try (OutputStream output = resolver.openOutputStream(uri)) {
            if (output == null) {
                throw new IOException("Unable to open raw data output stream");
            }
            output.write(json.getBytes());
        }
        values.clear();
        values.put(MediaStore.MediaColumns.IS_PENDING, 0);
        resolver.update(uri, values, null, null);
        return uri;
    }
}
