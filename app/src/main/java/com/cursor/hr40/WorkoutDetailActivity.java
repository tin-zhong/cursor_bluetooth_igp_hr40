package com.cursor.hr40;

import android.os.Bundle;
import android.widget.LinearLayout;

import androidx.appcompat.app.AppCompatActivity;

public final class WorkoutDetailActivity extends AppCompatActivity {
    public static final String EXTRA_SESSION_ID = "session_id";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        LinearLayout root = PageScaffold.open(this, "运动明细");
        String sessionId = getIntent().getStringExtra(EXTRA_SESSION_ID);
        WorkoutSession session = WorkoutSessionLabels.findById(this, sessionId);
        UserProfile profile = ProfileStore.load(this);

        if (session == null) {
            PageScaffold.bodyText(this, root, "未找到运动记录");
            PageScaffold.addBackButton(this, root);
            return;
        }

        root.addView(WorkoutDetailViews.build(this, profile, session), PageScaffold.matchWrap());
        PageScaffold.addBackButton(this, root);
    }
}
