package com.cursor.hr40;

import android.content.Intent;
import android.os.Bundle;
import android.widget.LinearLayout;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.button.MaterialButton;

import java.util.List;

public final class WorkoutDetailPickerActivity extends AppCompatActivity {
    private LinearLayout root;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        root = PageScaffold.open(this, "查看运动明细");
        OnlineFeatures.refreshWorkoutList(this, this::populateList);
    }

    private void populateList() {
        List<WorkoutSession> sessions = WorkoutSessionLabels.collectExportableSessions(this);

        if (sessions.isEmpty()) {
            PageScaffold.bodyText(this, root, "暂无可查看的运动记录");
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
    }
}
