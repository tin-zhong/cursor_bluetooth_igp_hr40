package com.cursor.hr40.db;

import com.cursor.hr40.HeartRateSample;
import com.cursor.hr40.StrengthSet;
import com.cursor.hr40.WorkoutSession;

import java.util.ArrayList;
import java.util.List;

public final class WorkoutRecordMapper {
    private WorkoutRecordMapper() {
    }

    public static WorkoutSession toSession(WorkoutWithDetails details) {
        if (details == null || details.workout == null) {
            return null;
        }
        WorkoutRecordEntity workout = details.workout;
        WorkoutSession session = new WorkoutSession(
                workout.id,
                workout.startMillis,
                workout.endMillis,
                workout.workoutType);
        if (details.samples != null) {
            for (HeartRateSampleEntity entity : details.samples) {
                session.addSample(toSample(entity));
            }
        }
        if (details.strengthSets != null) {
            for (StrengthSetEntity entity : details.strengthSets) {
                session.addStrengthSet(toStrengthSet(entity));
            }
        }
        return session;
    }

    public static HeartRateSampleEntity toSampleEntity(String sessionId, HeartRateSample sample) {
        HeartRateSampleEntity entity = new HeartRateSampleEntity();
        entity.sessionId = sessionId;
        entity.timestampMillis = sample.timestampMillis;
        entity.bpm = sample.bpm;
        entity.contactSupported = sample.contactSupported;
        entity.contactDetected = sample.contactDetected;
        entity.energyExpendedKj = sample.energyExpendedKj;
        entity.rrIntervalCount = sample.rrIntervalCount;
        return entity;
    }

    public static StrengthSetEntity toStrengthSetEntity(String sessionId, StrengthSet set) {
        StrengthSetEntity entity = new StrengthSetEntity();
        entity.sessionId = sessionId;
        entity.exerciseName = set.exerciseName;
        entity.weight = set.weight;
        entity.weightUnit = set.weightUnit;
        entity.reps = set.reps;
        entity.timestampMillis = set.timestampMillis;
        return entity;
    }

    public static List<HeartRateSampleEntity> toSampleEntities(String sessionId, List<HeartRateSample> samples) {
        List<HeartRateSampleEntity> entities = new ArrayList<>();
        for (HeartRateSample sample : samples) {
            entities.add(toSampleEntity(sessionId, sample));
        }
        return entities;
    }

    public static List<StrengthSetEntity> toStrengthSetEntities(String sessionId, List<StrengthSet> sets) {
        List<StrengthSetEntity> entities = new ArrayList<>();
        for (StrengthSet set : sets) {
            entities.add(toStrengthSetEntity(sessionId, set));
        }
        return entities;
    }

    private static HeartRateSample toSample(HeartRateSampleEntity entity) {
        return new HeartRateSample(
                entity.timestampMillis,
                entity.bpm,
                entity.contactSupported,
                entity.contactDetected,
                entity.energyExpendedKj,
                entity.rrIntervalCount);
    }

    private static StrengthSet toStrengthSet(StrengthSetEntity entity) {
        return new StrengthSet(
                entity.exerciseName,
                entity.weight,
                entity.weightUnit,
                entity.reps,
                entity.timestampMillis);
    }
}
