package com.cursor.hr40;

import java.util.List;

public final class EnergyEstimator {
    private EnergyEstimator() {
    }

    public static double estimateCalories(UserProfile profile, WorkoutSession session) {
        List<HeartRateSample> samples = session.samples();
        if (profile == null || samples.size() < 2) {
            return 0.0;
        }
        double calories = 0.0;
        for (int i = 0; i < samples.size() - 1; i++) {
            HeartRateSample current = samples.get(i);
            HeartRateSample next = samples.get(i + 1);
            long deltaMillis = Math.max(0L, next.timestampMillis - current.timestampMillis);
            if (deltaMillis == 0L) {
                continue;
            }
            double minutes = deltaMillis / 60000.0;
            double bpm = (current.bpm + next.bpm) / 2.0;
            calories += Math.max(0.0, caloriesPerMinute(profile, bpm)) * minutes;
        }
        return calories;
    }

    private static double caloriesPerMinute(UserProfile profile, double bpm) {
        if (UserProfile.SEX_FEMALE.equals(profile.sex)) {
            return (-20.4022 + (0.4472 * bpm) - (0.1263 * profile.weightKg) + (0.074 * profile.age)) / 4.184;
        }
        return (-55.0969 + (0.6309 * bpm) + (0.1988 * profile.weightKg) + (0.2017 * profile.age)) / 4.184;
    }
}
