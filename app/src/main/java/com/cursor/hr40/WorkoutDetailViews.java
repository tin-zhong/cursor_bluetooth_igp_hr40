package com.cursor.hr40;

import android.content.Context;
import android.graphics.Color;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/** Builds in-app scrollable workout detail content. */
public final class WorkoutDetailViews {
    private WorkoutDetailViews() {
    }

    public static View build(Context context, UserProfile profile, WorkoutSession session) {
        WorkoutStats stats = WorkoutStats.calculate(profile, session);
        int maxHr = profile == null ? 190 : profile.estimatedMaxHeartRate();

        LinearLayout root = vertical(context);
        root.addView(sectionTitle(context, "运动概览"), matchWrap(context));
        root.addView(summaryBlock(context, profile, session, stats), matchWrap(context));

        if (!session.samples().isEmpty()) {
            root.addView(sectionTitle(context, "心率曲线"), matchWrap(context));
            HeartRateChartView chart = new HeartRateChartView(context);
            chart.setData(session.samples(), maxHr);
            root.addView(chart, matchWrap(context));

            root.addView(sectionTitle(context, "心率区间"), matchWrap(context));
            root.addView(zoneBlock(context, stats, session.durationMillis()), matchWrap(context));
        }

        if (WorkoutSession.TYPE_STRENGTH.equals(session.workoutType) && !session.strengthSets().isEmpty()) {
            root.addView(sectionTitle(context, "力量训练组数"), matchWrap(context));
            root.addView(strengthSetsBlock(context, session.strengthSets()), matchWrap(context));
        }

        return root;
    }

    private static View summaryBlock(
            Context context,
            UserProfile profile,
            WorkoutSession session,
            WorkoutStats stats) {
        LinearLayout block = vertical(context);
        block.setPadding(dp(context, 12), dp(context, 4), dp(context, 12), dp(context, 8));

        String type = WorkoutSession.TYPE_STRENGTH.equals(session.workoutType) ? "力量训练" : "有氧训练";
        String end = session.endMillis > 0L
                ? formatDateTime(session.endMillis)
                : "进行中";
        addRow(block, context, "开始时间", formatDateTime(session.startMillis));
        addRow(block, context, "结束时间", end);
        addRow(block, context, "训练类型", type);
        addRow(block, context, "运动时长", formatDuration(session.durationMillis()));
        addRow(block, context, "估算消耗", String.format(Locale.US, "%.1f kcal", stats.calories));

        if (!session.samples().isEmpty()) {
            addRow(block, context, "平均心率", stats.avgBpm + " bpm");
            addRow(block, context, "最高/最低", stats.maxBpm + " / " + stats.minBpm + " bpm");
            addRow(block, context, "采样数量", String.valueOf(stats.sampleCount));
        }

        if (WorkoutSession.TYPE_STRENGTH.equals(session.workoutType)) {
            addRow(block, context, "力量组数", String.valueOf(session.strengthSets().size()));
        }

        if (profile != null) {
            addRow(block, context, "运动人员",
                    profile.name + "，" + profile.heightCm + " cm，"
                            + String.format(Locale.US, "%.1f", profile.weightKg) + " kg，"
                            + profile.age + " 岁");
        }
        return block;
    }

    private static View zoneBlock(Context context, WorkoutStats stats, long totalMillis) {
        LinearLayout block = vertical(context);
        block.setPadding(dp(context, 12), dp(context, 4), dp(context, 12), dp(context, 12));
        long total = Math.max(1L, totalMillis);
        int barMaxWidth = dp(context, 200);
        for (int i = 0; i < WorkoutStats.ZONE_LABELS.length; i++) {
            LinearLayout row = new LinearLayout(context);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(0, dp(context, 4), 0, dp(context, 4));

            View bar = new View(context);
            float ratio = stats.zoneMillis[i] / (float) total;
            int barWidth = Math.max(dp(context, 4), Math.round(barMaxWidth * ratio));
            LinearLayout.LayoutParams barParams = new LinearLayout.LayoutParams(barWidth, dp(context, 14));
            bar.setLayoutParams(barParams);
            bar.setBackgroundColor(WorkoutStats.ZONE_COLORS[i]);
            row.addView(bar);

            TextView label = bodyText(context, WorkoutStats.ZONE_LABELS[i] + "  " + formatDuration(stats.zoneMillis[i]));
            LinearLayout.LayoutParams labelParams = new LinearLayout.LayoutParams(
                    0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
            labelParams.setMarginStart(dp(context, 10));
            label.setLayoutParams(labelParams);
            row.addView(label);
            block.addView(row, matchWrap(context));
        }
        return block;
    }

    private static View strengthSetsBlock(Context context, List<StrengthSet> sets) {
        LinearLayout block = vertical(context);
        block.setPadding(dp(context, 12), dp(context, 4), dp(context, 12), dp(context, 12));
        int index = 1;
        for (StrengthSet set : sets) {
            String line = index++ + ". " + set.exerciseName + "  "
                    + set.displayWeight() + " x" + set.reps + "  "
                    + formatDateTime(set.timestampMillis);
            block.addView(bodyText(context, line), matchWrap(context));
        }
        return block;
    }

    private static TextView sectionTitle(Context context, String title) {
        TextView view = new TextView(context);
        view.setText(title);
        view.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f);
        view.setTextColor(Color.rgb(21, 101, 192));
        view.setPadding(dp(context, 12), dp(context, 14), dp(context, 12), dp(context, 4));
        return view;
    }

    private static void addRow(LinearLayout parent, Context context, String label, String value) {
        TextView row = bodyText(context, label + ": " + value);
        parent.addView(row, matchWrap(context));
    }

    private static TextView bodyText(Context context, String text) {
        TextView view = new TextView(context);
        view.setText(text);
        view.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f);
        view.setTextColor(Color.BLACK);
        view.setPadding(dp(context, 12), dp(context, 3), dp(context, 12), dp(context, 3));
        return view;
    }

    private static LinearLayout vertical(Context context) {
        LinearLayout layout = new LinearLayout(context);
        layout.setOrientation(LinearLayout.VERTICAL);
        return layout;
    }

    private static LinearLayout.LayoutParams matchWrap(Context context) {
        return new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
    }

    private static int dp(Context context, int value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }

    private static String formatDateTime(long millis) {
        return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(new Date(millis));
    }

    private static String formatDuration(long millis) {
        long seconds = Math.max(0L, millis / 1000L);
        long hours = seconds / 3600L;
        long minutes = (seconds % 3600L) / 60L;
        long secs = seconds % 60L;
        if (hours > 0) {
            return String.format(Locale.US, "%d:%02d:%02d", hours, minutes, secs);
        }
        return String.format(Locale.US, "%02d:%02d", minutes, secs);
    }
}
