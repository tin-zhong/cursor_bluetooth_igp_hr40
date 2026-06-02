package com.cursor.hr40.db;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "workout_records")
public class WorkoutRecordEntity {
    @PrimaryKey
    @NonNull
    public String id;

    public long startMillis;
    public long endMillis;
    @NonNull
    public String workoutType;
}
