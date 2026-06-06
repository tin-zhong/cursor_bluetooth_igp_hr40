package com.cursor.hr40;

import android.content.Intent;
import android.os.Bundle;
import android.text.InputType;
import android.widget.LinearLayout;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import org.json.JSONException;

public final class OnlineProfileActivity extends AppCompatActivity {
    private boolean femaleSelected = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (!SupabaseSessionStore.hasSession(this)) {
            startActivity(new Intent(this, AuthActivity.class));
            finish();
            return;
        }

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(24), dp(32), dp(24), dp(24));

        TextInputLayout nameLayout = new TextInputLayout(this);
        nameLayout.setHint("姓名或昵称");
        TextInputEditText nameInput = new TextInputEditText(this);
        nameLayout.addView(nameInput);
        root.addView(nameLayout);

        TextInputLayout heightLayout = new TextInputLayout(this);
        heightLayout.setHint("身高 cm");
        TextInputEditText heightInput = new TextInputEditText(this);
        heightInput.setInputType(InputType.TYPE_CLASS_NUMBER);
        heightLayout.addView(heightInput);
        root.addView(heightLayout);

        TextInputLayout weightLayout = new TextInputLayout(this);
        weightLayout.setHint("体重 kg");
        TextInputEditText weightInput = new TextInputEditText(this);
        weightInput.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        weightLayout.addView(weightInput);
        root.addView(weightLayout);

        TextInputLayout ageLayout = new TextInputLayout(this);
        ageLayout.setHint("年龄");
        TextInputEditText ageInput = new TextInputEditText(this);
        ageInput.setInputType(InputType.TYPE_CLASS_NUMBER);
        ageLayout.addView(ageInput);
        root.addView(ageLayout);

        LinearLayout sexRow = new LinearLayout(this);
        MaterialButton maleButton = new MaterialButton(this);
        maleButton.setText("男");
        MaterialButton femaleButton = new MaterialButton(this);
        femaleButton.setText("女");
        maleButton.setOnClickListener(v -> {
            femaleSelected = false;
            maleButton.setEnabled(false);
            femaleButton.setEnabled(true);
        });
        femaleButton.setOnClickListener(v -> {
            femaleSelected = true;
            maleButton.setEnabled(true);
            femaleButton.setEnabled(false);
        });
        maleButton.setEnabled(false);
        sexRow.addView(maleButton);
        sexRow.addView(femaleButton);
        root.addView(sexRow);

        UserProfile local = ProfileStore.load(this);
        if (local != null) {
            nameInput.setText(local.name);
            heightInput.setText(String.valueOf(local.heightCm));
            weightInput.setText(String.valueOf(local.weightKg));
            ageInput.setText(String.valueOf(local.age));
            femaleSelected = UserProfile.SEX_FEMALE.equals(local.sex);
            maleButton.setEnabled(femaleSelected);
            femaleButton.setEnabled(!femaleSelected);
        }

        MaterialButton saveButton = new MaterialButton(this);
        saveButton.setText("保存并继续");
        saveButton.setOnClickListener(v -> save(
                nameInput, heightInput, weightInput, ageInput));
        root.addView(saveButton);

        setContentView(root);
    }

    private void save(
            TextInputEditText nameInput,
            TextInputEditText heightInput,
            TextInputEditText weightInput,
            TextInputEditText ageInput) {
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
                    runOnUiThread(() -> {
                        startActivity(new Intent(this, MainActivity.class));
                        finish();
                    });
                } catch (Exception e) {
                    runOnUiThread(() ->
                            Toast.makeText(this, "保存失败: " + e.getMessage(), Toast.LENGTH_LONG).show());
                }
            }).start();
        } catch (NumberFormatException e) {
            Toast.makeText(this, "请检查输入格式", Toast.LENGTH_SHORT).show();
        } catch (JSONException e) {
            Toast.makeText(this, "保存资料失败", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public void onBackPressed() {
        // Force profile completion for new online users.
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
