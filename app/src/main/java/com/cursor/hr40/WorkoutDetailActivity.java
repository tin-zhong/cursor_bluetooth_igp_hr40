package com.cursor.hr40;

import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.widget.LinearLayout;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

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

        MaterialButton deleteButton = PageScaffold.actionButton(this, "删除该运动记录", () -> confirmDelete(session));
        deleteButton.setBackgroundTintList(ColorStateList.valueOf(Color.rgb(211, 47, 47)));
        deleteButton.setTextColor(Color.WHITE);
        LinearLayout.LayoutParams deleteParams = PageScaffold.matchWrap();
        deleteParams.topMargin = Math.round(8 * getResources().getDisplayMetrics().density);
        root.addView(deleteButton, deleteParams);
    }

    private void confirmDelete(WorkoutSession session) {
        new MaterialAlertDialogBuilder(this)
                .setTitle("删除运动记录")
                .setMessage("确定要删除这条运动记录吗？该操作不可恢复，关联的心率采样和力量组也会一并清除。")
                .setNegativeButton("取消", null)
                .setPositiveButton("确认删除", (dialog, which) ->
                        OnlineFeatures.deleteWorkout(this, session.id, this::finish, () -> {}))
                .show();
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
