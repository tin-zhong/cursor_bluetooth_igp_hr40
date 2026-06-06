package com.cursor.hr40;

import android.content.Intent;
import android.os.Bundle;
import android.text.InputType;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

public final class AccountActivity extends AppCompatActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(24), dp(32), dp(24), dp(24));

        TextView email = new TextView(this);
        email.setText("邮箱: " + SupabaseSessionStore.getEmail(this));
        root.addView(email);

        MaterialButton changePassword = new MaterialButton(this);
        changePassword.setText("修改密码");
        changePassword.setOnClickListener(v -> showChangePasswordDialog());
        root.addView(changePassword);

        MaterialButton deleteAccount = new MaterialButton(this);
        deleteAccount.setText("注销账户");
        deleteAccount.setOnClickListener(v -> confirmDeleteStep1());
        root.addView(deleteAccount);

        setContentView(root);
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

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
