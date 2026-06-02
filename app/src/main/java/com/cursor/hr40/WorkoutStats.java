package com.cursor.hr40;

import java.util.List;

public final class WorkoutStats {
    public static final String[] ZONE_LABELS = {
            "恢复区 <60%",
            "燃脂区 60-70%",
            "有氧区 70-80%",
            "阈值区 80-90%",
            "极限区 >=90%"
    };

    public final int minBpm;
    public final int maxBpm;
    public final int avgBpm;
    public final double calories;
    public final long[] zoneMillis;
    public final int sampleCount;

    private WorkoutStats(int minBpm, int maxBpm, int avgBpm, double calories, long[] zoneMillis, int sampleCount) {
        this.minBpm = minBpm;
        this.maxBpm = maxBpm;
        this.avgBpm = avgBpm;
        this.calories = calories;
        this.zoneMillis = zoneMillis;
        this.sampleCount = sampleCount;
    }

    public static WorkoutStats calculate(UserProfile profile, WorkoutSession session) {
        List<HeartRateSample> samples = session.samples();
        if (samples.isEmpty()) {
            return new WorkoutStats(0, 0, 0, 0.0, new long[ZONE_LABELS.length], 0);
        }
        int min = Integer.MAX_VALUE;
        int max = Integer.MIN_VALUE;
        long sum = 0L;
        for (HeartRateSample sample : samples) {
            min = Math.min(min, sample.bpm);
            max = Math.max(max, sample.bpm);
            sum += sample.bpm;
        }

        long[] zones = new long[ZONE_LABELS.length];
        int maxHr = profile == null ? 190 : profile.estimatedMaxHeartRate();
        for (int i = 0; i < samples.size() - 1; i++) {
            HeartRateSample current = samples.get(i);
            HeartRateSample next = samples.get(i + 1);
            long delta = Math.max(0L, next.timestampMillis - current.timestampMillis);
            zones[zoneIndex(current.bpm, maxHr)] += delta;
        }

        return new WorkoutStats(
                min,
                max,
                (int) Math.round(sum / (double) samples.size()),
                EnergyEstimator.estimateCalories(profile, session),
                zones,
                samples.size());
    }

    public static final int[] ZONE_COLORS = {
            0xFF64B5F6,
            0xFF66BB6A,
            0xFFFFCA28,
            0xFFFB8C00,
            0xFFE53935
    };

    public static int zoneIndex(int bpm, int maxHr) {
        double ratio = bpm / (double) maxHr;
        if (ratio < 0.60) {
            return 0;
        }
        if (ratio < 0.70) {
            return 1;
        }
        if (ratio < 0.80) {
            return 2;
        }
        if (ratio < 0.90) {
            return 3;
        }
        return 4;
    }

    public static int zoneColor(int bpm, int maxHr) {
        return ZONE_COLORS[zoneIndex(bpm, maxHr)];
    }
}
