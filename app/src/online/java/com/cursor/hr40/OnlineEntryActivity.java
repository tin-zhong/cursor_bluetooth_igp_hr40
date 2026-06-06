package com.cursor.hr40;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

public final class OnlineEntryActivity extends AppCompatActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        route();
    }

    private void route() {
        if (!SupabaseSessionStore.hasSession(this)) {
            startActivity(new Intent(this, AuthActivity.class));
            finish();
            return;
        }

        new Thread(() -> {
            try {
                SupabaseApiClient client = new SupabaseApiClient(this);
                SupabaseApiClient.CloudProfile cloudProfile = client.fetchProfile();
                runOnUiThread(() -> {
                    if (cloudProfile == null || !cloudProfile.profileCompleted) {
                        startActivity(new Intent(this, OnlineProfileActivity.class));
                    } else {
                        try {
                            ProfileStore.save(this, cloudProfile.toLocalProfile());
                        } catch (Exception ignored) {
                        }
                        startActivity(new Intent(this, MainActivity.class));
                    }
                    finish();
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    Toast.makeText(this, "加载账户失败: " + e.getMessage(), Toast.LENGTH_LONG).show();
                    SupabaseSessionStore.clear(this);
                    startActivity(new Intent(this, AuthActivity.class));
                    finish();
                });
            }
        }).start();
    }
}
