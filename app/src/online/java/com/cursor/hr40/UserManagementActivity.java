package com.cursor.hr40;

import android.content.Intent;
import android.os.Bundle;
import android.text.InputType;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

public final class UserManagementActivity extends AppCompatActivity {
    private boolean femaleSelected = false;

    private TextInputEditText nameInput;
    private TextInputEditText heightInput;
    private TextInputEditText weightInput;
    private TextInputEditText ageInput;
    private MaterialButton maleButton;
    private MaterialButton femaleButton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        ScrollView scrollView = new ScrollView(this);
        scrollView.setFillViewport(true);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(24), dp(32), dp(24), dp(32));
        root.setLayoutParams(new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));

        TextView title = new TextView(this);
        title.setText("用户管理");
        OnlineUi.stylePageTitle(title);
        root.addView(title, matchWrap());

        TextView profileHint = new TextView(this);
        profileHint.setText("运动人员资料");
        profileHint.setPadding(0, dp(16), 0, dp(8));
        OnlineUi.styleSectionTitle(profileHint);
        root.addView(profileHint, matchWrap());

        nameInput = addField(root, "姓名或昵称", InputType.TYPE_CLASS_TEXT);
        heightInput = addField(root, "身高 cm", InputType.TYPE_CLASS_NUMBER);
        weightInput = addField(root, "体重 kg", InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        ageInput = addField(root, "年龄", InputType.TYPE_CLASS_NUMBER);

        LinearLayout sexRow = new LinearLayout(this);
        sexRow.setOrientation(LinearLayout.HORIZONTAL);
        maleButton = outlinedButton("男");
        femaleButton = outlinedButton("女");
        maleButton.setOnClickListener(v -> setSexSelected(false));
        femaleButton.setOnClickListener(v -> setSexSelected(true));
        sexRow.addView(maleButton, weighted());
        sexRow.addView(femaleButton, weighted());
        root.addView(sexRow, matchWrap());

        MaterialButton saveProfileButton = materialButton("保存资料", v -> saveProfile());
        root.addView(saveProfileButton, matchWrap());

        TextView accountHint = new TextView(this);
        accountHint.setText("账户与安全");
        accountHint.setPadding(0, dp(24), 0, dp(8));
        OnlineUi.styleSectionTitle(accountHint);
        root.addView(accountHint, matchWrap());

        TextView email = new TextView(this);
        email.setText("邮箱: " + SupabaseSessionStore.getEmail(this));
        OnlineUi.styleBody(email);
        root.addView(email, matchWrap());

        MaterialButton changePassword = materialButton("修改密码", v -> showChangePasswordDialog());
        root.addView(changePassword, matchWrap());

        MaterialButton deleteAccount = materialButton("注销账户", v -> confirmDeleteStep1());
        root.addView(deleteAccount, matchWrap());

        loadProfileFields();
        scrollView.addView(root);
        setContentView(scrollView);
    }

    private TextInputEditText addField(LinearLayout root, String hint, int inputType) {
        TextInputLayout layout = new TextInputLayout(this);
        layout.setHint(hint);
        TextInputEditText input = new TextInputEditText(this);
        input.setInputType(inputType);
        layout.addView(input);
        root.addView(layout, matchWrap());
        return input;
    }

    private void loadProfileFields() {
        UserProfile local = ProfileStore.load(this);
        if (local == null) {
            setSexSelected(false);
            return;
        }
        nameInput.setText(local.name);
        heightInput.setText(String.valueOf(local.heightCm));
        weightInput.setText(String.valueOf(local.weightKg));
        ageInput.setText(String.valueOf(local.age));
        setSexSelected(UserProfile.SEX_FEMALE.equals(local.sex));
    }

    private void setSexSelected(boolean female) {
        femaleSelected = female;
        styleSexButton(maleButton, !female);
        styleSexButton(femaleButton, female);
    }

    private void saveProfile() {
        try {
            String name = nameInput.getText() == null ? "" : nameInput.getText().toString().trim();
            int height = Integer.parseInt(heightInput.getText().toString().trim());
            double weight = Double.parseDouble(weightInput.getText().toString().trim());
            int age = Integer.parseInt(ageInput.getText().toString().trim());
            if (name.isEmpty() || height < 80 || height > 240 || weight < 20 || weight > 250 || age < 10 || age > 100) {
                Toast.makeText(this, "请填写合理的资料", Toast.LENGTH_SHORT).show();
                return;
            }
            String sex = femaleSelected ? UserProfile.SEX_FEMALE : UserProfile.SEX_MALE;
            UserProfile profile = new UserProfile(name, height, weight, age, sex, System.currentTimeMillis());
            SupabaseApiClient.CloudProfile cloudProfile = new SupabaseApiClient.CloudProfile(
                    name, sex, age, height, weight, true);

            new Thread(() -> {
                try {
                    SupabaseApiClient client = new SupabaseApiClient(this);
                    client.upsertProfile(cloudProfile);
                    ProfileStore.save(this, profile);
                    runOnUiThread(() -> Toast.makeText(this, "资料已保存", Toast.LENGTH_SHORT).show());
                } catch (Exception e) {
                    runOnUiThread(() ->
                            Toast.makeText(this, "保存失败: " + e.getMessage(), Toast.LENGTH_LONG).show());
                }
            }).start();
        } catch (NumberFormatException e) {
            Toast.makeText(this, "请检查输入格式", Toast.LENGTH_SHORT).show();
        }
    }

    private void showChangePasswordDialog() {
        LinearLayout form = new LinearLayout(this);
        form.setOrientation(LinearLayout.VERTICAL);
        TextInputLayout layout = new TextInputLayout(this);
        layout.setHint("新密码");
        TextInputEditText input = new TextInputEditText(this);
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        layout.addView(input);
        form.addView(layout);

        new MaterialAlertDialogBuilder(this)
                .setTitle("修改密码")
                .setView(form)
                .setNegativeButton("取消", null)
                .setPositiveButton("下一步", (d, w) -> {
                    String password = input.getText() == null ? "" : input.getText().toString();
                    if (password.length() < 6) {
                        Toast.makeText(this, "密码至少 6 位", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    new MaterialAlertDialogBuilder(this)
                            .setTitle("确认修改密码")
                            .setMessage("确定要更新密码吗？")
                            .setNegativeButton("取消", null)
                            .setPositiveButton("确认", (d2, w2) -> updatePassword(password))
                            .show();
                })
                .show();
    }

    private void updatePassword(String password) {
        new Thread(() -> {
            try {
                new SupabaseApiClient(this).updatePassword(password);
                runOnUiThread(() ->
                        Toast.makeText(this, "密码已更新", Toast.LENGTH_SHORT).show());
            } catch (Exception e) {
                runOnUiThread(() ->
                        Toast.makeText(this, e.getMessage(), Toast.LENGTH_LONG).show());
            }
        }).start();
    }

    private void confirmDeleteStep1() {
        new MaterialAlertDialogBuilder(this)
                .setTitle("注销账户")
                .setMessage("注销会永久删除你的账户及全部运动记录。是否继续？")
                .setNegativeButton("取消", null)
                .setPositiveButton("继续", (d, w) -> confirmDeleteStep2())
                .show();
    }

    private void confirmDeleteStep2() {
        new MaterialAlertDialogBuilder(this)
                .setTitle("最终确认")
                .setMessage("这是最后一步。确认后账户和所有训练数据将被立即删除，且无法恢复。")
                .setNegativeButton("取消", null)
                .setPositiveButton("确认注销", (d, w) -> deleteAccount())
                .show();
    }

    private void deleteAccount() {
        new Thread(() -> {
            try {
                new SupabaseApiClient(this).deleteAccount();
                runOnUiThread(() -> {
                    Intent intent = new Intent(this, AuthActivity.class);
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                    startActivity(intent);
                    finish();
                });
            } catch (Exception e) {
                runOnUiThread(() ->
                        Toast.makeText(this, "注销失败: " + e.getMessage(), Toast.LENGTH_LONG).show());
            }
        }).start();
    }

    private MaterialButton materialButton(String text, android.view.View.OnClickListener listener) {
        MaterialButton button = new MaterialButton(this);
        button.setText(text);
        OnlineUi.styleButton(button);
        button.setOnClickListener(listener);
        return button;
    }

    private MaterialButton outlinedButton(String text) {
        MaterialButton button = new MaterialButton(
                this, null, com.google.android.material.R.attr.materialButtonOutlinedStyle);
        button.setText(text);
        OnlineUi.styleButton(button);
        return button;
    }

    private void styleSexButton(MaterialButton button, boolean selected) {
        button.setStrokeColorResource(selected ? R.color.md_primary : android.R.color.darker_gray);
        button.setTextColor(getColor(selected ? R.color.md_primary : android.R.color.darker_gray));
    }

    private LinearLayout.LayoutParams matchWrap() {
        return new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
    }

    private LinearLayout.LayoutParams weighted() {
        return new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
