package com.cursor.hr40;

import android.content.Context;
import android.media.AudioManager;
import android.media.ToneGenerator;
import android.os.Handler;
import android.os.Looper;
import android.speech.tts.TextToSpeech;

import java.util.Locale;

/**
 * Plays a short alarm beep plus a Chinese voice prompt, used for situational alerts
 * such as the heart-rate band disconnecting during a workout.
 */
public final class AlertPlayer {
    private final Context appContext;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private TextToSpeech tts;
    private boolean ttsReady;
    private ToneGenerator toneGenerator;
    private int beepCount;

    private final Runnable beepRunnable = new Runnable() {
        @Override
        public void run() {
            if (beepCount <= 0) {
                return;
            }
            playSingleBeep();
            beepCount--;
            if (beepCount > 0) {
                handler.postDelayed(this, 300L);
            }
        }
    };

    public AlertPlayer(Context context) {
        this.appContext = context.getApplicationContext();
    }

    /** Warm up the text-to-speech engine so the first announcement can speak immediately. */
    public void prepare() {
        ensureTts();
    }

    /** Beep twice and speak the given message (Chinese). */
    public void announce(String message) {
        beep(2);
        ensureTts();
        if (tts != null && ttsReady) {
            tts.speak(message, TextToSpeech.QUEUE_FLUSH, null, "alert");
        }
    }

    private void beep(int times) {
        beepCount = Math.max(1, times);
        handler.removeCallbacks(beepRunnable);
        handler.post(beepRunnable);
    }

    private void playSingleBeep() {
        try {
            if (toneGenerator == null) {
                toneGenerator = new ToneGenerator(AudioManager.STREAM_ALARM, 90);
            }
            toneGenerator.startTone(ToneGenerator.TONE_PROP_BEEP, 200);
        } catch (RuntimeException ignored) {
            // Some devices lack tone generator support.
        }
    }

    private void ensureTts() {
        if (tts != null) {
            return;
        }
        tts = new TextToSpeech(appContext, status -> {
            if (status == TextToSpeech.SUCCESS && tts != null) {
                tts.setLanguage(Locale.CHINESE);
                ttsReady = true;
            }
        });
    }

    public void release() {
        handler.removeCallbacks(beepRunnable);
        beepCount = 0;
        if (tts != null) {
            tts.stop();
            tts.shutdown();
            tts = null;
        }
        if (toneGenerator != null) {
            toneGenerator.release();
            toneGenerator = null;
        }
    }
}
