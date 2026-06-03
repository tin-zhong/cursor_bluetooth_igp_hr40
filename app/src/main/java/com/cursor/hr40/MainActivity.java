package com.cursor.hr40;

import android.Manifest;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.CheckBox;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import com.cursor.hr40.db.WorkoutRecordMapper;
import com.cursor.hr40.db.WorkoutWithDetails;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import org.json.JSONException;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;


public final class MainActivity extends AppCompatActivity implements BleHeartRateManager.Listener {
    private static final int REQUEST_BLE_PERMISSIONS = 1001;
    private static final String PREFS_META = "app_meta";
    private static final String KEY_LAST_WEIGHT_PROMPT_WEEK = "last_weight_prompt_week";
    private static final String KEY_AUTO_CLEANUP_ENABLED = "auto_cleanup_enabled";
    private static final String KEY_RETENTION_DAYS = "retention_days";
    private static final String KEY_LAST_AUTO_CLEANUP_DAY = "last_auto_cleanup_day";

    private final Handler handler = new Handler(Looper.getMainLooper());

    private BleHeartRateManager heartRateManager;
    private UserProfile profile;
    private WorkoutSession activeSession;
    private WorkoutSession lastCompletedSession;
    private HeartRateSample latestSample;
    private boolean reconnectScheduled;
    private boolean heartRateLinkActive;

    private TextView statusText;
    private TextView bpmText;
    private TextView durationText;
    private TextView caloriesText;
    private LinearLayout durationSection;
    private LinearLayout caloriesSection;
    private MaterialButton scanButton;
    private MaterialButton startButton;
    private MaterialButton endButton;
    private MaterialButton exportButton;
    private MaterialButton rawExportButton;
    private MaterialButton exerciseManageButton;
    private MaterialButton editProfileButton;
    private MaterialButton fileManageButton;
    private MaterialButton historyManageButton;
    private MaterialCardView strengthPanel;
    private Spinner exerciseSpinner;
    private ArrayAdapter<String> exerciseAdapter;
    private final List<String> exerciseNames = new ArrayList<>();
    private TextInputEditText strengthWeightInput;
    private MaterialButton unitToggleButton;
    private TextView repsValueText;
    private TextView strengthLogText;
    private int pendingReps;
    private boolean workoutPaused;
    private long pauseStartedAtMillis;
    private long pausedDurationMillis;
    private long lastDisplayedDurationSeconds = -1L;

    private static final long UI_SECOND_TICK_MIN_DELAY_MS = 50L;
    private static final long MAINTENANCE_INTERVAL_MS = 60_000L;

    private final Runnable uiSecondTick = new Runnable() {
        @Override
        public void run() {
            if (activeSession != null && !workoutPaused) {
                updateWorkoutElapsedDisplay();
            }
            scheduleNextUiSecondTick();
        }
    };

