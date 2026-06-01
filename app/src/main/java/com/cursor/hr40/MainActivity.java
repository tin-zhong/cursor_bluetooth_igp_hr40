package com.cursor.hr40;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONException;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

public final class MainActivity extends Activity implements BleHeartRateManager.Listener {
    private static final int REQUEST_BLE_PERMISSIONS = 1001;

    private final Handler handler = new Handler(Looper.getMainLooper());

    private BleHeartRateManager heartRateManager;
    private UserProfile profile;
    private WorkoutSession activeSession;
    private WorkoutSession lastCompletedSession;
    private HeartRateSample latestSample;

    private TextView statusText;
    private TextView bpmText;
    private TextView profileText;
    private TextView workoutText;
    private TextView statsText;
    private LinearLayout deviceListContainer;
    private final Map<String, Button> deviceButtons = new LinkedHashMap<>();
    private Button startButton;
    private Button endButton;
    private Button exportButton;

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
        loadLatestWorkout();
        updateAllUi();
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
            clearDeviceCandidates();
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
    public void onDeviceFound(BleHeartRateManager.DeviceCandidate candidate) {
        if (candidate.address == null || candidate.address.isEmpty()) {
            return;
        }
        deviceListContainer.setVisibility(View.VISIBLE);
        Button existing = deviceButtons.get(candidate.address);
        String label = deviceButtonLabel(candidate);
        if (existing != null) {
            existing.setText(label);
            return;
        }
        Button button = button(label);
        button.setAllCaps(false);
        button.setOnClickListener(view -> {
            statusText.setText("正在手动连接 " + candidate.displayName() + "...");
            heartRateManager.connectToDevice(candidate.address);
        });
        deviceButtons.put(candidate.address, button);
        deviceListContainer.addView(button, matchWrap());
    }

    @Override
    public void onHeartRate(HeartRateSample sample) {
        latestSample = sample;
        bpmText.setText(String.valueOf(sample.bpm));
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
    }

    private void buildUi() {
        ScrollView scrollView = new ScrollView(this);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(32, 40, 32, 32);
        scrollView.addView(root);

        TextView title = new TextView(this);
        title.setText("HR40 离线运动监测");
        title.setTextSize(24f);
        title.setTextColor(Color.rgb(18, 52, 86));
        title.setGravity(Gravity.CENTER_HORIZONTAL);
        root.addView(title, matchWrap());

        statusText = label("未连接心率带");
        statusText.setGravity(Gravity.CENTER_HORIZONTAL);
        root.addView(statusText, matchWrap());

        bpmText = new TextView(this);
        bpmText.setText("--");
        bpmText.setTextSize(64f);
        bpmText.setTextColor(Color.rgb(211, 47, 47));
        bpmText.setGravity(Gravity.CENTER_HORIZONTAL);
        root.addView(bpmText, matchWrap());

        TextView bpmLabel = label("bpm");
        bpmLabel.setGravity(Gravity.CENTER_HORIZONTAL);
        root.addView(bpmLabel, matchWrap());

        Button connectButton = button("扫描并连接 HR40");
        connectButton.setOnClickListener(view -> scanOrRequestPermissions());
        root.addView(connectButton, matchWrap());

        TextView scanHelp = label("提示：HR40 同一时间通常只能被一个 App 使用。若右侧 iGPSPORT 已显示连接，请先在 iGPSPORT 中解除绑定或断开连接，再回到本 App 扫描。");
        scanHelp.setTextColor(Color.DKGRAY);
        root.addView(scanHelp, matchWrap());

        deviceListContainer = new LinearLayout(this);
        deviceListContainer.setOrientation(LinearLayout.VERTICAL);
        deviceListContainer.setVisibility(View.GONE);
        TextView deviceTitle = label("扫描到的设备（如果未自动连接，可手动选择 HR40）");
        deviceTitle.setTextColor(Color.rgb(21, 101, 192));
        deviceListContainer.addView(deviceTitle, matchWrap());
        root.addView(deviceListContainer, matchWrap());

        startButton = button("开始运动");
        startButton.setOnClickListener(view -> startWorkout());
        root.addView(startButton, matchWrap());

        endButton = button("结束运动并导出 PDF");
        endButton.setOnClickListener(view -> finishWorkoutAndExport());
        root.addView(endButton, matchWrap());

        exportButton = button("导出最近一次运动 PDF");
        exportButton.setOnClickListener(view -> exportLastWorkout());
        root.addView(exportButton, matchWrap());

        Button editProfileButton = button("编辑运动人员资料");
        editProfileButton.setOnClickListener(view -> showProfileDialog(true));
        root.addView(editProfileButton, matchWrap());

        profileText = label("");
        profileText.setTextIsSelectable(true);
        root.addView(profileText, matchWrap());

        workoutText = label("");
        workoutText.setTextIsSelectable(true);
        root.addView(workoutText, matchWrap());

        statsText = label("");
        statsText.setTextIsSelectable(true);
        root.addView(statsText, matchWrap());

        TextView note = label("数据完全保存在本机：个人资料使用应用私有存储，运动记录使用本地 JSON，PDF 导出到 Downloads/HR40。");
        note.setTextColor(Color.DKGRAY);
        root.addView(note, matchWrap());

        setContentView(scrollView);
    }

