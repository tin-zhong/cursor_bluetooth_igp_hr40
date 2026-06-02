package com.cursor.hr40;

import java.util.List;

/**
 * Keytel (2005) heart-rate based energy expenditure estimation.
 * Calories are accumulated per sampling interval using the average heart rate
 * of adjacent samples. Intervals with average HR below {@link #MIN_HR_FOR_CALORIES}
 * are ignored to reduce resting/noise contributions.
 */
public final class EnergyEstimator {
    public static final int MIN_HR_FOR_CALORIES = 60;
    private static final double KCAL_PER_KJ = 4.184;

    private EnergyEstimator() {
    }

    public static double estimateCalories(UserProfile profile, WorkoutSession session) {
        List<HeartRateSample> samples = session.samples();
        if (profile == null || samples.size() < 2) {
            return 0.0;
        }
        double calories = 0.0;
        for (int i = 0; i < samples.size() - 1; i++) {
            calories += caloriesForSegment(profile, samples.get(i), samples.get(i + 1));
        }
        return calories;
    }

    public static double caloriesForSegment(UserProfile profile, HeartRateSample current, HeartRateSample next) {
        if (profile == null || current == null || next == null) {
            return 0.0;
        }
        long deltaMillis = Math.max(0L, next.timestampMillis - current.timestampMillis);
        if (deltaMillis == 0L) {
            return 0.0;
        }
        double averageBpm = (current.bpm + next.bpm) / 2.0;
        if (averageBpm < MIN_HR_FOR_CALORIES) {
            return 0.0;
        }
        double minutes = deltaMillis / 60000.0;
        return caloriesPerMinute(profile, averageBpm) * minutes;
    }

    public static double caloriesPerMinute(UserProfile profile, double bpm) {
        double kilojoulesPerMinute;
        if (UserProfile.SEX_FEMALE.equals(profile.sex)) {
            kilojoulesPerMinute = -20.4022 + (0.4472 * bpm) - (0.1263 * profile.weightKg) + (0.074 * profile.age);
        } else {
            kilojoulesPerMinute = -55.0969 + (0.6309 * bpm) + (0.1988 * profile.weightKg) + (0.2017 * profile.age);
        }
        double caloriesPerMinute = kilojoulesPerMinute / KCAL_PER_KJ;
        return Math.max(0.0, caloriesPerMinute);
    }
}
