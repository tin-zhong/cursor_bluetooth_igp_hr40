package com.cursor.hr40;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import org.json.JSONException;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class MainActivity extends AppCompatActivity implements BleHeartRateManager.Listener {
    private static final int REQUEST_BLE_PERMISSIONS = 1001;

    private final Handler handler = new Handler(Looper.getMainLooper());

    private BleHeartRateManager heartRateManager;
    private UserProfile profile;
    private WorkoutSession activeSession;
    private WorkoutSession lastCompletedSession;
    private HeartRateSample latestSample;
    private boolean reconnectScheduled;

    private TextView statusText;
    private TextView bpmText;
    private TextView durationText;
    private LinearLayout durationSection;
    private MaterialButton startButton;
    private MaterialButton endButton;
    private MaterialButton exportButton;
    private MaterialCardView strengthPanel;
    private Spinner exerciseSpinner;
    private ArrayAdapter<String> exerciseAdapter;
    private final List<String> exerciseNames = new ArrayList<>();
    private TextInputEditText strengthWeightInput;
    private MaterialButton unitToggleButton;
    private TextView repsValueText;
    private TextView strengthLogText;
    private int pendingReps;

    private final Runnable ticker = new Runnable() {
        @Override
        public void run() {
            updateWorkoutUi();
            handler.postDelayed(this, 1000L);
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
        }
        handler.post(ticker);
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
    }

    @Override
    public void onHeartRate(HeartRateSample sample) {
        reconnectScheduled = false;
        latestSample = sample;
        bpmText.setText(String.valueOf(sample.bpm));
        int maxHr = profile == null ? 190 : profile.estimatedMaxHeartRate();
        bpmText.setTextColor(WorkoutStats.zoneColor(sample.bpm, maxHr));
        String contact = sample.contactSupported
                ? (sample.contactDetected ? "佩戴状态正常" : "请调整心率带佩戴")
                : "设备未上报佩戴状态";
        statusText.setText(contact);

        if (activeSession != null) {
            activeSession.addSample(sample);
            if (activeSession.samples().size() % 10 == 0) {
                persistSessionQuietly(activeSession);
            }
        }
        updateWorkoutUi();
    }

    @Override
    public void onError(String message) {
        statusText.setText(message);
        showToast(message);
        if (activeSession != null && message.contains("连接异常: 8")) {
            scheduleWorkoutReconnect();
        }
    }

    private void buildUi() {
        ScrollView scrollView = new ScrollView(this);
        scrollView.setFillViewport(true);
        scrollView.setBackgroundColor(getColor(R.color.md_background));

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(20), dp(28), dp(20), dp(24));
        scrollView.addView(root, matchWrap());

        TextView title = new TextView(this);
        title.setText("HR40 离线运动监测 v1.1.1");
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 22f);
        title.setTextColor(getColor(R.color.md_primary));
        title.setGravity(Gravity.CENTER_HORIZONTAL);
        root.addView(title, matchWrap());

        statusText = textView("未连接心率带");
        statusText.setGravity(Gravity.CENTER_HORIZONTAL);
        statusText.setTextColor(Color.DKGRAY);
        root.addView(statusText, matchWrap());

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

        TextView bpmLabel = textView("bpm");
        bpmLabel.setGravity(Gravity.CENTER_HORIZONTAL);
        bpmLabel.setTextColor(Color.DKGRAY);
        metricsContent.addView(bpmLabel, matchWrap());
        root.addView(metricsCard, matchWrap());

        root.addView(materialButton("扫描并连接 HR40", v -> scanOrRequestPermissions()), matchWrap());

        startButton = materialButton("开始运动", v -> promptWorkoutType());
        root.addView(startButton, matchWrap());

        endButton = materialButton("结束运动并导出 PDF", v -> finishWorkoutAndExport());
        endButton.setEnabled(false);
        root.addView(endButton, matchWrap());

        exportButton = materialButton("导出最近一次运动 PDF", v -> exportLastWorkout());
        root.addView(exportButton, matchWrap());

        root.addView(materialButton("动作管理", v -> showExerciseManagementDialog()), matchWrap());
        root.addView(materialButton("编辑运动人员资料", v -> showProfileDialog(true)), matchWrap());

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
        setContentView(scrollView);
    }

    private void promptWorkoutType() {
        if (profile == null) {
            showProfileDialog(false);
            return;
        }
        if (activeSession != null) {
            showToast("当前已有运动记录正在进行");
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
        pendingReps = 0;
        repsValueText.setText("0");
        strengthWeightInput.setText("");
        persistSessionQuietly(activeSession);
        statusText.setText(WorkoutSession.TYPE_STRENGTH.equals(workoutType)
                ? "力量训练已开始，等待 HR40 心率数据..."
                : "有氧训练已开始，等待 HR40 心率数据...");
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

    private void finishWorkoutAndExport() {
        if (activeSession == null) {
            showToast("当前没有正在进行的运动");
            return;
        }
        activeSession.finish();
        persistSessionQuietly(activeSession);
        lastCompletedSession = activeSession;
        activeSession = null;
        updateWorkoutUi();
        exportSession(lastCompletedSession);
    }

    private void exportLastWorkout() {
        WorkoutSession session = lastCompletedSession;
        if (session == null) {
            try {
                session = WorkoutRepository.loadLatest(this);
            } catch (IOException e) {
                showToast("读取最近运动失败: " + e.getMessage());
                return;
            }
        }
        if (session == null || (session.samples().isEmpty() && session.strengthSets().isEmpty())) {
            showToast("暂无可导出的运动记录");
            return;
        }
        exportSession(session);
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
                dialog.dismiss();
            } catch (NumberFormatException | JSONException e) {
                showToast("保存资料失败，请检查输入");
            }
        }));
        dialog.show();
    }

    private void loadLatestWorkout() {
        try {
            WorkoutSession latest = WorkoutRepository.loadLatest(this);
            if (latest != null && !latest.isActive()) {
                lastCompletedSession = latest;
            }
        } catch (IOException ignored) {
            lastCompletedSession = null;
        }
    }

    private void persistSessionQuietly(WorkoutSession session) {
        try {
            WorkoutRepository.save(this, session);
        } catch (IOException | JSONException e) {
            statusText.setText("保存运动记录失败: " + e.getMessage());
        }
    }

    private void updateWorkoutUi() {
        WorkoutSession displaySession = activeSession != null ? activeSession : lastCompletedSession;
        startButton.setEnabled(activeSession == null);
        endButton.setEnabled(activeSession != null);
        exportButton.setEnabled(displaySession != null
                && (!displaySession.samples().isEmpty() || !displaySession.strengthSets().isEmpty()));

        boolean inWorkout = activeSession != null;
        durationSection.setVisibility(inWorkout ? View.VISIBLE : View.GONE);
        if (inWorkout) {
            durationText.setText(formatDuration(activeSession.durationMillis()));
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
        if (weight <= 0) {
            showToast("重量必须大于 0");
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
