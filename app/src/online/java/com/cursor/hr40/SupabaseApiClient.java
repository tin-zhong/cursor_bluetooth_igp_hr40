package com.cursor.hr40;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public final class SupabaseApiClient {
    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");

    private final OkHttpClient client = new OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .build();

    private final Context context;

    public SupabaseApiClient(Context context) {
        this.context = context.getApplicationContext();
    }

    public AuthResult signIn(String email, String password) throws IOException, JSONException, ApiException {
        JSONObject body = new JSONObject();
        body.put("email", email);
        body.put("password", password);
        JSONObject json = postAuth("/auth/v1/token?grant_type=password", body, null);
        return parseAuthResult(json);
    }

    public AuthResult signUp(String email, String password) throws IOException, JSONException, ApiException {
        JSONObject body = new JSONObject();
        body.put("email", email);
        body.put("password", password);
        JSONObject json = postAuth("/auth/v1/signup", body, null);
        if (json.has("access_token")) {
            return parseAuthResult(json);
        }
        JSONObject user = json.optJSONObject("user");
        if (user == null) {
            throw new ApiException("注册成功，请前往邮箱完成验证后再登录");
        }
        return null;
    }

    public CloudProfile fetchProfile() throws IOException, JSONException, ApiException {
        String userId = requireUserId();
        String path = "/rest/v1/profiles?id=eq." + userId + "&select=*";
        JSONArray rows = new JSONArray(getRest(path));
        if (rows.length() == 0) {
            return null;
        }
        return CloudProfile.fromJson(rows.getJSONObject(0));
    }

    public void upsertProfile(CloudProfile profile)
            throws IOException, JSONException, ApiException {
        String userId = requireUserId();
        JSONObject body = profile.toJson(userId);
        postRest("/rest/v1/profiles", body, "resolution=merge-duplicates,return=minimal");
    }

    public void updatePassword(String newPassword) throws IOException, JSONException, ApiException {
        JSONObject body = new JSONObject();
        body.put("password", newPassword);
        putAuth("/auth/v1/user", body);
    }

    public void deleteAccount() throws IOException, ApiException {
        postRest("/rest/v1/rpc/delete_account", new JSONObject(), "return=minimal");
        SupabaseSessionStore.clear(context);
        SyncStateStore.clear(context);
    }

    public List<CloudExercise> fetchExercises() throws IOException, JSONException, ApiException {
        String path = "/rest/v1/exercises?select=id,name&order=name.asc";
        JSONArray rows = new JSONArray(getRest(path));
        List<CloudExercise> exercises = new ArrayList<>();
        for (int i = 0; i < rows.length(); i++) {
            JSONObject row = rows.getJSONObject(i);
            exercises.add(new CloudExercise(
                    row.getString("id"),
                    row.getString("name")));
        }
        return exercises;
    }

    public void addExercise(String name) throws IOException, JSONException, ApiException {
        JSONObject body = new JSONObject();
        body.put("user_id", requireUserId());
        body.put("name", name.trim());
        postRest("/rest/v1/exercises", body, "return=minimal");
    }

    public void deleteExerciseByName(String name) throws IOException, JSONException, ApiException {
        String trimmed = name.trim();
        String encodedName = URLEncoder.encode(trimmed, StandardCharsets.UTF_8);
        String path = "/rest/v1/exercises?name=eq." + encodedName + "&select=id";
        JSONArray rows = new JSONArray(getRest(path));
        if (rows.length() == 0) {
            return;
        }
        String exerciseId = rows.getJSONObject(0).getString("id");
        deleteRest("/rest/v1/exercises?id=eq." + exerciseId);
    }

    public void syncWorkout(WorkoutSession session) throws IOException, JSONException, ApiException {
        String userId = requireUserId();
        JSONObject workoutBody = new JSONObject();
        workoutBody.put("user_id", userId);
        workoutBody.put("local_id", session.id);
        workoutBody.put("start_millis", session.startMillis);
        workoutBody.put("end_millis", session.endMillis);
        workoutBody.put("workout_type", session.workoutType);
        workoutBody.put("device_id", android.os.Build.MODEL);

        String workoutResponse = postRest(
                "/rest/v1/workout_records?on_conflict=user_id,local_id",
                workoutBody,
                "resolution=merge-duplicates,return=representation");
        JSONArray workoutRows = new JSONArray(workoutResponse);
        if (workoutRows.length() == 0) {
            throw new ApiException("同步训练记录失败");
        }
        String workoutId = workoutRows.getJSONObject(0).getString("id");

        deleteRest("/rest/v1/heart_rate_samples?workout_id=eq." + workoutId);
        deleteRest("/rest/v1/strength_sets?workout_id=eq." + workoutId);

        JSONArray sampleRows = new JSONArray();
        for (HeartRateSample sample : session.samples()) {
            JSONObject row = new JSONObject();
            row.put("workout_id", workoutId);
            row.put("user_id", userId);
            row.put("timestamp_millis", sample.timestampMillis);
            row.put("bpm", sample.bpm);
            row.put("contact_supported", sample.contactSupported);
            row.put("contact_detected", sample.contactDetected);
            if (sample.energyExpendedKj != null) {
                row.put("energy_expended_kj", sample.energyExpendedKj);
            }
            row.put("rr_interval_count", sample.rrIntervalCount);
            sampleRows.put(row);
        }
        if (sampleRows.length() > 0) {
            postRestArray("/rest/v1/heart_rate_samples", sampleRows, "return=minimal");
        }

        JSONArray setRows = new JSONArray();
        for (StrengthSet set : session.strengthSets()) {
            JSONObject row = new JSONObject();
            row.put("workout_id", workoutId);
            row.put("user_id", userId);
            row.put("exercise_name", set.exerciseName);
            row.put("weight", set.weight);
            row.put("weight_unit", set.weightUnit);
            row.put("reps", set.reps);
            row.put("timestamp_millis", set.timestampMillis);
            setRows.put(row);
        }
        if (setRows.length() > 0) {
            postRestArray("/rest/v1/strength_sets", setRows, "return=minimal");
        }

        SyncStateStore.markWorkoutSynced(context, session.id);
    }

    private AuthResult parseAuthResult(JSONObject json) throws JSONException {
        String accessToken = json.getString("access_token");
        String refreshToken = json.optString("refresh_token", "");
        JSONObject user = json.getJSONObject("user");
        String userId = user.getString("id");
        String email = user.optString("email", "");
        SupabaseSessionStore.save(context, accessToken, refreshToken, userId, email);
        return new AuthResult(userId, email);
    }

    private String requireUserId() throws ApiException {
        String userId = SupabaseSessionStore.getUserId(context);
        if (userId.isEmpty()) {
            throw new ApiException("未登录");
        }
        return userId;
    }

    private JSONObject postAuth(String path, JSONObject body, String token)
            throws IOException, JSONException, ApiException {
        Request.Builder builder = new Request.Builder()
                .url(BuildConfig.SUPABASE_URL + path)
                .post(RequestBody.create(body.toString(), JSON))
                .header("apikey", BuildConfig.SUPABASE_ANON_KEY)
                .header("Content-Type", "application/json");
        if (token != null) {
            builder.header("Authorization", "Bearer " + token);
        }
        return executeForJson(builder.build());
    }

    private JSONObject putAuth(String path, JSONObject body) throws IOException, JSONException, ApiException {
        Request request = new Request.Builder()
                .url(BuildConfig.SUPABASE_URL + path)
                .put(RequestBody.create(body.toString(), JSON))
                .header("apikey", BuildConfig.SUPABASE_ANON_KEY)
                .header("Authorization", "Bearer " + SupabaseSessionStore.getAccessToken(context))
                .header("Content-Type", "application/json")
                .build();
        return executeForJson(request);
    }

    private String getRest(String path) throws IOException, ApiException {
        Request request = new Request.Builder()
                .url(BuildConfig.SUPABASE_URL + path)
                .get()
                .header("apikey", BuildConfig.SUPABASE_ANON_KEY)
                .header("Authorization", "Bearer " + SupabaseSessionStore.getAccessToken(context))
                .build();
        return executeForBody(request);
    }

    private String postRest(String path, JSONObject body, String prefer)
            throws IOException, JSONException, ApiException {
        return postRestRaw(path, body.toString(), prefer);
    }

    private String postRestArray(String path, JSONArray body, String prefer)
            throws IOException, ApiException {
        return postRestRaw(path, body.toString(), prefer);
    }

    private String postRestRaw(String path, String jsonBody, String prefer) throws IOException, ApiException {
        Request.Builder builder = new Request.Builder()
                .url(BuildConfig.SUPABASE_URL + path)
                .post(RequestBody.create(jsonBody, JSON))
                .header("apikey", BuildConfig.SUPABASE_ANON_KEY)
                .header("Authorization", "Bearer " + SupabaseSessionStore.getAccessToken(context))
                .header("Content-Type", "application/json");
        if (prefer != null && !prefer.isEmpty()) {
            builder.header("Prefer", prefer);
        }
        return executeForBody(builder.build());
    }

    private void deleteRest(String path) throws IOException, ApiException {
        Request request = new Request.Builder()
                .url(BuildConfig.SUPABASE_URL + path)
                .delete()
                .header("apikey", BuildConfig.SUPABASE_ANON_KEY)
                .header("Authorization", "Bearer " + SupabaseSessionStore.getAccessToken(context))
                .build();
        executeForBody(request);
    }

    private JSONObject executeForJson(Request request) throws IOException, JSONException, ApiException {
        String body = executeForBody(request);
        return body.isEmpty() ? new JSONObject() : new JSONObject(body);
    }

    private String executeForBody(Request request) throws IOException, ApiException {
        try (Response response = client.newCall(request).execute()) {
            String body = response.body() == null ? "" : response.body().string();
            if (!response.isSuccessful()) {
                throw new ApiException(parseErrorMessage(body, response.code()));
            }
            return body;
        }
    }

    private String parseErrorMessage(String body, int code) {
        try {
            JSONObject json = new JSONObject(body);
            if (json.has("msg")) {
                return json.getString("msg");
            }
            if (json.has("message")) {
                return json.getString("message");
            }
            if (json.has("error_description")) {
                return json.getString("error_description");
            }
            if (json.has("code") && "23505".equals(json.optString("code"))) {
                return "该动作名称已存在";
            }
        } catch (JSONException ignored) {
        }
        if (code == 409) {
            return "数据冲突，请刷新后重试";
        }
        return "请求失败 (" + code + ")";
    }

    public static final class AuthResult {
        public final String userId;
        public final String email;

        AuthResult(String userId, String email) {
            this.userId = userId;
            this.email = email;
        }
    }

    public static final class CloudProfile {
        public final String name;
        public final String sex;
        public final int age;
        public final int heightCm;
        public final double weightKg;
        public final boolean profileCompleted;

        CloudProfile(String name, String sex, int age, int heightCm, double weightKg, boolean profileCompleted) {
            this.name = name;
            this.sex = sex;
            this.age = age;
            this.heightCm = heightCm;
            this.weightKg = weightKg;
            this.profileCompleted = profileCompleted;
        }

        static CloudProfile fromJson(JSONObject json) throws JSONException {
            return new CloudProfile(
                    json.optString("name", ""),
                    json.optString("sex", UserProfile.SEX_MALE),
                    json.optInt("age", 30),
                    json.optInt("height_cm", 170),
                    json.optDouble("weight_kg", 70.0),
                    json.optBoolean("profile_completed", false));
        }

        JSONObject toJson(String userId) throws JSONException {
            JSONObject json = new JSONObject();
            json.put("id", userId);
            json.put("name", name);
            json.put("sex", sex);
            json.put("age", age);
            json.put("height_cm", heightCm);
            json.put("weight_kg", weightKg);
            json.put("profile_completed", true);
            return json;
        }

        UserProfile toLocalProfile() {
            return new UserProfile(name, heightCm, weightKg, age, sex, System.currentTimeMillis());
        }
    }

    public static final class CloudExercise {
        public final String id;
        public final String name;

        CloudExercise(String id, String name) {
            this.id = id;
            this.name = name;
        }
    }

    public static final class ApiException extends Exception {
        ApiException(String message) {
            super(message);
        }
    }
}
