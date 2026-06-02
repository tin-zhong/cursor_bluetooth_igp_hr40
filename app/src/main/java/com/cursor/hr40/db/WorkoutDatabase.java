package com.cursor.hr40.db;

import android.content.Context;

import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;

@Database(
        entities = {
                WorkoutRecordEntity.class,
                HeartRateSampleEntity.class,
                StrengthSetEntity.class
        },
        version = 1,
        exportSchema = false)
public abstract class WorkoutDatabase extends RoomDatabase {
    private static volatile WorkoutDatabase INSTANCE;

    public abstract WorkoutDao workoutDao();

    public static WorkoutDatabase getInstance(Context context) {
        if (INSTANCE == null) {
            synchronized (WorkoutDatabase.class) {
                if (INSTANCE == null) {
                    INSTANCE = Room.databaseBuilder(
                                    context.getApplicationContext(),
                                    WorkoutDatabase.class,
                                    "hr40_workouts.db")
                            .fallbackToDestructiveMigration()
                            .allowMainThreadQueries()
                            .build();
                }
            }
        }
        return INSTANCE;
    }
}
