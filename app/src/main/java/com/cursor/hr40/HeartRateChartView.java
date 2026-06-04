package com.cursor.hr40;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;

import java.util.List;
import java.util.Locale;

/**
 * In-app heart rate timeline chart (same visual language as PDF export).
 */
public final class HeartRateChartView extends View {
    private List<HeartRateSample> samples = List.of();
    private int maxHeartRate = 190;

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);

    public HeartRateChartView(Context context) {
        super(context);
    }

    public HeartRateChartView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public void setData(List<HeartRateSample> samples, int maxHeartRate) {
        this.samples = samples == null ? List.of() : samples;
        this.maxHeartRate = Math.max(1, maxHeartRate);
        invalidate();
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int width = MeasureSpec.getSize(widthMeasureSpec);
        int height = (int) (width * 0.42f);
        height = Math.max(height, dp(140));
        setMeasuredDimension(width, height);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        RectF rect = new RectF(dp(36), dp(8), getWidth() - dp(8), getHeight() - dp(28));
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(1f);
        paint.setColor(Color.LTGRAY);
        canvas.drawRect(rect, paint);

        if (samples.size() < 2) {
            paint.setStyle(Paint.Style.FILL);
            paint.setTextSize(sp(13));
            paint.setColor(Color.DKGRAY);
            canvas.drawText("心率采样不足，无法绘制曲线", rect.left, rect.centerY(), paint);
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
        drawChartGrid(canvas, rect, axisMin, axisMax, bpmStep, span, timeStep);

        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(dpF(2.4f));
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

    private void drawChartGrid(
            Canvas canvas,
            RectF rect,
            int axisMin,
            int axisMax,
            int bpmStep,
            long spanMillis,
            long timeStepMillis) {
        paint.setStrokeWidth(0.7f);
        paint.setTextSize(sp(9));
        for (int bpm = axisMin; bpm <= axisMax; bpm += bpmStep) {
            float normalized = (bpm - axisMin) / (float) (axisMax - axisMin);
            float y = rect.bottom - (normalized * rect.height());
            paint.setStyle(Paint.Style.STROKE);
            paint.setColor(Color.rgb(224, 224, 224));
            canvas.drawLine(rect.left, y, rect.right, y, paint);
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(Color.DKGRAY);
            canvas.drawText(String.valueOf(bpm), dp(4), y + dp(3), paint);
        }

        long lastLabelMillis = -1L;
        for (long elapsed = 0L; elapsed <= spanMillis; elapsed += timeStepMillis) {
            drawTimeTick(canvas, rect, elapsed, spanMillis);
            lastLabelMillis = elapsed;
        }
        if (lastLabelMillis < 0L || spanMillis - lastLabelMillis > timeStepMillis / 2L) {
            drawTimeTick(canvas, rect, spanMillis, spanMillis);
        }
    }

    private void drawTimeTick(Canvas canvas, RectF rect, long elapsedMillis, long spanMillis) {
        float x = rect.left + (elapsedMillis / (float) Math.max(1L, spanMillis)) * rect.width();
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(0.7f);
        paint.setColor(Color.rgb(232, 232, 232));
        canvas.drawLine(x, rect.top, x, rect.bottom, paint);
        paint.setStyle(Paint.Style.FILL);
        paint.setTextSize(sp(9));
        paint.setColor(Color.DKGRAY);
        canvas.drawText(formatDuration(elapsedMillis), Math.min(x, rect.right - dp(34)), rect.bottom + dp(14), paint);
    }

    private static int zoneColorForBpm(int bpm, int maxHeartRate) {
        return WorkoutStats.zoneColor(bpm, maxHeartRate);
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
                5_000L, 10_000L, 15_000L, 30_000L, 60_000L, 120_000L,
                300_000L, 600_000L, 900_000L, 1_800_000L, 3_600_000L
        };
        for (long step : steps) {
            if (roughMillis <= step) {
                return step;
            }
        }
        long hours = (long) Math.ceil(roughMillis / 3_600_000.0);
        return Math.max(1L, hours) * 3_600_000L;
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

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private float dpF(float value) {
        return value * getResources().getDisplayMetrics().density;
    }

    private float sp(float value) {
        return value * getResources().getDisplayMetrics().scaledDensity;
    }
}
