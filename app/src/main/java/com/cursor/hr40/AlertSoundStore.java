package com.cursor.hr40;

import android.content.Context;
import android.content.SharedPreferences;
import android.media.RingtoneManager;
import android.net.Uri;
import android.text.TextUtils;

/**
 * Persists the user-selected ringtone used for countdown / rest-time prompts.
 * When no ringtone is chosen the timer falls back to its built-in beep.
 */
public final class AlertSoundStore {
    private static final String PREFS_NAME = "hr40_alert_sound_prefs";
    private static final String KEY_SOUND_URI = "alert_sound_uri";

    private AlertSoundStore() {
    }

    /** Stored alert sound URI, or {@code null} when the default beep should be used. */
    public static Uri getUri(Context context) {
        String stored = prefs(context).getString(KEY_SOUND_URI, null);
        if (TextUtils.isEmpty(stored)) {
            return null;
        }
        return Uri.parse(stored);
    }

    /** Save the chosen URI; pass {@code null} to clear and restore the default beep. */
    public static void save(Context context, Uri uri) {
        SharedPreferences.Editor editor = prefs(context).edit();
        if (uri == null) {
            editor.remove(KEY_SOUND_URI);
        } else {
            editor.putString(KEY_SOUND_URI, uri.toString());
        }
        editor.apply();
    }

    /** Human-readable name of the current sound, falling back to a default label. */
    public static String currentTitle(Context context) {
        Uri uri = getUri(context);
        if (uri == null) {
            return "默认铃声";
        }
        try {
            android.media.Ringtone ringtone = RingtoneManager.getRingtone(context, uri);
            if (ringtone != null) {
                String title = ringtone.getTitle(context);
                if (!TextUtils.isEmpty(title)) {
                    return title;
                }
            }
        } catch (RuntimeException ignored) {
            // Fall through to the default label when the URI is no longer resolvable.
        }
        return "自定义铃声";
    }

    private static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }
}
