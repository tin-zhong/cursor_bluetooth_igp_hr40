package com.cursor.hr40.db;

import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Index;
import androidx.room.PrimaryKey;

@Entity(
        tableName = "heart_rate_samples",
        foreignKeys = @ForeignKey(
                entity = WorkoutRecordEntity.class,
                parentColumns = "id",
                childColumns = "sessionId",
                onDelete = ForeignKey.CASCADE),
        indices = {@Index("sessionId")})
public class HeartRateSampleEntity {
    @PrimaryKey(autoGenerate = true)
    public long rowId;

    public String sessionId;
    public long timestampMillis;
    public int bpm;
    public boolean contactSupported;
    public boolean contactDetected;
    public Integer energyExpendedKj;
    public int rrIntervalCount;
}
