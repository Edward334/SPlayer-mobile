package com.imsyy.splayer;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.media.session.MediaSession;
import android.media.MediaMetadata;
import android.media.session.PlaybackState;
import android.os.Build;
import android.os.IBinder;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.media.app.NotificationCompat.MediaStyle;

public class SPlayerAudioService extends Service {
    private static final String CHANNEL_ID = "splayer-playback";
    private static final int NOTIFICATION_ID = 25884;
    private MediaSession mediaSession;
    private static SPlayerAudioService activeService;
    private static String pendingTitle = "SPlayer";
    private static String pendingArtist = "";
    private static boolean pendingPlaying;
    private static long pendingPositionMs;

    @Override
    public void onCreate() {
        super.onCreate();
        activeService = this;
        createNotificationChannel();
        mediaSession = new MediaSession(this, "SPlayer");
        mediaSession.setCallback(new MediaSession.Callback() {
            @Override
            public void onPlay() {
                SPlayerAudioPlugin.handleMediaPlay();
            }

            @Override
            public void onPause() {
                SPlayerAudioPlugin.handleMediaPause();
            }

            @Override
            public void onSeekTo(long pos) {
                SPlayerAudioPlugin.handleMediaSeek(pos);
            }
        });
        mediaSession.setFlags(MediaSession.FLAG_HANDLES_MEDIA_BUTTONS | MediaSession.FLAG_HANDLES_TRANSPORT_CONTROLS);
        mediaSession.setActive(true);
        updateMetadata(pendingTitle, pendingArtist, "");
        updatePlaybackState(pendingPlaying, pendingPositionMs);
        Notification notification = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("SPlayer")
                .setContentText("正在播放音乐")
                .setSmallIcon(android.R.drawable.ic_media_play)
                .setOngoing(true)
                .setCategory(NotificationCompat.CATEGORY_TRANSPORT)
                .setStyle(new MediaStyle().setMediaSession(mediaSession.getSessionToken()))
                .build();
        startForeground(NOTIFICATION_ID, notification);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        return START_STICKY;
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onDestroy() {
        if (mediaSession != null) {
            mediaSession.setActive(false);
            mediaSession.release();
            mediaSession = null;
        }
        if (activeService == this) activeService = null;
        super.onDestroy();
    }

    static void updateMetadata(String title, String artist, String artwork) {
        pendingTitle = title;
        pendingArtist = artist;
        if (activeService == null || activeService.mediaSession == null) return;
        MediaMetadata metadata = new MediaMetadata.Builder()
                .putString(MediaMetadata.METADATA_KEY_TITLE, title)
                .putString(MediaMetadata.METADATA_KEY_ARTIST, artist)
                .putString(MediaMetadata.METADATA_KEY_ALBUM, "SPlayer")
                .build();
        activeService.mediaSession.setMetadata(metadata);
    }

    static void updatePlaybackState(boolean playing, long positionMs) {
        pendingPlaying = playing;
        pendingPositionMs = positionMs;
        if (activeService == null || activeService.mediaSession == null) return;
        int state = playing ? PlaybackState.STATE_PLAYING : PlaybackState.STATE_PAUSED;
        PlaybackState playbackState = new PlaybackState.Builder()
                .setActions(PlaybackState.ACTION_PLAY | PlaybackState.ACTION_PAUSE
                        | PlaybackState.ACTION_SEEK_TO | PlaybackState.ACTION_PLAY_PAUSE)
                .setState(state, positionMs, 1.0f)
                .build();
        activeService.mediaSession.setPlaybackState(playbackState);
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return;
        NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID,
                "SPlayer 播放控制",
                NotificationManager.IMPORTANCE_LOW
        );
        NotificationManager manager = getSystemService(NotificationManager.class);
        if (manager != null) manager.createNotificationChannel(channel);
    }
}
