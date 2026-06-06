package com.cursor.hr40;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.widget.LinearLayout;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.button.MaterialButton;

import java.io.IOException;

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
            return;
        }

        root.addView(WorkoutDetailViews.build(this, profile, session), PageScaffold.matchWrap());

        MaterialButton exportButton = PageScaffold.actionButton(this, "导出运动记录 PDF", () -> exportSession(profile, session));
        LinearLayout.LayoutParams exportParams = PageScaffold.matchWrap();
        exportParams.topMargin = Math.round(16 * getResources().getDisplayMetrics().density);
        root.addView(exportButton, exportParams);
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
