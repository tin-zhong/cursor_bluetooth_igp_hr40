package com.cursor.hr40;

import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

public final class AuthActivity extends AppCompatActivity {
    private boolean loginMode = true;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (SupabaseSessionStore.hasSession(this)) {
            startActivity(new Intent(this, OnlineEntryActivity.class));
            finish();
            return;
        }
        render();
    }

    private void render() {
        FrameLayout outer = new FrameLayout(this);
        outer.setBackgroundColor(Color.parseColor("#F5F7FB"));

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER_HORIZONTAL);
        int horizontalPadding = dp(24);
        root.setPadding(horizontalPadding, dp(24), horizontalPadding, dp(24));

        TextView title = new TextView(this);
        title.setText(loginMode ? appTitle() : "注册");
        title.setGravity(Gravity.CENTER_HORIZONTAL);
        OnlineUi.stylePageTitle(title);
        root.addView(title);

        TextInputLayout emailLayout = new TextInputLayout(this);
        TextInputEditText emailInput = new TextInputEditText(this);
        emailInput.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS);
        emailLayout.setHint("邮箱");
        emailLayout.addView(emailInput);
        LinearLayout.LayoutParams fieldParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        fieldParams.topMargin = dp(20);
        root.addView(emailLayout, fieldParams);

        TextInputLayout passwordLayout = new TextInputLayout(this);
        TextInputEditText passwordInput = new TextInputEditText(this);
        passwordInput.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        passwordLayout.setHint("密码");
        passwordLayout.addView(passwordInput);
        LinearLayout.LayoutParams passwordParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        passwordParams.topMargin = dp(12);
        root.addView(passwordLayout, passwordParams);

        MaterialButton submit = new MaterialButton(this);
        submit.setText(loginMode ? "登录" : "注册");
        OnlineUi.styleButton(submit);
        submit.setOnClickListener(v -> submit(emailInput, passwordInput));
        LinearLayout.LayoutParams submitParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        submitParams.topMargin = dp(20);
        root.addView(submit, submitParams);

        MaterialButton toggle = new MaterialButton(this, null, com.google.android.material.R.attr.borderlessButtonStyle);
        toggle.setText(loginMode ? "没有账号？去注册" : "已有账号？去登录");
        OnlineUi.styleButton(toggle);
        toggle.setOnClickListener(v -> {
            loginMode = !loginMode;
            render();
        });
        LinearLayout.LayoutParams toggleParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        toggleParams.topMargin = dp(8);
        root.addView(toggle, toggleParams);

        FrameLayout.LayoutParams rootParams = new FrameLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        rootParams.gravity = Gravity.CENTER;
        outer.addView(root, rootParams);
        setContentView(outer);
    }

    private String appTitle() {
        try {
            String versionName = getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
            return OnlineFeatures.appTitle(versionName);
        } catch (Exception ignored) {
            return OnlineFeatures.appTitle("unknown");
        }
    }

    private void submit(TextInputEditText emailInput, TextInputEditText passwordInput) {
        String email = emailInput.getText() == null ? "" : emailInput.getText().toString().trim();
        String password = passwordInput.getText() == null ? "" : passwordInput.getText().toString();
        if (email.isEmpty() || password.length() < 6) {
            Toast.makeText(this, "请填写邮箱和至少 6 位密码", Toast.LENGTH_SHORT).show();
            return;
        }

        new Thread(() -> {
            try {
                SupabaseApiClient client = new SupabaseApiClient(this);
                if (loginMode) {
                    client.signIn(email, password);
                    runOnUiThread(() -> {
                        startActivity(new Intent(this, OnlineEntryActivity.class));
                        finish();
                    });
                } else {
                    SupabaseApiClient.AuthResult result = client.signUp(email, password);
                    runOnUiThread(() -> {
                        if (result != null) {
                            startActivity(new Intent(this, OnlineEntryActivity.class));
                            finish();
                        } else {
                            new MaterialAlertDialogBuilder(this)
                                    .setTitle("注册成功")
                                    .setMessage("若开启了邮箱验证，请先前往邮箱完成验证后再登录。")
                                    .setPositiveButton("我知道了", (d, w) -> {
                                        loginMode = true;
                                        render();
                                    })
                                    .show();
                        }
                    });
                }
            } catch (Exception e) {
                runOnUiThread(() ->
                        Toast.makeText(this, e.getMessage(), Toast.LENGTH_LONG).show());
            }
        }).start();
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
