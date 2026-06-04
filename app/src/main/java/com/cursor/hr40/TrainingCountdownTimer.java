package com.cursor.hr40;

import android.content.Context;
import android.media.AudioManager;
import android.media.ToneGenerator;
import android.os.Handler;
import android.os.Looper;
import android.speech.tts.TextToSpeech;

import java.util.Locale;

/** Countdown for timed holds (e.g. plank) with beeps and voice prompt. */
public final class TrainingCountdownTimer {
    public interface Listener {
        void onTick(int remainingSeconds);

        void onFinished();
    }

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Context appContext;
    private TextToSpeech tts;
    private ToneGenerator toneGenerator;
    private int remainingSeconds;
    private Listener listener;
    private boolean running;
    private int beepCount;

    private final Runnable tickRunnable = new Runnable() {
        @Override
        public void run() {
            if (!running) {
                return;
            }
            remainingSeconds--;
            if (remainingSeconds < 0) {
                finishCountdown();
                return;
            }
            if (listener != null) {
                listener.onTick(remainingSeconds);
            }
            handler.postDelayed(this, 1000L);
        }
    };

    private final Runnable beepRunnable = new Runnable() {
        @Override
        public void run() {
            if (beepCount <= 0) {
                return;
            }
            playSingleBeep();
            beepCount--;
            if (beepCount > 0) {
                handler.postDelayed(this, 280L);
            }
        }
    };

    public TrainingCountdownTimer(Context context) {
        this.appContext = context.getApplicationContext();
    }

    public void setListener(Listener listener) {
        this.listener = listener;
    }

    public void start(int totalSeconds) {
        stop();
        remainingSeconds = Math.max(1, totalSeconds);
        running = true;
        ensureTts();
        if (listener != null) {
            listener.onTick(remainingSeconds);
        }
        handler.postDelayed(tickRunnable, 1000L);
    }

    public void stop() {
        running = false;
        handler.removeCallbacks(tickRunnable);
        handler.removeCallbacks(beepRunnable);
        beepCount = 0;
    }

    public void release() {
        stop();
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

    private void finishCountdown() {
        running = false;
        handler.removeCallbacks(tickRunnable);
        playCompletionAlerts();
        if (listener != null) {
            listener.onFinished();
        }
    }

    private void playCompletionAlerts() {
        beepCount = 3;
        handler.post(beepRunnable);
        if (tts != null) {
            tts.speak("时间到", TextToSpeech.QUEUE_FLUSH, null, "countdown_done");
        }
    }

    private void playSingleBeep() {
        try {
            if (toneGenerator == null) {
                toneGenerator = new ToneGenerator(AudioManager.STREAM_ALARM, 85);
            }
            toneGenerator.startTone(ToneGenerator.TONE_PROP_BEEP, 180);
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
            }
        });
    }
}
