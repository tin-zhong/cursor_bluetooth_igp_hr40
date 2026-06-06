package com.cursor.hr40;

import android.content.Intent;
import android.widget.LinearLayout;

public final class OnlineFeatures {
    private OnlineFeatures() {
    }

    public static String appTitle(String versionName) {
        return "HR40 在线运动监测 v" + versionName;
    }

    public static void attachAccountButton(MainActivity activity, LinearLayout root) {
        com.google.android.material.button.MaterialButton accountButton =
                new com.google.android.material.button.MaterialButton(activity);
        accountButton.setText("账户管理");
        accountButton.setOnClickListener(v ->
                activity.startActivity(new Intent(activity, AccountActivity.class)));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        root.addView(accountButton, params);
    }
}
