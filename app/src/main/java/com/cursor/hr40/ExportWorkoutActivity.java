package com.cursor.hr40;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.widget.LinearLayout;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.button.MaterialButton;

import java.io.IOException;
import java.util.List;

public final class ExportWorkoutActivity extends AppCompatActivity {
    private LinearLayout root;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        root = PageScaffold.open(this, "导出运动记录 PDF");
        OnlineFeatures.refreshWorkoutList(this, this::populateList);
    }

    private void populateList() {
        UserProfile profile = ProfileStore.load(this);
        List<WorkoutSession> sessions = WorkoutSessionLabels.collectExportableSessions(this);

        if (sessions.isEmpty()) {
            PageScaffold.bodyText(this, root, "暂无可导出的运动记录");
            return;
        }

        PageScaffold.sectionTitle(this, root, "选择记录");
        for (WorkoutSession session : sessions) {
            MaterialButton itemButton = PageScaffold.actionButton(
                    this,
                    WorkoutSessionLabels.formatSessionLabel(session),
                    () -> exportSession(profile, session));
            root.addView(itemButton, PageScaffold.matchWrap());
        }
    }

    private void exportSession(UserProfile profile, WorkoutSession session) {
        try {
            Uri uri = PdfReportExporter.export(this, profile, session);
            Toast.makeText(this, "PDF 已导出", Toast.LENGTH_SHORT).show();
            Intent share = new Intent(Intent.ACTION_SEND);
            share.setType("application/pdf");
            share.putExtra(Intent.EXTRA_STREAM, uri);
            share.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(Intent.createChooser(share, "分享 HR40 运动报告"));
        } catch (IOException e) {
            Toast.makeText(this, "导出 PDF 失败: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }
}