    private void scanOrRequestPermissions() {
        clearDeviceCandidates();
        if (hasBlePermissions()) {
            heartRateManager.startScan();
            return;
        }
        requestPermissions(requiredBlePermissions(), REQUEST_BLE_PERMISSIONS);
    }

    private void startWorkout() {
        if (profile == null) {
            showProfileDialog(false);
            return;
        }
        if (activeSession != null) {
            showToast("当前已有运动记录正在进行");
            return;
        }
        activeSession = WorkoutSession.startNow();
        persistSessionQuietly(activeSession);
        statusText.setText("运动已开始，等待 HR40 心率数据...");
        updateAllUi();
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
        updateAllUi();
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
        if (session == null || session.samples().isEmpty()) {
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
        LinearLayout form = new LinearLayout(this);
        form.setOrientation(LinearLayout.VERTICAL);
        form.setPadding(32, 8, 32, 0);

        EditText nameInput = input("姓名或昵称", InputType.TYPE_CLASS_TEXT);
        EditText heightInput = input("身高 cm", InputType.TYPE_CLASS_NUMBER);
        EditText weightInput = input("体重 kg", InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        EditText ageInput = input("年龄", InputType.TYPE_CLASS_NUMBER);
        RadioGroup sexGroup = new RadioGroup(this);
        sexGroup.setOrientation(RadioGroup.HORIZONTAL);
        RadioButton male = new RadioButton(this);
        male.setText("男");
        male.setId(View.generateViewId());
        RadioButton female = new RadioButton(this);
        female.setText("女");
        female.setId(View.generateViewId());
        sexGroup.addView(male);
        sexGroup.addView(female);

        if (profile != null) {
            nameInput.setText(profile.name);
            heightInput.setText(String.valueOf(profile.heightCm));
            weightInput.setText(String.format(Locale.US, "%.1f", profile.weightKg));
            ageInput.setText(String.valueOf(profile.age));
            sexGroup.check(UserProfile.SEX_FEMALE.equals(profile.sex) ? female.getId() : male.getId());
        } else {
            sexGroup.check(male.getId());
        }

        form.addView(nameInput);
        form.addView(heightInput);
        form.addView(weightInput);
        form.addView(ageInput);
        form.addView(sexGroup);

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("首次使用请填写运动人员资料")
                .setMessage("资料仅保存在当前设备，用于估算最大心率、心率区间和能量消耗。")
                .setView(form)
                .setCancelable(cancellable)
                .setPositiveButton("保存", null)
                .create();
        dialog.setOnShowListener(dialogInterface -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(view -> {
            try {
                String name = nameInput.getText().toString().trim();
                int height = Integer.parseInt(heightInput.getText().toString().trim());
                double weight = Double.parseDouble(weightInput.getText().toString().trim());
                int age = Integer.parseInt(ageInput.getText().toString().trim());
                if (name.isEmpty() || height < 80 || height > 240 || weight < 20 || weight > 250 || age < 10 || age > 100) {
                    showToast("请填写合理的姓名、身高、体重和年龄");
                    return;
                }
                String sex = sexGroup.getCheckedRadioButtonId() == female.getId()
                        ? UserProfile.SEX_FEMALE
                        : UserProfile.SEX_MALE;
                profile = new UserProfile(name, height, weight, age, sex, System.currentTimeMillis());
                ProfileStore.save(this, profile);
                updateProfileUi();
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

    private void updateAllUi() {
        updateProfileUi();
        updateWorkoutUi();
    }

    private void updateProfileUi() {
        if (profile == null) {
            profileText.setText("运动人员资料: 未填写");
            return;
        }
        String sex = UserProfile.SEX_FEMALE.equals(profile.sex) ? "女" : "男";
        profileText.setText("运动人员资料\n"
                + "姓名: " + profile.name + "\n"
                + "性别: " + sex + "\n"
                + "身高: " + profile.heightCm + " cm\n"
                + "体重: " + profile.weightKg + " kg\n"
                + "年龄: " + profile.age + "\n"
                + "估算最大心率: " + profile.estimatedMaxHeartRate() + " bpm");
    }

    private void updateWorkoutUi() {
        WorkoutSession displaySession = activeSession != null ? activeSession : lastCompletedSession;
        startButton.setEnabled(activeSession == null);
        endButton.setEnabled(activeSession != null);
        exportButton.setEnabled(displaySession != null && !displaySession.samples().isEmpty());

        if (displaySession == null) {
            workoutText.setText("运动记录: 尚未开始");
            statsText.setText("");
            return;
        }

        WorkoutStats stats = WorkoutStats.calculate(profile, displaySession);
        String state = displaySession.isActive() ? "进行中" : "已结束";
        workoutText.setText("运动记录: " + state + "\n"
                + "时长: " + formatDuration(displaySession.durationMillis()) + "\n"
                + "采样点: " + stats.sampleCount + "\n"
                + "最近心率: " + (latestSample == null ? "--" : latestSample.bpm) + " bpm");

        StringBuilder builder = new StringBuilder();
        builder.append("运动统计\n")
                .append("平均心率: ").append(stats.avgBpm).append(" bpm\n")
                .append("最高心率: ").append(stats.maxBpm).append(" bpm\n")
                .append("最低心率: ").append(stats.minBpm).append(" bpm\n")
                .append("估算消耗: ").append(String.format(Locale.US, "%.1f kcal", stats.calories)).append("\n")
                .append("心率区间:\n");
        for (int i = 0; i < WorkoutStats.ZONE_LABELS.length; i++) {
            builder.append(" - ")
                    .append(WorkoutStats.ZONE_LABELS[i])
                    .append(": ")
                    .append(formatDuration(stats.zoneMillis[i]))
                    .append("\n");
        }
        statsText.setText(builder.toString());
    }


    private void clearDeviceCandidates() {
        deviceButtons.clear();
        if (deviceListContainer == null) {
            return;
        }
        while (deviceListContainer.getChildCount() > 1) {
            deviceListContainer.removeViewAt(1);
        }
        deviceListContainer.setVisibility(View.GONE);
    }

    private String deviceButtonLabel(BleHeartRateManager.DeviceCandidate candidate) {
        StringBuilder builder = new StringBuilder();
        builder.append(candidate.likelyHeartRateBand ? "连接候选心率带：" : "连接 BLE 设备：");
        builder.append(candidate.displayName());
        if (candidate.bonded) {
            builder.append("（已配对）");
        }
        if (candidate.hasHeartRateService) {
            builder.append("（心率服务）");
        }
        if (candidate.rssi != 0) {
            builder.append(" RSSI " ).append(candidate.rssi);
        }
        return builder.toString();
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

    private TextView label(String text) {
        TextView view = new TextView(this);
        view.setText(text);
        view.setTextSize(15f);
        view.setTextColor(Color.BLACK);
        view.setPadding(0, 12, 0, 12);
        return view;
    }

    private Button button(String text) {
        Button button = new Button(this);
        button.setText(text);
        return button;
    }

    private EditText input(String hint, int inputType) {
        EditText editText = new EditText(this);
        editText.setHint(hint);
        editText.setInputType(inputType);
        return editText;
    }

    private LinearLayout.LayoutParams matchWrap() {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        params.setMargins(0, 6, 0, 6);
        return params;
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
