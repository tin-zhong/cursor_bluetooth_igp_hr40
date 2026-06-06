package com.cursor.hr40;

import android.content.Intent;
import android.os.Bundle;
import android.widget.LinearLayout;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.button.MaterialButton;

import java.util.List;

public final class WorkoutDetailPickerActivity extends AppCompatActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        LinearLayout root = PageScaffold.open(this, "查看运动明细");
        List<WorkoutSession> sessions = WorkoutSessionLabels.collectExportableSessions(this);

        if (sessions.isEmpty()) {
            PageScaffold.bodyText(this, root, "暂无可查看的运动记录");
            PageScaffold.addBackButton(this, root);
            return;
        }

        PageScaffold.sectionTitle(this, root, "选择记录");
        for (WorkoutSession session : sessions) {
            MaterialButton itemButton = PageScaffold.actionButton(
                    this,
                    WorkoutSessionLabels.formatSessionLabel(session),
                    () -> {
                        Intent intent = new Intent(this, WorkoutDetailActivity.class);
                        intent.putExtra(WorkoutDetailActivity.EXTRA_SESSION_ID, session.id);
                        startActivity(intent);
                    });
            root.addView(itemButton, PageScaffold.matchWrap());
        }
        PageScaffold.addBackButton(this, root);
    }
}
