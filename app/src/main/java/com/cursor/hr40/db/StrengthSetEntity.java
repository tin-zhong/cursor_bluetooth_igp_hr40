package com.cursor.hr40.db;

import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Index;
import androidx.room.PrimaryKey;

@Entity(
        tableName = "strength_sets",
        foreignKeys = @ForeignKey(
                entity = WorkoutRecordEntity.class,
                parentColumns = "id",
                childColumns = "sessionId",
                onDelete = ForeignKey.CASCADE),
        indices = {@Index("sessionId")})
public class StrengthSetEntity {
    @PrimaryKey(autoGenerate = true)
    public long rowId;

    public String sessionId;
    public String exerciseName;
    public double weight;
    public String weightUnit;
    public int reps;
    public long timestampMillis;
}
