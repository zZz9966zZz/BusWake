package com.buswake.app;

import android.content.Context;
import android.media.AudioAttributes;
import android.media.AudioFocusRequest;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.os.VibratorManager;
import android.provider.Settings;

import java.io.File;

public final class AlarmController {
    private static MediaPlayer player;
    private static Vibrator vibrator;
    private static AudioManager audioManager;
    private static AudioFocusRequest focusRequest;
    private static boolean playing;

    private AlarmController() {
    }

    public static synchronized void start(Context context) {
        if (playing) {
            return;
        }
        playing = true;
        Context app = context.getApplicationContext();

        audioManager = (AudioManager) app.getSystemService(Context.AUDIO_SERVICE);
        AudioAttributes attrs = new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ALARM)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build();

        try {
            if (audioManager != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                focusRequest = new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
                        .setAudioAttributes(attrs)
                        .setAcceptsDelayedFocusGain(false)
                        .build();
                audioManager.requestAudioFocus(focusRequest);
            }
        } catch (Exception ignored) {
        }

        player = createPlayer(app, attrs);
        AppLog.i("Alarm", "Playback started with "
                + (AlarmSoundStore.hasCustom(app) ? "custom sound" : "system default alarm")
                + ", volume follows system alarm stream");
        startVibration(app);
    }

    private static MediaPlayer createPlayer(Context app, AudioAttributes attrs) {
        File custom = AlarmSoundStore.getCustomFile(app);
        if (custom != null) {
            MediaPlayer mp = prepareFile(app, attrs, custom);
            if (mp != null) {
                return mp;
            }
        }

        Uri uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM);
        if (uri == null) {
            uri = Settings.System.DEFAULT_ALARM_ALERT_URI;
        }
        MediaPlayer mp = prepareUri(app, attrs, uri);
        if (mp != null) {
            return mp;
        }

        uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);
        if (uri == null) {
            uri = Settings.System.DEFAULT_NOTIFICATION_URI;
        }
        return prepareUri(app, attrs, uri);
    }

    private static MediaPlayer prepareFile(Context app, AudioAttributes attrs, File file) {
        MediaPlayer mp = new MediaPlayer();
        try {
            mp.setAudioAttributes(attrs);
            mp.setDataSource(file.getAbsolutePath());
            mp.setLooping(true);
            mp.prepare();
            mp.start();
            return mp;
        } catch (Exception e) {
            try {
                mp.release();
            } catch (Exception ignored) {
            }
            return null;
        }
    }

    private static MediaPlayer prepareUri(Context app, AudioAttributes attrs, Uri uri) {
        if (uri == null) {
            return null;
        }
        MediaPlayer mp = new MediaPlayer();
        try {
            mp.setAudioAttributes(attrs);
            mp.setDataSource(app, uri);
            mp.setLooping(true);
            mp.prepare();
            mp.start();
            return mp;
        } catch (Exception e) {
            try {
                mp.release();
            } catch (Exception ignored) {
            }
            return null;
        }
    }

    private static void startVibration(Context app) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                VibratorManager vm = (VibratorManager) app.getSystemService(Context.VIBRATOR_MANAGER_SERVICE);
                vibrator = vm != null ? vm.getDefaultVibrator() : null;
            } else {
                vibrator = (Vibrator) app.getSystemService(Context.VIBRATOR_SERVICE);
            }
            if (vibrator != null && vibrator.hasVibrator()) {
                long[] pattern = new long[]{0, 700, 450, 700, 900};
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    vibrator.vibrate(VibrationEffect.createWaveform(pattern, 0));
                } else {
                    vibrator.vibrate(pattern, 0);
                }
            }
        } catch (Exception ignored) {
        }
    }

    public static synchronized void stop() {
        if (!playing) {
            return;
        }
        playing = false;
        releasePlayer();
        AppLog.i("Alarm", "Playback stopped");
        if (vibrator != null) {
            try {
                vibrator.cancel();
            } catch (Exception ignored) {
            }
            vibrator = null;
        }
        if (audioManager != null && focusRequest != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            try {
                audioManager.abandonAudioFocusRequest(focusRequest);
            } catch (Exception ignored) {
            }
        }
        focusRequest = null;
        audioManager = null;
    }

    private static void releasePlayer() {
        if (player != null) {
            try {
                if (player.isPlaying()) {
                    player.stop();
                }
            } catch (Exception ignored) {
            }
            try {
                player.release();
            } catch (Exception ignored) {
            }
            player = null;
        }
    }

    public static synchronized boolean isPlaying() {
        return playing;
    }
}