    private final Runnable maintenanceTick = new Runnable() {
        @Override
        public void run() {
            runAutoHistoryCleanupIfNeeded();
            handler.postDelayed(this, MAINTENANCE_INTERVAL_MS);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        profile = ProfileStore.load(this);
        heartRateManager = new BleHeartRateManager(this, this);
        buildUi();
        reloadExerciseNames();
        loadLatestWorkout();
        updateWorkoutUi();
        if (profile == null) {
            showProfileDialog(false);
        } else if (shouldForceWeeklyWeightUpdate()) {
            showWeeklyWeightDialog();
        }
        handler.post(maintenanceTick);
        if (activeSession != null) {
            startUiSecondTick();
        }
    }

    @Override
    protected void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        if (heartRateManager != null) {
            heartRateManager.close();
        }
        super.onDestroy();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode != REQUEST_BLE_PERMISSIONS) {
            return;
        }
        if (hasBlePermissions()) {
            heartRateManager.startScan();
        } else {
            showToast("需要蓝牙权限才能连接 HR40 心率带");
        }
    }

    @Override
    public void onStatus(String status) {
        statusText.setText(status);
        if (shouldClearHeartRateDisplay(status)) {
            showDisconnectedHeartRate();
        }
    }

    @Override
    public void onHeartRate(HeartRateSample sample) {
        reconnectScheduled = false;
        heartRateLinkActive = true;
        latestSample = sample;
        bpmText.setText(String.valueOf(sample.bpm));
        int maxHr = profile == null ? 190 : profile.estimatedMaxHeartRate();
        bpmText.setTextColor(WorkoutStats.zoneColor(sample.bpm, maxHr));
        if (!workoutPaused) {
            String contact = sample.contactSupported
                    ? (sample.contactDetected ? "佩戴状态正常" : "请调整心率带佩戴")
                    : "设备未上报佩戴状态";
            statusText.setText(contact);
        }

        if (activeSession != null && !workoutPaused) {
            activeSession.addSample(sample);
            if (activeSession.samples().size() % 10 == 0) {
                persistSessionQuietly(activeSession);
            }
        }
    }

    @Override
    public void onError(String message) {
        statusText.setText(message);
        showDisconnectedHeartRate();
        showToast(message);
        if (activeSession != null && message.contains("连接异常: 8")) {
            scheduleWorkoutReconnect();
        }
    }


    private void showDisconnectedHeartRate() {
        heartRateLinkActive = false;
        bpmText.setText("--");
        bpmText.setTextColor(Color.GRAY);
    }

    private boolean shouldClearHeartRateDisplay(String status) {
        return status.contains("已断开")
                || status.contains("扫描")
                || status.contains("正在连接")
                || status.contains("未连接");
    }

    private void applySystemBarPadding(View rootView) {
        ViewCompat.setOnApplyWindowInsetsListener(rootView, (view, windowInsets) -> {
            Insets insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars());
            view.setPadding(0, insets.top + dp(16), 0, insets.bottom);
            return windowInsets;
        });
        ViewCompat.requestApplyInsets(rootView);
    }

    private void buildUi() {
        LinearLayout screen = new LinearLayout(this);
        screen.setOrientation(LinearLayout.VERTICAL);
        screen.setBackgroundColor(getColor(R.color.md_background));
        applySystemBarPadding(screen);

        LinearLayout fixedSection = new LinearLayout(this);
        fixedSection.setOrientation(LinearLayout.VERTICAL);
        fixedSection.setPadding(dp(20), dp(0), dp(20), dp(8));

        TextView title = new TextView(this);
        title.setText("HR40 离线运动监测 v" + appVersionName());
        LinearLayout.LayoutParams titleParams = matchWrap();
        titleParams.topMargin = dp(8);
        titleParams.bottomMargin = dp(4);
        title.setLayoutParams(titleParams);
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 22f);
        title.setTextColor(getColor(R.color.md_primary));
        title.setGravity(Gravity.CENTER_HORIZONTAL);
        fixedSection.addView(title, matchWrap());

        statusText = textView("未连接心率带");
        statusText.setGravity(Gravity.CENTER_HORIZONTAL);
        statusText.setTextColor(Color.DKGRAY);
        LinearLayout.LayoutParams statusParams = matchWrap();
        statusParams.bottomMargin = dp(12);
        fixedSection.addView(statusText, statusParams);

        MaterialCardView metricsCard = card();
        LinearLayout metricsContent = verticalLayout();
        metricsCard.addView(metricsContent);

        bpmText = new TextView(this);
        bpmText.setText("--");
        bpmText.setTextSize(TypedValue.COMPLEX_UNIT_SP, 64f);
        bpmText.setTextColor(Color.GRAY);
        bpmText.setGravity(Gravity.CENTER_HORIZONTAL);
        metricsContent.addView(bpmText, matchWrap());

        durationSection = verticalLayout();
        TextView durationLabel = textView("运动时长");
        durationLabel.setGravity(Gravity.CENTER_HORIZONTAL);
        durationLabel.setTextColor(Color.DKGRAY);
        durationSection.addView(durationLabel, matchWrap());
        durationText = new TextView(this);
        durationText.setText("00:00");
        durationText.setTextSize(TypedValue.COMPLEX_UNIT_SP, 64f);
        durationText.setTextColor(Color.BLACK);
        durationText.setGravity(Gravity.CENTER_HORIZONTAL);
        durationSection.addView(durationText, matchWrap());
        durationSection.setVisibility(View.GONE);
        metricsContent.addView(durationSection, matchWrap());

        caloriesSection = verticalLayout();
        TextView caloriesLabel = textView("估算消耗");
        caloriesLabel.setGravity(Gravity.CENTER_HORIZONTAL);
        caloriesLabel.setTextColor(Color.DKGRAY);
        caloriesSection.addView(caloriesLabel, matchWrap());
        caloriesText = new TextView(this);
        caloriesText.setText("0.0 kcal");
        caloriesText.setTextSize(TypedValue.COMPLEX_UNIT_SP, 28f);
        caloriesText.setTextColor(getColor(R.color.md_secondary));
        caloriesText.setGravity(Gravity.CENTER_HORIZONTAL);
        caloriesSection.addView(caloriesText, matchWrap());
        caloriesSection.setVisibility(View.GONE);
        metricsContent.addView(caloriesSection, matchWrap());

        TextView bpmLabel = textView("bpm");
        bpmLabel.setGravity(Gravity.CENTER_HORIZONTAL);
        bpmLabel.setTextColor(Color.DKGRAY);
        metricsContent.addView(bpmLabel, matchWrap());
        fixedSection.addView(metricsCard, matchWrap());

        ScrollView scrollView = new ScrollView(this);
        scrollView.setFillViewport(true);
        scrollView.setBackgroundColor(getColor(R.color.md_background));

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(20), dp(8), dp(20), dp(24));
        scrollView.addView(root, matchWrap());

        scanButton = materialButton("扫描并连接 HR40", v -> scanOrRequestPermissions());
        root.addView(scanButton, matchWrap());

        startButton = materialButton("开始运动", v -> handleStartPauseButton());
        root.addView(startButton, matchWrap());

        endButton = materialButton("结束运动", v -> confirmFinishWorkout());
        endButton.setEnabled(false);
        root.addView(endButton, matchWrap());

        exportButton = materialButton("导出运动记录 PDF", v -> showExportSessionDialog());
        root.addView(exportButton, matchWrap());

        rawExportButton = materialButton("导出原始训练数据(JSON)", v -> exportRawWorkoutData());
        root.addView(rawExportButton, matchWrap());

        exerciseManageButton = materialButton("动作管理", v -> showExerciseManagementDialog());
        root.addView(exerciseManageButton, matchWrap());
        editProfileButton = materialButton("编辑运动人员资料", v -> showProfileDialog(true));
        root.addView(editProfileButton, matchWrap());

        fileManageButton = materialButton("导出文件管理", v -> showExportedFilesDialog());
        root.addView(fileManageButton, matchWrap());

        historyManageButton = materialButton("历史数据管理", v -> showHistoryManageDialog());
        root.addView(historyManageButton, matchWrap());

        strengthPanel = card();
        strengthPanel.setVisibility(View.GONE);
        LinearLayout strengthContent = verticalLayout();
        strengthPanel.addView(strengthContent);

        TextView strengthTitle = textView("力量训练");
        strengthTitle.setTextColor(getColor(R.color.md_primary));
        strengthTitle.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f);
        strengthContent.addView(strengthTitle, matchWrap());

        exerciseSpinner = new Spinner(this);
        strengthContent.addView(exerciseSpinner, matchWrap());

        LinearLayout weightRow = new LinearLayout(this);
        weightRow.setOrientation(LinearLayout.HORIZONTAL);
        weightRow.setGravity(Gravity.CENTER_VERTICAL);

        TextInputLayout weightLayout = new TextInputLayout(
                this, null, com.google.android.material.R.attr.textInputOutlinedStyle);
        weightLayout.setHint("重量");
        strengthWeightInput = new TextInputEditText(this);
        strengthWeightInput.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        weightLayout.addView(strengthWeightInput);
        LinearLayout.LayoutParams weightParams = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        weightParams.setMargins(0, 0, dp(8), 0);
        weightRow.addView(weightLayout, weightParams);

        unitToggleButton = outlinedButton(currentWeightUnitLabel());
        unitToggleButton.setOnClickListener(v -> toggleWeightUnit());
        weightRow.addView(unitToggleButton, wrapContent());
        strengthContent.addView(weightRow, matchWrap());

        TextView repsLabel = textView("次数");
        repsLabel.setTextColor(Color.DKGRAY);
        strengthContent.addView(repsLabel, matchWrap());

        LinearLayout repsMinusRow = new LinearLayout(this);
        repsMinusRow.setOrientation(LinearLayout.HORIZONTAL);
        repsMinusRow.setGravity(Gravity.CENTER);
        repsMinusRow.addView(repButton("-10", -10));
        repsMinusRow.addView(repButton("-5", -5));
        repsMinusRow.addView(repButton("-1", -1));
        strengthContent.addView(repsMinusRow, matchWrap());

        repsValueText = textView("0");
        repsValueText.setTextSize(TypedValue.COMPLEX_UNIT_SP, 32f);
        repsValueText.setGravity(Gravity.CENTER_HORIZONTAL);
        strengthContent.addView(repsValueText, matchWrap());

        LinearLayout repsPlusRow = new LinearLayout(this);
        repsPlusRow.setOrientation(LinearLayout.HORIZONTAL);
        repsPlusRow.setGravity(Gravity.CENTER);
        repsPlusRow.addView(repButton("+1", 1));
        repsPlusRow.addView(repButton("+5", 5));
        repsPlusRow.addView(repButton("+10", 10));
        strengthContent.addView(repsPlusRow, matchWrap());

        strengthContent.addView(materialButton("记录本组", v -> recordStrengthSet()), matchWrap());

        strengthLogText = textView("");
        strengthLogText.setTextIsSelectable(true);
        strengthContent.addView(strengthLogText, matchWrap());

        root.addView(strengthPanel, matchWrap());

        screen.addView(fixedSection, matchWrap());
        LinearLayout.LayoutParams scrollParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1f);
        screen.addView(scrollView, scrollParams);
        setContentView(screen);
    }

    private void handleStartPauseButton() {
        if (activeSession == null) {
            promptWorkoutType();
            return;
        }
        if (workoutPaused) {
            resumeWorkout();
        } else {
            pauseWorkout();
        }
    }

    private void pauseWorkout() {
        if (activeSession == null || workoutPaused) {
            return;
        }
        workoutPaused = true;
        pauseStartedAtMillis = System.currentTimeMillis();
        statusText.setText("运动已暂停");
        showToast("已暂停运动");
        stopUiSecondTick();
        updateWorkoutUi();
    }

    private void resumeWorkout() {
        if (activeSession == null || !workoutPaused) {
            return;
        }
        pausedDurationMillis += Math.max(0L, System.currentTimeMillis() - pauseStartedAtMillis);
        workoutPaused = false;
        pauseStartedAtMillis = 0L;
        statusText.setText("运动已继续");
        showToast("已继续运动");
        lastDisplayedDurationSeconds = -1L;
        startUiSecondTick();
        updateWorkoutUi();
    }

    private void promptWorkoutType() {
        if (profile == null) {
            showProfileDialog(false);
            return;
        }
        new MaterialAlertDialogBuilder(this)
                .setTitle("选择训练类型")
                .setItems(new CharSequence[]{"力量训练", "有氧训练"}, (dialog, which) -> {
                    if (which == 0) {
                        beginWorkout(WorkoutSession.TYPE_STRENGTH);
                    } else {
                        beginWorkout(WorkoutSession.TYPE_AEROBIC);
                    }
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void beginWorkout(String workoutType) {
        activeSession = WorkoutSession.startNow(workoutType);
        workoutPaused = false;
        pauseStartedAtMillis = 0L;
        pausedDurationMillis = 0L;
        pendingReps = 0;
        repsValueText.setText("0");
        strengthWeightInput.setText("");
        persistSessionQuietly(activeSession);
        statusText.setText(WorkoutSession.TYPE_STRENGTH.equals(workoutType)
                ? "力量训练已开始，等待 HR40 心率数据..."
                : "有氧训练已开始，等待 HR40 心率数据...");
        startUiSecondTick();
        updateWorkoutUi();
    }

    private void scanOrRequestPermissions() {
        if (hasBlePermissions()) {
            heartRateManager.startScan();
            return;
        }
        requestPermissions(requiredBlePermissions(), REQUEST_BLE_PERMISSIONS);
    }

    private void scheduleWorkoutReconnect() {
        if (reconnectScheduled) {
            return;
        }
        reconnectScheduled = true;
        statusText.setText("运动中检测到心率带连接异常 8，正在自动重新扫描连接...");
        handler.postDelayed(() -> {
            if (activeSession == null) {
                reconnectScheduled = false;
                return;
            }
            if (hasBlePermissions()) {
                heartRateManager.startScan();
            } else {
                reconnectScheduled = false;
                requestPermissions(requiredBlePermissions(), REQUEST_BLE_PERMISSIONS);
            }
        }, 1200L);
    }

    private void confirmFinishWorkout() {
        if (activeSession == null) {
            showToast("当前没有正在进行的运动");
            return;
        }
        new MaterialAlertDialogBuilder(this)
                .setTitle("确认结束运动")
                .setMessage("确定要结束当前运动吗？")
                .setNegativeButton("取消", null)
                .setPositiveButton("确认结束", (dialog, which) -> finishWorkoutInternal())
                .show();
    }

    private void finishWorkoutInternal() {
        if (activeSession == null) {
            showToast("当前没有正在进行的运动");
            return;
        }
        if (workoutPaused) {
            pausedDurationMillis += Math.max(0L, System.currentTimeMillis() - pauseStartedAtMillis);
            workoutPaused = false;
            pauseStartedAtMillis = 0L;
        }
        long adjustedEndMillis = System.currentTimeMillis() - pausedDurationMillis;
        activeSession.endMillis = Math.max(activeSession.startMillis, adjustedEndMillis);
        try {
            WorkoutRepository.archiveFinishedSession(this, activeSession);
        } catch (IOException | JSONException e) {
            showToast("保存运动记录失败: " + e.getMessage());
            return;
        }
        lastCompletedSession = activeSession;
        activeSession = null;
        pausedDurationMillis = 0L;
        stopUiSecondTick();
        updateWorkoutUi();
        showToast("运动已结束，可按需导出 PDF");
    }

    private List<WorkoutSession> collectExportableSessions() {
        List<WorkoutSession> sessions = WorkoutRepository.loadAll(this);
        List<WorkoutSession> exportableSessions = new ArrayList<>();
        for (WorkoutSession session : sessions) {
            if (session.samples().isEmpty() && session.strengthSets().isEmpty()) {
                continue;
            }
            exportableSessions.add(session);
        }
        return exportableSessions;
    }

    private String appVersionName() {
        try {
            return getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
        } catch (Exception ignored) {
            return "unknown";
        }
    }

    private void showExportSessionDialog() {
        List<WorkoutSession> exportableSessions = collectExportableSessions();
        List<String> labels = new ArrayList<>();
        for (WorkoutSession session : exportableSessions) {
            labels.add(formatSessionLabel(session));
        }
        if (exportableSessions.isEmpty()) {
            showToast("暂无可导出的运动记录");
            return;
        }
        new MaterialAlertDialogBuilder(this)
                .setTitle("选择要导出的运动记录")
                .setItems(labels.toArray(new String[0]), (dialog, which) -> exportSession(exportableSessions.get(which)))
                .show();
    }

    private void exportSession(WorkoutSession session) {
        try {
            Uri uri = PdfReportExporter.export(this, profile, session);
            showToast("PDF 已导出");
            Intent share = new Intent(Intent.ACTION_SEND);
            share.setType("application/pdf");
            share.putExtra(Intent.EXTRA_STREAM, uri);
            share.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(Intent.createChooser(share, "分享 HR40 运动报告"));
        } catch (IOException e) {
            showToast("导出 PDF 失败: " + e.getMessage());
        }
    }

    private void showProfileDialog(boolean cancellable) {
        LinearLayout form = verticalLayout();
        form.setPadding(dp(8), dp(4), dp(8), 0);

        TextInputLayout nameLayout = inputLayout("姓名或昵称");
        TextInputEditText nameInput = new TextInputEditText(this);
        nameLayout.addView(nameInput);
        TextInputLayout heightLayout = inputLayout("身高 cm");
        TextInputEditText heightInput = new TextInputEditText(this);
        heightInput.setInputType(InputType.TYPE_CLASS_NUMBER);
        heightLayout.addView(heightInput);
        TextInputLayout weightLayout = inputLayout("体重 kg");
        TextInputEditText weightInput = new TextInputEditText(this);
        weightInput.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        weightLayout.addView(weightInput);
        TextInputLayout ageLayout = inputLayout("年龄");
        TextInputEditText ageInput = new TextInputEditText(this);
        ageInput.setInputType(InputType.TYPE_CLASS_NUMBER);
        ageLayout.addView(ageInput);

        LinearLayout sexRow = new LinearLayout(this);
        sexRow.setOrientation(LinearLayout.HORIZONTAL);
        MaterialButton maleButton = outlinedButton("男");
        MaterialButton femaleButton = outlinedButton("女");
        sexRow.addView(maleButton, weighted());
        sexRow.addView(femaleButton, weighted());

        final boolean[] femaleSelected = {false};
        if (profile != null) {
            nameInput.setText(profile.name);
            heightInput.setText(String.valueOf(profile.heightCm));
            weightInput.setText(String.format(Locale.US, "%.1f", profile.weightKg));
            ageInput.setText(String.valueOf(profile.age));
            femaleSelected[0] = UserProfile.SEX_FEMALE.equals(profile.sex);
        }
        styleSexButton(maleButton, !femaleSelected[0]);
        styleSexButton(femaleButton, femaleSelected[0]);
        maleButton.setOnClickListener(v -> {
            femaleSelected[0] = false;
            styleSexButton(maleButton, true);
            styleSexButton(femaleButton, false);
        });
        femaleButton.setOnClickListener(v -> {
            femaleSelected[0] = true;
            styleSexButton(maleButton, false);
            styleSexButton(femaleButton, true);
        });

        form.addView(nameLayout, matchWrap());
        form.addView(heightLayout, matchWrap());
        form.addView(weightLayout, matchWrap());
        form.addView(ageLayout, matchWrap());
        form.addView(sexRow, matchWrap());

        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(this)
                .setTitle("运动人员资料")
                .setMessage("资料仅保存在当前设备，用于估算最大心率、心率区间和能量消耗。")
                .setView(form)
                .setCancelable(cancellable)
                .setPositiveButton("保存", null);
        androidx.appcompat.app.AlertDialog dialog = builder.create();
        dialog.setOnShowListener(d -> dialog.getButton(androidx.appcompat.app.AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            try {
                String name = nameInput.getText() == null ? "" : nameInput.getText().toString().trim();
                int height = Integer.parseInt(heightInput.getText().toString().trim());
                double weight = Double.parseDouble(weightInput.getText().toString().trim());
                int age = Integer.parseInt(ageInput.getText().toString().trim());
                if (name.isEmpty() || height < 80 || height > 240 || weight < 20 || weight > 250 || age < 10 || age > 100) {
                    showToast("请填写合理的姓名、身高、体重和年龄");
                    return;
                }
                String sex = femaleSelected[0] ? UserProfile.SEX_FEMALE : UserProfile.SEX_MALE;
                profile = new UserProfile(name, height, weight, age, sex, System.currentTimeMillis());
                ProfileStore.save(this, profile);
                markWeightPromptHandledForCurrentWeek();
                dialog.dismiss();
            } catch (NumberFormatException | JSONException e) {
                showToast("保存资料失败，请检查输入");
            }
        }));
        dialog.show();
    }

    private boolean shouldForceWeeklyWeightUpdate() {
        Calendar calendar = Calendar.getInstance();
        if (calendar.get(Calendar.DAY_OF_WEEK) != Calendar.MONDAY) {
            return false;
        }
        int currentWeek = calendar.get(Calendar.YEAR) * 100 + calendar.get(Calendar.WEEK_OF_YEAR);
        SharedPreferences prefs = getSharedPreferences(PREFS_META, MODE_PRIVATE);
        int lastPromptWeek = prefs.getInt(KEY_LAST_WEIGHT_PROMPT_WEEK, -1);
        return lastPromptWeek != currentWeek;
    }

    private void markWeightPromptHandledForCurrentWeek() {
        Calendar calendar = Calendar.getInstance();
        int currentWeek = calendar.get(Calendar.YEAR) * 100 + calendar.get(Calendar.WEEK_OF_YEAR);
        getSharedPreferences(PREFS_META, MODE_PRIVATE)
                .edit()
                .putInt(KEY_LAST_WEIGHT_PROMPT_WEEK, currentWeek)
                .apply();
    }

    private void showWeeklyWeightDialog() {
        if (profile == null) {
            return;
        }
        LinearLayout form = verticalLayout();
        form.setPadding(dp(8), dp(4), dp(8), 0);

        TextInputLayout weightLayout = inputLayout("本周体重 kg");
        TextInputEditText weightInput = new TextInputEditText(this);
        weightInput.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        weightInput.setText(String.format(Locale.US, "%.1f", profile.weightKg));
        weightLayout.addView(weightInput);
        form.addView(weightLayout, matchWrap());

        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(this)
                .setTitle("每周体重更新")
                .setMessage("周一首次打开需先更新体重，以便本周消耗估算更准确。")
                .setView(form)
                .setCancelable(false)
                .setPositiveButton("保存", null);
        androidx.appcompat.app.AlertDialog dialog = builder.create();
        dialog.setCanceledOnTouchOutside(false);
        dialog.setOnShowListener(d -> dialog.getButton(androidx.appcompat.app.AlertDialog.BUTTON_POSITIVE)
                .setOnClickListener(v -> {
                    try {
                        double weight = Double.parseDouble(weightInput.getText().toString().trim());
                        if (weight < 20 || weight > 250) {
                            showToast("请填写合理的体重");
                            return;
                        }
                        profile = new UserProfile(
                                profile.name,
                                profile.heightCm,
                                weight,
                                profile.age,
                                profile.sex,
                                profile.createdAtMillis);
                        ProfileStore.save(this, profile);
                        markWeightPromptHandledForCurrentWeek();
                        dialog.dismiss();
                    } catch (NumberFormatException | JSONException e) {
                        showToast("保存体重失败，请检查输入");
                    }
                }));
        dialog.show();
    }

    private void loadLatestWorkout() {
        WorkoutSession latest = WorkoutRepository.loadLatest(this);
        if (latest != null && !latest.isActive()) {
            lastCompletedSession = latest;
        }
    }

    private void persistSessionQuietly(WorkoutSession session) {
        try {
            WorkoutRepository.saveJson(this, session);
        } catch (IOException | JSONException e) {
            statusText.setText("保存运动记录失败: " + e.getMessage());
        }
    }

    private void exportRawWorkoutData() {
        List<WorkoutWithDetails> details = WorkoutRepository.loadAllWithDetails(this);
        List<WorkoutWithDetails> exportable = new ArrayList<>();
        List<String> labels = new ArrayList<>();
        for (WorkoutWithDetails item : details) {
            WorkoutSession session = WorkoutRecordMapper.toSession(item);
            if (session == null || (session.samples().isEmpty() && session.strengthSets().isEmpty())) {
                continue;
            }
            exportable.add(item);
            labels.add(formatSessionLabel(session));
        }
        if (exportable.isEmpty()) {
            showToast("数据库暂无可导出的训练数据");
            return;
        }
        new MaterialAlertDialogBuilder(this)
                .setTitle("选择要导出的原始数据")
                .setItems(labels.toArray(new String[0]), (dialog, which) -> exportRawSingle(exportable.get(which)))
                .show();
    }

    private void exportRawSingle(WorkoutWithDetails details) {
        try {
            Uri uri = RawDataExporter.exportSingle(this, details);
            showToast("原始数据已导出");
            Intent share = new Intent(Intent.ACTION_SEND);
            share.setType("application/json");
            share.putExtra(Intent.EXTRA_STREAM, uri);
            share.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(Intent.createChooser(share, "分享 HR40 原始训练数据"));
        } catch (IOException e) {
            showToast("导出原始数据失败: " + e.getMessage());
        }
    }

    private static final class ExportedFileItem {
        Uri uri;
        String name;
        String mimeType;
        String relativePath;
    }

    private void showExportedFilesDialog() {
        List<ExportedFileItem> files = loadExportedFiles();
        if (files.isEmpty()) {
            showToast("暂无导出文件");
            return;
        }
        String[] labels = new String[files.size()];
        boolean[] checked = new boolean[files.size()];
        for (int i = 0; i < files.size(); i++) {
            ExportedFileItem item = files.get(i);
            labels[i] = item.name + " (" + item.mimeType + ")";
            checked[i] = false;
        }
        Set<Integer> selected = new HashSet<>();
        new MaterialAlertDialogBuilder(this)
                .setTitle("导出文件管理（可多选）")
                .setMultiChoiceItems(labels, checked, (dialog, which, isChecked) -> {
                    if (isChecked) {
                        selected.add(which);
                    } else {
                        selected.remove(which);
                    }
                })
                .setPositiveButton("批量移动", (dialog, which) -> {
                    if (selected.isEmpty()) {
                        showToast("请先选择文件");
                        return;
                    }
                    List<ExportedFileItem> targets = new ArrayList<>();
                    for (Integer index : selected) {
                        targets.add(files.get(index));
                    }
                    int moved = moveExportedFiles(targets);
                    showToast("已移动 " + moved + " 个文件");
                })
                .setNeutralButton("批量删除", (dialog, which) -> {
                    if (selected.isEmpty()) {
                        showToast("请先选择文件");
                        return;
                    }
                    List<ExportedFileItem> targets = new ArrayList<>();
                    for (Integer index : selected) {
                        targets.add(files.get(index));
                    }
                    int deleted = deleteExportedFiles(targets);
                    showToast("已删除 " + deleted + " 个文件");
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private int deleteExportedFiles(List<ExportedFileItem> items) {
        int count = 0;
        for (ExportedFileItem item : items) {
            int deleted = getContentResolver().delete(item.uri, null, null);
            if (deleted > 0) {
                count++;
            }
        }
        return count;
    }

    private int moveExportedFiles(List<ExportedFileItem> items) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            showToast("当前系统不支持直接移动，请使用文件管理器操作");
            return 0;
        }
        int count = 0;
        for (ExportedFileItem item : items) {
            ContentValues values = new ContentValues();
            values.put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/HR40_Archive");
            int updated = getContentResolver().update(item.uri, values, null, null);
            if (updated > 0) {
                count++;
            }
        }
        return count;
    }

    private List<ExportedFileItem> loadExportedFiles() {
        List<ExportedFileItem> items = new ArrayList<>();
        String[] projection = new String[]{
                MediaStore.Downloads._ID,
                MediaStore.MediaColumns.DISPLAY_NAME,
                MediaStore.MediaColumns.MIME_TYPE,
                MediaStore.MediaColumns.RELATIVE_PATH
        };
        String selection = MediaStore.MediaColumns.RELATIVE_PATH + " LIKE ?";
        String[] args = new String[]{"Download/HR40%"};
        ContentResolver resolver = getContentResolver();
        try (Cursor cursor = resolver.query(MediaStore.Downloads.EXTERNAL_CONTENT_URI, projection, selection, args, MediaStore.MediaColumns.DATE_MODIFIED + " DESC")) {
            if (cursor == null) {
                return items;
            }
            int idCol = cursor.getColumnIndexOrThrow(MediaStore.Downloads._ID);
            int nameCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DISPLAY_NAME);
            int mimeCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.MIME_TYPE);
            int pathCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.RELATIVE_PATH);
            while (cursor.moveToNext()) {
                ExportedFileItem item = new ExportedFileItem();
                long id = cursor.getLong(idCol);
                item.uri = ContentUris.withAppendedId(MediaStore.Downloads.EXTERNAL_CONTENT_URI, id);
                item.name = cursor.getString(nameCol);
                item.mimeType = cursor.getString(mimeCol);
                item.relativePath = cursor.getString(pathCol);
                items.add(item);
            }
        }
        return items;
    }

    private void showHistoryManageDialog() {
        List<WorkoutSession> sessions = collectExportableSessions();
        if (sessions.isEmpty()) {
            showToast("暂无历史训练记录");
            return;
        }
        SharedPreferences prefs = getSharedPreferences(PREFS_META, MODE_PRIVATE);
        boolean autoEnabled = prefs.getBoolean(KEY_AUTO_CLEANUP_ENABLED, false);
        int currentDays = prefs.getInt(KEY_RETENTION_DAYS, 30);

        ScrollView scrollView = new ScrollView(this);
        LinearLayout container = verticalLayout();
        container.setPadding(dp(4), dp(4), dp(4), dp(4));
        scrollView.addView(container);

        TextView statusView = textView(
                "共 " + sessions.size() + " 条训练记录。自动清理："
                        + (autoEnabled ? "已开启" : "已关闭")
                        + " | 保留周期：" + retentionPeriodLabel(currentDays));
        statusView.setTextColor(Color.DKGRAY);
        container.addView(statusView, matchWrap());

        Set<Integer> selected = new HashSet<>();
        for (int i = 0; i < sessions.size(); i++) {
            final int index = i;
            CheckBox checkBox = new CheckBox(this);
            checkBox.setText(formatSessionLabel(sessions.get(index)));
            checkBox.setOnCheckedChangeListener((buttonView, isChecked) -> {
                if (isChecked) {
                    selected.add(index);
                } else {
                    selected.remove(index);
                }
            });
            container.addView(checkBox, matchWrap());
        }

        new MaterialAlertDialogBuilder(this)
                .setTitle("历史数据管理")
                .setView(scrollView)
                .setPositiveButton("批量清理选中", (dialog, which) -> {
                    if (selected.isEmpty()) {
                        showToast("请先选择要清理的历史记录");
                        return;
                    }
                    List<String> ids = new ArrayList<>();
                    for (Integer index : selected) {
                        ids.add(sessions.get(index).id);
                    }
                    int deleted = WorkoutRepository.deleteWorkoutsByIds(this, ids);
                    lastCompletedSession = WorkoutRepository.loadLatest(this);
                    updateWorkoutUi();
                    showToast("已清理 " + deleted + " 条历史训练记录");
                })
                .setNeutralButton(autoEnabled ? "自动清理：已开启" : "自动清理：已关闭", (dialog, which) -> {
                    boolean next = !autoEnabled;
                    prefs.edit().putBoolean(KEY_AUTO_CLEANUP_ENABLED, next).apply();
                    showToast(next ? "自动清理已开启" : "自动清理已关闭");
                    showHistoryManageDialog();
                })
                .setNegativeButton("保留周期：" + retentionPeriodLabel(currentDays), (dialog, which) ->
                        showRetentionPeriodPickerDialog(prefs, currentDays, this::showHistoryManageDialog))
                .show();
    }

    private void showRetentionPeriodPickerDialog(SharedPreferences prefs, int currentDays, Runnable onSelected) {
        final int[] retentionOptions = new int[]{7, 30, 90, 180, 365};
        final String[] retentionLabels = new String[]{"1 周（7 天）", "1 个月（30 天）", "3 个月（90 天）", "6 个月（180 天）", "1 年（365 天）"};
        int checked = 1;
        for (int i = 0; i < retentionOptions.length; i++) {
            if (retentionOptions[i] == currentDays) {
                checked = i;
                break;
            }
        }
        new MaterialAlertDialogBuilder(this)
                .setTitle("选择保留周期")
                .setSingleChoiceItems(retentionLabels, checked, (dialog, which) -> {
                    int days = retentionOptions[which];
                    prefs.edit().putInt(KEY_RETENTION_DAYS, days).apply();
                    showToast("保留周期：" + retentionPeriodLabel(days));
                    dialog.dismiss();
                    if (onSelected != null) {
                        onSelected.run();
                    }
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private static String retentionPeriodLabel(int days) {
        switch (days) {
            case 7:
                return "1 周";
            case 30:
                return "1 个月";
            case 90:
                return "3 个月";
            case 180:
                return "6 个月";
            case 365:
                return "1 年";
            default:
                return days + " 天";
        }
    }

    private int cleanupHistoryOlderThanDays(int days) {
        long cutoff = System.currentTimeMillis() - days * 24L * 60L * 60L * 1000L;
        int deleted = WorkoutRepository.deleteWorkoutsOlderThan(this, cutoff);
        lastCompletedSession = WorkoutRepository.loadLatest(this);
        updateWorkoutUi();
        return deleted;
    }

    private void runAutoHistoryCleanupIfNeeded() {
        SharedPreferences prefs = getSharedPreferences(PREFS_META, MODE_PRIVATE);
        if (!prefs.getBoolean(KEY_AUTO_CLEANUP_ENABLED, false)) {
            return;
        }
        long today = System.currentTimeMillis() / (24L * 60L * 60L * 1000L);
        long lastDay = prefs.getLong(KEY_LAST_AUTO_CLEANUP_DAY, -1L);
        if (today == lastDay) {
            return;
        }
        int days = prefs.getInt(KEY_RETENTION_DAYS, 30);
        cleanupHistoryOlderThanDays(days);
        prefs.edit().putLong(KEY_LAST_AUTO_CLEANUP_DAY, today).apply();
    }

    private long currentWorkoutDurationMillis() {
        if (activeSession == null) {
            return 0L;
        }
        long duration = activeSession.durationMillis() - pausedDurationMillis;
        if (workoutPaused) {
            duration -= Math.max(0L, System.currentTimeMillis() - pauseStartedAtMillis);
        }
        return Math.max(0L, duration);
    }

    /**
     * 与手机系统时钟整秒对齐：在下一秒的 0 毫秒时刻触发刷新。
     */
    private void scheduleNextUiSecondTick() {
        long now = System.currentTimeMillis();
        long nextSecondMillis = ((now / 1000L) + 1L) * 1000L;
        long delay = nextSecondMillis - now;
        if (delay < UI_SECOND_TICK_MIN_DELAY_MS) {
            delay += 1000L;
        }
        handler.removeCallbacks(uiSecondTick);
        handler.postDelayed(uiSecondTick, delay);
    }

    /**
     * 按系统时钟走过的整秒数计算运动时长（与状态栏时钟秒针同步跳变）。
     * 归档、导出仍使用 {@link #currentWorkoutDurationMillis()} 的毫秒精度。
     */
    private long currentWorkoutDurationClockSeconds() {
        if (activeSession == null) {
            return 0L;
        }
        long nowSecond = System.currentTimeMillis() / 1000L;
        long startSecond = activeSession.startMillis / 1000L;
        long pausedSeconds = pausedDurationMillis / 1000L;
        long seconds = nowSecond - startSecond - pausedSeconds;
        if (workoutPaused) {
            seconds -= (nowSecond - (pauseStartedAtMillis / 1000L));
        }
        return Math.max(0L, seconds);
    }

    private void startUiSecondTick() {
        lastDisplayedDurationSeconds = -1L;
        handler.removeCallbacks(uiSecondTick);
        scheduleNextUiSecondTick();
    }

    private void stopUiSecondTick() {
        handler.removeCallbacks(uiSecondTick);
    }

    private void updateWorkoutElapsedDisplay() {
        if (activeSession == null) {
            return;
        }
        long seconds = currentWorkoutDurationClockSeconds();
        if (seconds != lastDisplayedDurationSeconds) {
            lastDisplayedDurationSeconds = seconds;
            durationText.setText(formatDuration(seconds * 1000L));
        }
        caloriesText.setText(String.format(Locale.US, "%.1f kcal",
                EnergyEstimator.estimateCalories(profile, activeSession)));
    }

    private String formatSessionLabel(WorkoutSession session) {
        String type = WorkoutSession.TYPE_STRENGTH.equals(session.workoutType) ? "力量" : "有氧";
        String start = new SimpleDateFormat("MM-dd HH:mm", Locale.getDefault())
                .format(new Date(session.startMillis));
        return start + " | " + type + " | 时长 " + formatDuration(session.durationMillis());
    }

    private void updateEndButtonStyle(boolean enabled) {
        if (enabled) {
            endButton.setBackgroundTintList(ColorStateList.valueOf(Color.rgb(211, 47, 47)));
            endButton.setTextColor(Color.WHITE);
        } else {
            endButton.setBackgroundTintList(ColorStateList.valueOf(Color.LTGRAY));
            endButton.setTextColor(Color.DKGRAY);
        }
    }

    private void updateWorkoutUi() {
        startButton.setEnabled(true);
        boolean endEnabled = activeSession != null;
        endButton.setEnabled(endEnabled);
        updateEndButtonStyle(endEnabled);
        exportButton.setEnabled(true);
        rawExportButton.setEnabled(true);

        boolean inWorkout = activeSession != null;
        exportButton.setVisibility(inWorkout ? View.GONE : View.VISIBLE);
        rawExportButton.setVisibility(inWorkout ? View.GONE : View.VISIBLE);
        exerciseManageButton.setVisibility(inWorkout ? View.GONE : View.VISIBLE);
        editProfileButton.setVisibility(inWorkout ? View.GONE : View.VISIBLE);
        fileManageButton.setVisibility(inWorkout ? View.GONE : View.VISIBLE);
        historyManageButton.setVisibility(inWorkout ? View.GONE : View.VISIBLE);
        if (!inWorkout) {
            startButton.setText("开始运动");
        } else if (workoutPaused) {
            startButton.setText("继续运动");
        } else {
            startButton.setText("暂停运动");
        }
        durationSection.setVisibility(inWorkout ? View.VISIBLE : View.GONE);
        caloriesSection.setVisibility(inWorkout ? View.VISIBLE : View.GONE);
        if (inWorkout) {
            updateWorkoutElapsedDisplay();
        } else {
            lastDisplayedDurationSeconds = -1L;
            caloriesText.setText("0.0 kcal");
        }

        boolean showStrength = inWorkout
                && WorkoutSession.TYPE_STRENGTH.equals(activeSession.workoutType);
        strengthPanel.setVisibility(showStrength ? View.VISIBLE : View.GONE);
        if (showStrength) {
            updateStrengthLog(activeSession);
        }
    }

    private MaterialButton repButton(String labelText, int delta) {
        MaterialButton button = outlinedButton(labelText);
        button.setMinWidth(0);
        button.setMinimumWidth(dp(72));
        button.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f);
        button.setOnClickListener(v -> adjustPendingReps(delta));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        params.setMargins(dp(4), dp(4), dp(4), dp(4));
        button.setLayoutParams(params);
        return button;
    }

    private void adjustPendingReps(int delta) {
        pendingReps = Math.max(0, pendingReps + delta);
        repsValueText.setText(String.valueOf(pendingReps));
    }

    private void toggleWeightUnit() {
        String next = StrengthSet.UNIT_KG.equals(ExerciseStore.loadWeightUnit(this))
                ? StrengthSet.UNIT_LB
                : StrengthSet.UNIT_KG;
        ExerciseStore.saveWeightUnit(this, next);
        unitToggleButton.setText(currentWeightUnitLabel());
    }

    private String currentWeightUnitLabel() {
        return ExerciseStore.loadWeightUnit(this);
    }

    private void reloadExerciseNames() {
        try {
            exerciseNames.clear();
            exerciseNames.addAll(ExerciseStore.loadExercises(this));
        } catch (IOException e) {
            exerciseNames.clear();
        }
        if (exerciseAdapter == null) {
            exerciseAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, exerciseNames);
            exerciseAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
            exerciseSpinner.setAdapter(exerciseAdapter);
        } else {
            exerciseAdapter.notifyDataSetChanged();
        }
    }

    private void showExerciseManagementDialog() {
        ScrollView scrollView = new ScrollView(this);
        LinearLayout container = verticalLayout();
        container.setPadding(dp(4), dp(4), dp(4), dp(4));
        scrollView.addView(container);

        TextInputLayout addLayout = inputLayout("新动作名称");
        TextInputEditText addInput = new TextInputEditText(this);
        addLayout.addView(addInput);
        container.addView(addLayout, matchWrap());

        LinearLayout listContainer = verticalLayout();
        container.addView(listContainer, matchWrap());

        final Runnable[] refreshHolder = new Runnable[1];
        refreshHolder[0] = () -> {
            listContainer.removeAllViews();
            reloadExerciseNames();
            if (exerciseNames.isEmpty()) {
                TextView empty = textView("暂无动作，请先添加。");
                empty.setTextColor(Color.DKGRAY);
                listContainer.addView(empty, matchWrap());
                return;
            }
            for (String name : new ArrayList<>(exerciseNames)) {
                MaterialCardView rowCard = card();
                rowCard.setCardElevation(0f);
                LinearLayout row = new LinearLayout(MainActivity.this);
                row.setOrientation(LinearLayout.HORIZONTAL);
                row.setGravity(Gravity.CENTER_VERTICAL);
                TextView nameView = textView(name);
                nameView.setLayoutParams(weighted());
                MaterialButton deleteButton = outlinedButton("删除");
                deleteButton.setOnClickListener(v -> {
                    new MaterialAlertDialogBuilder(MainActivity.this)
                            .setTitle("删除动作")
                            .setMessage("确定删除 \"" + name + "\" 吗？")
                            .setPositiveButton("删除", (d, w) -> {
                                try {
                                    ExerciseStore.deleteExercise(MainActivity.this, name);
                                    refreshHolder[0].run();
                                } catch (IOException | JSONException e) {
                                    showToast("删除失败: " + e.getMessage());
                                }
                            })
                            .setNegativeButton("取消", null)
                            .show();
                });
                row.addView(nameView);
                row.addView(deleteButton, wrapContent());
                rowCard.addView(row);
                listContainer.addView(rowCard, matchWrap());
            }
        };

        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(this)
                .setTitle("动作管理")
                .setView(scrollView)
                .setPositiveButton("添加动作", null)
                .setNegativeButton("关闭", null);
        androidx.appcompat.app.AlertDialog dialog = builder.create();
        dialog.setOnShowListener(d -> dialog.getButton(androidx.appcompat.app.AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String name = addInput.getText() == null ? "" : addInput.getText().toString().trim();
            if (name.isEmpty()) {
                showToast("请输入动作名称");
                return;
            }
            try {
                ExerciseStore.addExercise(this, name);
                addInput.setText("");
                refreshHolder[0].run();
                for (int i = 0; i < exerciseNames.size(); i++) {
                    if (exerciseNames.get(i).equalsIgnoreCase(name)) {
                        exerciseSpinner.setSelection(i);
                        break;
                    }
                }
            } catch (IOException | JSONException e) {
                showToast("添加失败: " + e.getMessage());
            }
        }));
        refreshHolder[0].run();
        dialog.show();
    }

    private void recordStrengthSet() {
        if (activeSession == null) {
            showToast("请先开始运动");
            return;
        }
        if (!WorkoutSession.TYPE_STRENGTH.equals(activeSession.workoutType)) {
            showToast("当前不是力量训练");
            return;
        }
        if (exerciseNames.isEmpty()) {
            showToast("请先在动作管理中添加训练动作");
            return;
        }
        String exerciseName = (String) exerciseSpinner.getSelectedItem();
        if (exerciseName == null || exerciseName.trim().isEmpty()) {
            showToast("请选择训练动作");
            return;
        }
        double weight;
        try {
            weight = Double.parseDouble(strengthWeightInput.getText().toString().trim());
        } catch (NumberFormatException e) {
            showToast("请输入有效重量");
            return;
        }
        if (weight < 0) {
            showToast("重量不能为负数");
            return;
        }
        if (pendingReps <= 0) {
            showToast("次数必须大于 0");
            return;
        }
        String unit = ExerciseStore.loadWeightUnit(this);
        StrengthSet set = new StrengthSet(
                exerciseName.trim(),
                weight,
                unit,
                pendingReps,
                System.currentTimeMillis());
        activeSession.addStrengthSet(set);
        persistSessionQuietly(activeSession);
        pendingReps = 0;
        repsValueText.setText("0");
        updateStrengthLog(activeSession);
        showToast("已记录: " + exerciseName + " " + set.displayWeight() + " x " + set.reps);
    }

    private void updateStrengthLog(WorkoutSession session) {
        List<StrengthSet> sets = session.strengthSets();
        if (sets.isEmpty()) {
            strengthLogText.setText("已记录组数: 0");
            return;
        }
        StringBuilder builder = new StringBuilder("已记录组数: " + sets.size() + "\n");
        int index = 1;
        for (StrengthSet set : sets) {
            builder.append(index++)
                    .append(". ")
                    .append(set.exerciseName)
                    .append("  ")
                    .append(set.displayWeight())
                    .append(" x")
                    .append(set.reps)
                    .append("\n");
        }
        strengthLogText.setText(builder.toString().trim());
    }

    private boolean hasBlePermissions() {
        for (String permission : requiredBlePermissions()) {
            if (checkSelfPermission(permission) != PackageManager.PERMISSION_GRANTED) {
                return false;
            }
        }
        return true;
    }

    private String[] requiredBlePermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            return new String[]{Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT};
        }
        return new String[]{Manifest.permission.ACCESS_FINE_LOCATION};
    }

    private MaterialCardView card() {
        MaterialCardView card = new MaterialCardView(this);
        card.setRadius(dp(16));
        card.setCardElevation(dp(2));
        card.setUseCompatPadding(true);
        card.setContentPadding(dp(16), dp(16), dp(16), dp(16));
        LinearLayout.LayoutParams params = matchWrap();
        params.setMargins(0, dp(8), 0, dp(8));
        card.setLayoutParams(params);
        return card;
    }

    private MaterialButton materialButton(String text, View.OnClickListener listener) {
        MaterialButton button = new MaterialButton(this);
        button.setText(text);
        button.setAllCaps(false);
        button.setOnClickListener(listener);
        return button;
    }

    private MaterialButton outlinedButton(String text) {
        MaterialButton button = new MaterialButton(
                this, null, com.google.android.material.R.attr.materialButtonOutlinedStyle);
        button.setText(text);
        button.setAllCaps(false);
        return button;
    }

    private TextInputLayout inputLayout(String hint) {
        TextInputLayout layout = new TextInputLayout(
                this, null, com.google.android.material.R.attr.textInputOutlinedStyle);
        layout.setHint(hint);
        return layout;
    }

    private TextView textView(String text) {
        TextView view = new TextView(this);
        view.setText(text);
        view.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f);
        view.setTextColor(Color.BLACK);
        view.setPadding(0, dp(6), 0, dp(6));
        return view;
    }

    private LinearLayout verticalLayout() {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        return layout;
    }

    private LinearLayout.LayoutParams matchWrap() {
        return new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
    }

    private LinearLayout.LayoutParams wrapContent() {
        return new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
    }

    private LinearLayout.LayoutParams weighted() {
        return new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
    }

    private void styleSexButton(MaterialButton button, boolean selected) {
        button.setStrokeColorResource(selected ? R.color.md_primary : android.R.color.darker_gray);
        button.setTextColor(getColor(selected ? R.color.md_primary : android.R.color.darker_gray));
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private void showToast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
    }

    private static String formatDuration(long millis) {
        long seconds = Math.max(0L, millis / 1000L);
        long hours = seconds / 3600L;
        long minutes = (seconds % 3600L) / 60L;
        long secs = seconds % 60L;
        if (hours > 0L) {
            return String.format(Locale.US, "%d:%02d:%02d", hours, minutes, secs);
        }
        return String.format(Locale.US, "%02d:%02d", minutes, secs);
    }
}
