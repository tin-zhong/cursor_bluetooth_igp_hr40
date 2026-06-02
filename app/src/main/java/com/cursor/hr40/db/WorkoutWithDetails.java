package com.cursor.hr40.db;

import androidx.room.Embedded;
import androidx.room.Relation;

import java.util.List;

public class WorkoutWithDetails {
    @Embedded
    public WorkoutRecordEntity workout;

    @Relation(parentColumn = "id", entityColumn = "sessionId", entity = HeartRateSampleEntity.class)
    public List<HeartRateSampleEntity> samples;

    @Relation(parentColumn = "id", entityColumn = "sessionId", entity = StrengthSetEntity.class)
    public List<StrengthSetEntity> strengthSets;
}
