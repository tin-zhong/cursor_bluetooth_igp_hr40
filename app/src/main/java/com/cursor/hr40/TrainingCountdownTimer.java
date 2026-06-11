package com.cursor.hr40;

import android.content.Context;
import android.media.AudioAttributes;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.media.ToneGenerator;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.speech.tts.TextToSpeech;

import java.util.Locale;

/**
 * Countdown for timed holds and rest periods with beeps and a voice prompt.
 *
 * <p>By default the completion alert plays once (three beeps + voice) and then finishes —
 * the offline app keeps this behaviour. When {@link #setLoopAlertUntilAck(boolean)} is enabled
 * (online app), the completion alert keeps looping until {@link #acknowledge()} is called, so
 * the user must actively confirm before the prompt stops. In that mode the alert tone can be
 * customised via {@link #setCustomAlertUri(Uri)}.
 */
public final class TrainingCountdownTimer {
    public interface Listener {
        void onTick(int remainingSeconds);

        void onFinished();
    }

    private static final long LOOP_BEEP_INTERVAL_MS = 700L;
    private static final long BURST_BEEP_INTERVAL_MS = 280L;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Context appContext;
    private TextToSpeech tts;
    private ToneGenerator toneGenerator;
    private MediaPlayer alertMediaPlayer;
    private int remainingSeconds;
    private int beepCount;
    private Listener listener;
    private boolean running;
    private boolean alerting;
    private boolean loopAlertUntilAck;
    private String completionMessage = "时间到";
    private Uri customAlertUri;

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

    /** One-shot completion burst: a fixed number of beeps spaced apart. */
    private final Runnable burstBeepRunnable = new Runnable() {
        @Override
        public void run() {
            if (beepCount <= 0) {
                return;
            }
            playSingleBeep();
            beepCount--;
            if (beepCount > 0) {
                handler.postDelayed(this, BURST_BEEP_INTERVAL_MS);
            }
        }
    };

    /** Looping completion beep that repeats while the alert awaits acknowledgement. */
    private final Runnable loopBeepRunnable = new Runnable() {
        @Override
        public void run() {
            if (!alerting) {
                return;
            }
            playSingleBeep();
            handler.postDelayed(this, LOOP_BEEP_INTERVAL_MS);
        }
    };

    public TrainingCountdownTimer(Context context) {
        this.appContext = context.getApplicationContext();
    }

    public void setListener(Listener listener) {
        this.listener = listener;
    }

    /** When true, the completion alert loops until {@link #acknowledge()} is called. */
    public void setLoopAlertUntilAck(boolean loop) {
        this.loopAlertUntilAck = loop;
    }

    /** Voice prompt spoken when the countdown finishes (e.g. 「休息时间结束」). */
    public void setCompletionMessage(String message) {
        this.completionMessage = (message == null || message.trim().isEmpty()) ? "时间到" : message;
    }

    /** Custom looping completion sound; {@code null} restores the built-in beep. */
    public void setCustomAlertUri(Uri uri) {
        this.customAlertUri = uri;
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
        handler.removeCallbacks(burstBeepRunnable);
        beepCount = 0;
        acknowledge();
    }

    /** Stop the looping completion alert after the user confirms. */
    public void acknowledge() {
        alerting = false;
        handler.removeCallbacks(loopBeepRunnable);
        if (tts != null) {
            tts.stop();
        }
        releaseAlertMediaPlayer();
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
        if (loopAlertUntilAck) {
            startCompletionAlertLoop();
        } else {
            playCompletionAlertOnce();
        }
        if (listener != null) {
            listener.onFinished();
        }
    }

    private void playCompletionAlertOnce() {
        beepCount = 3;
        handler.removeCallbacks(burstBeepRunnable);
        handler.post(burstBeepRunnable);
        if (tts != null) {
            tts.speak(completionMessage, TextToSpeech.QUEUE_FLUSH, null, "countdown_done");
        }
    }

    private void startCompletionAlertLoop() {
        alerting = true;
        if (tts != null) {
            tts.speak(completionMessage, TextToSpeech.QUEUE_FLUSH, null, "countdown_done");
        }
        if (customAlertUri != null && startCustomLoopingSound()) {
            return;
        }
        handler.removeCallbacks(loopBeepRunnable);
        handler.post(loopBeepRunnable);
    }

    private boolean startCustomLoopingSound() {
        try {
            releaseAlertMediaPlayer();
            alertMediaPlayer = new MediaPlayer();
            alertMediaPlayer.setAudioAttributes(new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build());
            alertMediaPlayer.setDataSource(appContext, customAlertUri);
            alertMediaPlayer.setLooping(true);
            alertMediaPlayer.prepare();
            alertMediaPlayer.start();
            return true;
        } catch (Exception e) {
            releaseAlertMediaPlayer();
            return false;
        }
    }

    private void releaseAlertMediaPlayer() {
        if (alertMediaPlayer != null) {
            try {
                alertMediaPlayer.stop();
            } catch (IllegalStateException ignored) {
                // Player may not have been started; ignore.
            }
            alertMediaPlayer.release();
            alertMediaPlayer = null;
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
