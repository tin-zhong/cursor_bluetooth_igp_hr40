package com.cursor.hr40;

import android.content.Context;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public final class WorkoutSessionLabels {
    private WorkoutSessionLabels() {
    }

    public static List<WorkoutSession> collectExportableSessions(Context context) {
        List<WorkoutSession> sessions = WorkoutRepository.loadAll(context);
        List<WorkoutSession> exportableSessions = new ArrayList<>();
        for (WorkoutSession session : sessions) {
            if (session.samples().isEmpty() && session.strengthSets().isEmpty()) {
                continue;
            }
            exportableSessions.add(session);
        }
        return exportableSessions;
    }

    public static WorkoutSession findById(Context context, String sessionId) {
        if (sessionId == null || sessionId.isEmpty()) {
            return null;
        }
        for (WorkoutSession session : WorkoutRepository.loadAll(context)) {
            if (sessionId.equals(session.id)) {
                return session;
            }
        }
        return null;
    }

    public static String formatSessionLabel(WorkoutSession session) {
        String type = WorkoutSession.TYPE_STRENGTH.equals(session.workoutType) ? "力量" : "有氧";
        String start = new SimpleDateFormat("MM-dd HH:mm", Locale.getDefault())
                .format(new Date(session.startMillis));
        return start + " | " + type + " | 时长 " + formatDuration(session.durationMillis());
    }

    public static String formatDuration(long millis) {
        long seconds = Math.max(0L, millis / 1000L);
        long hours = seconds / 3600L;
        long minutes = (seconds % 3600L) / 60L;
        long secs = seconds % 60L;
        if (hours > 0L) {
            return String.format(Locale.US, "%d:%02d:%02d", hours, minutes, secs);
        }
        return String.format(Locale.US, "%02d:%02d", minutes, secs);
    }
}
