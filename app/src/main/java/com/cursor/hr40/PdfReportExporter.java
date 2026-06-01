package com.cursor.hr40;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.pdf.PdfDocument;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.MediaStore;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public final class PdfReportExporter {
    private static final int PAGE_WIDTH = 595;
    private static final int PAGE_HEIGHT = 842;
    private static final int MARGIN = 42;
    private static final int[] ZONE_COLORS = {
            Color.rgb(100, 181, 246),
            Color.rgb(102, 187, 106),
            Color.rgb(255, 202, 40),
            Color.rgb(251, 140, 0),
            Color.rgb(229, 57, 53)
    };

    private PdfReportExporter() {
    }

    public static Uri export(Context context, UserProfile profile, WorkoutSession session) throws IOException {
        String fileName = "hr40_workout_" + new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US)
                .format(new Date(session.startMillis)) + ".pdf";
        PdfDocument document = createDocument(profile, session);
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                return writeToDownloads(context, document, fileName);
            }
            File file = new File(context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS), fileName);
            try (OutputStream output = new FileOutputStream(file)) {
                document.writeTo(output);
            }
            return Uri.fromFile(file);
        } finally {
            document.close();
        }
    }

    private static Uri writeToDownloads(Context context, PdfDocument document, String fileName) throws IOException {
        ContentResolver resolver = context.getContentResolver();
        ContentValues values = new ContentValues();
        values.put(MediaStore.MediaColumns.DISPLAY_NAME, fileName);
        values.put(MediaStore.MediaColumns.MIME_TYPE, "application/pdf");
        values.put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/HR40");
        values.put(MediaStore.MediaColumns.IS_PENDING, 1);

        Uri uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values);
        if (uri == null) {
            throw new IOException("Unable to create PDF in Downloads");
        }
        try (OutputStream output = resolver.openOutputStream(uri)) {
            if (output == null) {
                throw new IOException("Unable to open PDF output stream");
            }
            document.writeTo(output);
        }
        values.clear();
        values.put(MediaStore.MediaColumns.IS_PENDING, 0);
        resolver.update(uri, values, null, null);
        return uri;
    }

    private static PdfDocument createDocument(UserProfile profile, WorkoutSession session) {
        PdfDocument document = new PdfDocument();
        PdfDocument.PageInfo pageInfo = new PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, 1).create();
        PdfDocument.Page page = document.startPage(pageInfo);
        Canvas canvas = page.getCanvas();
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        WorkoutStats stats = WorkoutStats.calculate(profile, session);

        int y = MARGIN;
        paint.setColor(Color.rgb(18, 52, 86));
        paint.setTextSize(22);
        paint.setFakeBoldText(true);
        canvas.drawText("HR40 运动心率报告", MARGIN, y, paint);

        y += 28;
        paint.setFakeBoldText(false);
        paint.setTextSize(10);
        paint.setColor(Color.DKGRAY);
        canvas.drawText("生成时间: " + dateTime(System.currentTimeMillis()), MARGIN, y, paint);

        y += 30;
        drawSectionTitle(canvas, paint, "运动概览", y);
        y += 18;
        y = drawSummary(canvas, paint, profile, session, stats, y);

        y += 18;
        drawSectionTitle(canvas, paint, "心率曲线", y);
        y += 12;
        int maxHeartRate = profile == null ? 190 : profile.estimatedMaxHeartRate();
        drawHeartRateChart(canvas, paint, session.samples(), new RectF(MARGIN, y, PAGE_WIDTH - MARGIN, y + 210), maxHeartRate);

        y += 240;
        drawSectionTitle(canvas, paint, "心率区间", y);
        y += 18;
        drawZones(canvas, paint, stats, session.durationMillis(), y);

        document.finishPage(page);
        return document;
    }

    private static int drawSummary(
            Canvas canvas,
            Paint paint,
            UserProfile profile,
            WorkoutSession session,
            WorkoutStats stats,
            int startY) {
        paint.setTextSize(11);
        paint.setColor(Color.BLACK);
        int y = startY;
        drawText(canvas, paint, "运动开始", dateTime(session.startMillis), MARGIN, y);
        drawText(canvas, paint, "运动结束", session.endMillis > 0L ? dateTime(session.endMillis) : "进行中", 310, y);
        y += 18;
        drawText(canvas, paint, "运动时长", duration(session.durationMillis()), MARGIN, y);
        drawText(canvas, paint, "采样数量", String.valueOf(stats.sampleCount), 310, y);
        y += 18;
        drawText(canvas, paint, "平均心率", stats.avgBpm + " bpm", MARGIN, y);
        drawText(canvas, paint, "最高/最低", stats.maxBpm + " / " + stats.minBpm + " bpm", 310, y);
        y += 18;
        drawText(canvas, paint, "估算消耗", String.format(Locale.US, "%.1f kcal", stats.calories), MARGIN, y);
        String profileText = profile == null
                ? "未填写"
                : profile.name + "，" + profile.heightCm + " cm，" + profile.weightKg + " kg，" + profile.age + " 岁";
        drawText(canvas, paint, "运动人员", profileText, 310, y);
        return y + 18;
    }

    private static void drawHeartRateChart(
            Canvas canvas,
            Paint paint,
            List<HeartRateSample> samples,
            RectF rect,
            int maxHeartRate) {
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(1f);
        paint.setColor(Color.LTGRAY);
        canvas.drawRect(rect, paint);

        paint.setStyle(Paint.Style.FILL);
        paint.setTextSize(9);
        paint.setColor(Color.GRAY);
        canvas.drawText("bpm", rect.left, rect.top - 4, paint);

        if (samples.size() < 2) {
            paint.setTextSize(12);
            paint.setColor(Color.DKGRAY);
            canvas.drawText("运动记录中没有足够的心率采样点", rect.left + 20, rect.centerY(), paint);
            return;
        }

        int sampleMin = Integer.MAX_VALUE;
        int sampleMax = Integer.MIN_VALUE;
        for (HeartRateSample sample : samples) {
            sampleMin = Math.min(sampleMin, sample.bpm);
            sampleMax = Math.max(sampleMax, sample.bpm);
        }

        int rawMin = Math.max(40, sampleMin - 8);
        int rawMax = Math.min(220, sampleMax + 8);
        if (rawMax <= rawMin) {
            rawMax = rawMin + 20;
        }
        int bpmStep = niceBpmStep((rawMax - rawMin) / 5.0);
        int axisMin = Math.max(0, (rawMin / bpmStep) * bpmStep);
        int axisMax = ((rawMax + bpmStep - 1) / bpmStep) * bpmStep;
        if (axisMax <= axisMin) {
            axisMax = axisMin + bpmStep;
        }

        long start = samples.get(0).timestampMillis;
        long end = samples.get(samples.size() - 1).timestampMillis;
        long span = Math.max(1L, end - start);
        long timeStep = niceTimeStep(span / 5L);

        drawChartGrid(canvas, paint, rect, axisMin, axisMax, bpmStep, span, timeStep);

        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(2.4f);
        float lastX = 0f;
        float lastY = 0f;
        int lastBpm = 0;
        for (int i = 0; i < samples.size(); i++) {
            HeartRateSample sample = samples.get(i);
            float x = rect.left + ((sample.timestampMillis - start) / (float) span) * rect.width();
            float normalized = (sample.bpm - axisMin) / (float) (axisMax - axisMin);
            float y = rect.bottom - (normalized * rect.height());
            if (i > 0) {
                int segmentBpm = Math.round((lastBpm + sample.bpm) / 2f);
                paint.setColor(zoneColorForBpm(segmentBpm, maxHeartRate));
                canvas.drawLine(lastX, lastY, x, y, paint);
            }
            lastX = x;
            lastY = y;
            lastBpm = sample.bpm;
        }
    }

    private static void drawChartGrid(
            Canvas canvas,
            Paint paint,
            RectF rect,
            int axisMin,
            int axisMax,
            int bpmStep,
            long spanMillis,
            long timeStepMillis) {
        paint.setStrokeWidth(0.7f);
        paint.setTextSize(8.5f);
        for (int bpm = axisMin; bpm <= axisMax; bpm += bpmStep) {
            float normalized = (bpm - axisMin) / (float) (axisMax - axisMin);
            float y = rect.bottom - (normalized * rect.height());
            paint.setStyle(Paint.Style.STROKE);
            paint.setColor(Color.rgb(224, 224, 224));
            canvas.drawLine(rect.left, y, rect.right, y, paint);
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(Color.DKGRAY);
            canvas.drawText(String.valueOf(bpm), rect.left - 30, y + 3, paint);
        }

        long lastLabelMillis = -1L;
        for (long elapsed = 0L; elapsed <= spanMillis; elapsed += timeStepMillis) {
            drawTimeTick(canvas, paint, rect, elapsed, spanMillis);
            lastLabelMillis = elapsed;
        }
        if (lastLabelMillis < 0L || spanMillis - lastLabelMillis > timeStepMillis / 2L) {
            drawTimeTick(canvas, paint, rect, spanMillis, spanMillis);
        }
    }

    private static void drawTimeTick(Canvas canvas, Paint paint, RectF rect, long elapsedMillis, long spanMillis) {
        float x = rect.left + (elapsedMillis / (float) Math.max(1L, spanMillis)) * rect.width();
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(0.7f);
        paint.setColor(Color.rgb(232, 232, 232));
        canvas.drawLine(x, rect.top, x, rect.bottom, paint);
        paint.setStyle(Paint.Style.FILL);
        paint.setTextSize(8.5f);
        paint.setColor(Color.DKGRAY);
        canvas.drawText(duration(elapsedMillis), Math.min(x, rect.right - 34), rect.bottom + 14, paint);
    }

    private static int niceBpmStep(double roughStep) {
        int[] steps = {5, 10, 15, 20, 25, 30, 40, 50};
        for (int step : steps) {
            if (roughStep <= step) {
                return step;
            }
        }
        return 60;
    }

    private static long niceTimeStep(long roughMillis) {
        long[] steps = {
                5_000L,
                10_000L,
                15_000L,
                30_000L,
                60_000L,
                120_000L,
                300_000L,
                600_000L,
                900_000L,
                1_800_000L,
                3_600_000L
        };
        for (long step : steps) {
            if (roughMillis <= step) {
                return step;
            }
        }
        long hours = (long) Math.ceil(roughMillis / 3_600_000.0);
        return Math.max(1L, hours) * 3_600_000L;
    }

    private static int zoneColorForBpm(int bpm, int maxHeartRate) {
        double ratio = bpm / (double) Math.max(1, maxHeartRate);
        if (ratio < 0.60) {
            return ZONE_COLORS[0];
        }
        if (ratio < 0.70) {
            return ZONE_COLORS[1];
        }
        if (ratio < 0.80) {
            return ZONE_COLORS[2];
        }
        if (ratio < 0.90) {
            return ZONE_COLORS[3];
        }
        return ZONE_COLORS[4];
    }

    private static void drawZones(Canvas canvas, Paint paint, WorkoutStats stats, long totalMillis, int startY) {
        int y = startY;
        long total = Math.max(1L, totalMillis);
        for (int i = 0; i < WorkoutStats.ZONE_LABELS.length; i++) {
            float ratio = stats.zoneMillis[i] / (float) total;
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(ZONE_COLORS[i]);
            canvas.drawRect(MARGIN, y - 10, MARGIN + (260 * ratio), y + 2, paint);
            paint.setColor(Color.BLACK);
            paint.setTextSize(10);
            canvas.drawText(WorkoutStats.ZONE_LABELS[i], MARGIN + 275, y, paint);
            canvas.drawText(duration(stats.zoneMillis[i]), MARGIN + 390, y, paint);
            y += 20;
        }
    }

    private static void drawSectionTitle(Canvas canvas, Paint paint, String title, int y) {
        paint.setStyle(Paint.Style.FILL);
        paint.setFakeBoldText(true);
        paint.setTextSize(14);
        paint.setColor(Color.rgb(21, 101, 192));
        canvas.drawText(title, MARGIN, y, paint);
        paint.setFakeBoldText(false);
    }

    private static void drawText(Canvas canvas, Paint paint, String label, String value, int x, int y) {
        paint.setFakeBoldText(true);
        canvas.drawText(label + ": ", x, y, paint);
        paint.setFakeBoldText(false);
        canvas.drawText(value, x + 58, y, paint);
    }

    private static String dateTime(long millis) {
        return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.CHINA).format(new Date(millis));
    }

    private static String duration(long millis) {
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
