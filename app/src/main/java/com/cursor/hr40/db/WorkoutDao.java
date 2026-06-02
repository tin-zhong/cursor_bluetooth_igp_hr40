package com.cursor.hr40.db;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Transaction;

import java.util.List;

@Dao
public interface WorkoutDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void upsertWorkout(WorkoutRecordEntity workout);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertHeartRateSamples(List<HeartRateSampleEntity> samples);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertStrengthSets(List<StrengthSetEntity> sets);

    @Query("DELETE FROM heart_rate_samples WHERE sessionId = :sessionId")
    void deleteHeartRateSamplesBySession(String sessionId);

    @Query("DELETE FROM strength_sets WHERE sessionId = :sessionId")
    void deleteStrengthSetsBySession(String sessionId);

    @Transaction
    @Query("SELECT * FROM workout_records ORDER BY startMillis DESC")
    List<WorkoutWithDetails> loadAllWorkoutsWithDetails();
}
