package com.parsagames.amiralmomenincalendar;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.media.AudioAttributes;
import android.media.MediaPlayer;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.PowerManager;

import java.io.File;

public class AdhanService extends Service {
    static final String ACTION_PLAY = "com.parsagames.amiralmomenincalendar.ADHAN_PLAY";
    static final String ACTION_STOP = "com.parsagames.amiralmomenincalendar.ADHAN_STOP";
    static final String CHANNEL = "adhan_channel";
    static final int NOTIF_ID = 7731;

    private MediaPlayer player;
    private PowerManager.WakeLock wake;
    private final Handler handler = new Handler();
    private boolean triedFallback = false;

    static void requestStop(Context c) {
        Intent i = new Intent(c, AdhanService.class);
        i.setAction(ACTION_STOP);
        c.startService(i);
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        String action = intent == null ? null : intent.getAction();
        if (ACTION_STOP.equals(action)) {
            shutdown();
            return START_NOT_STICKY;
        }
        String name = intent == null ? null : intent.getStringExtra("name");
        if (name == null || name.length() == 0) name = "اذان";
        showNotification(name);
        startPlayback();
        return START_NOT_STICKY;
    }

    private void showNotification(String name) {
        NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (Build.VERSION.SDK_INT >= 26 && nm != null) {
            NotificationChannel ch = new NotificationChannel(CHANNEL, "اذان", NotificationManager.IMPORTANCE_DEFAULT);
            ch.setSound(null, null);
            nm.createNotificationChannel(ch);
        }
        Intent stop = new Intent(this, AdhanService.class);
        stop.setAction(ACTION_STOP);
        PendingIntent stopPi = PendingIntent.getService(this, 1, stop, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        PendingIntent openPi = null;
        Intent launch = getPackageManager().getLaunchIntentForPackage(getPackageName());
        if (launch != null) {
            openPi = PendingIntent.getActivity(this, 2, launch, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        }
        Notification.Builder b;
        if (Build.VERSION.SDK_INT >= 26) {
            b = new Notification.Builder(this, CHANNEL);
        } else {
            b = new Notification.Builder(this);
        }
        b.setSmallIcon(getApplicationInfo().icon)
                .setContentTitle(name)
                .setContentText("در حال پخش اذان")
                .setOngoing(true)
                .addAction(0, "قطع اذان", stopPi);
        if (openPi != null) b.setContentIntent(openPi);
        Notification n = b.build();
        if (Build.VERSION.SDK_INT >= 29) {
            startForeground(NOTIF_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK);
        } else {
            startForeground(NOTIF_ID, n);
        }
    }

    private void startPlayback() {
        releasePlayer();
        try {
            PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
            if (pm != null && wake == null) {
                wake = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "adhan:play");
                wake.acquire(12 * 60 * 1000L);
            }
        } catch (Exception e) {
            // ignore
        }
        android.content.SharedPreferences sp = getSharedPreferences(AdhanScheduler.PREFS, 0);
        String file = sp.getString("file", "");
        String url = sp.getString("url", "");
        if (file != null && file.startsWith("file://")) file = file.substring(7);
        try {
            player = new MediaPlayer();
            player.setAudioAttributes(new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build());
            if (file != null && file.length() > 0 && new File(file).exists()) {
                player.setDataSource(file);
            } else if (url != null && url.startsWith("http")) {
                player.setDataSource(url);
            } else {
                player.setDataSource(this, RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM));
            }
            player.setOnPreparedListener(new MediaPlayer.OnPreparedListener() {
                @Override
                public void onPrepared(MediaPlayer mp) {
                    mp.start();
                }
            });
            player.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
                @Override
                public void onCompletion(MediaPlayer mp) {
                    shutdown();
                }
            });
            player.setOnErrorListener(new MediaPlayer.OnErrorListener() {
                @Override
                public boolean onError(MediaPlayer mp, int what, int extra) {
                    fallbackRingtone();
                    return true;
                }
            });
            player.prepareAsync();
        } catch (Exception e) {
            fallbackRingtone();
        }
        // safety: never play longer than 12 minutes
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                shutdown();
            }
        }, 12 * 60 * 1000L);
    }

    private void fallbackRingtone() {
        if (triedFallback) {
            shutdown();
            return;
        }
        triedFallback = true;
        releasePlayer();
        try {
            Uri u = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM);
            player = new MediaPlayer();
            player.setDataSource(this, u);
            player.setOnPreparedListener(new MediaPlayer.OnPreparedListener() {
                @Override
                public void onPrepared(MediaPlayer mp) {
                    mp.start();
                }
            });
            player.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
                @Override
                public void onCompletion(MediaPlayer mp) {
                    shutdown();
                }
            });
            player.prepareAsync();
        } catch (Exception e) {
            shutdown();
        }
    }

    private void releasePlayer() {
        if (player != null) {
            try {
                player.stop();
            } catch (Exception e) {
                // ignore
            }
            try {
                player.release();
            } catch (Exception e) {
                // ignore
            }
            player = null;
        }
    }

    private void shutdown() {
        handler.removeCallbacksAndMessages(null);
        releasePlayer();
        try {
            if (wake != null && wake.isHeld()) wake.release();
        } catch (Exception e) {
            // ignore
        }
        wake = null;
        stopForeground(true);
        stopSelf();
    }

    @Override
    public void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        releasePlayer();
        super.onDestroy();
    }
}
