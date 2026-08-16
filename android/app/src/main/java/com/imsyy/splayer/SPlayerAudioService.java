package com.imsyy.splayer;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.media.session.MediaSession;
import android.media.MediaMetadata;
import android.media.session.PlaybackState;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;

import androidx.annotation.Nullable;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class SPlayerAudioService extends Service {
    private static final String CHANNEL_ID = "splayer-playback";
    private static final int NOTIFICATION_ID = 25884;
    private MediaSession mediaSession;
    private static SPlayerAudioService activeService;
    private static String pendingTitle = "SPlayer";
    private static String pendingArtist = "";
    private static String pendingArtwork = "";
    private static Bitmap pendingArtworkBitmap;
    private static String loadedArtwork = "";
    private static final ExecutorService artworkExecutor = Executors.newSingleThreadExecutor();
    private static long pendingDurationMs;
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

            @Override
            public void onSkipToNext() {
                SPlayerAudioPlugin.handleMediaNext();
            }

            @Override
            public void onSkipToPrevious() {
                SPlayerAudioPlugin.handleMediaPrevious();
            }
        });
        mediaSession.setFlags(MediaSession.FLAG_HANDLES_MEDIA_BUTTONS | MediaSession.FLAG_HANDLES_TRANSPORT_CONTROLS);
        mediaSession.setActive(true);
        updateMetadata(pendingTitle, pendingArtist, pendingArtwork, pendingDurationMs);
        updatePlaybackState(pendingPlaying, pendingPositionMs);
        Notification notification = new Notification.Builder(this, CHANNEL_ID)
                .setContentTitle("SPlayer")
                .setContentText("正在播放音乐")
                .setSmallIcon(android.R.drawable.ic_media_play)
                .setOngoing(true)
                .setCategory(Notification.CATEGORY_TRANSPORT)
                .setStyle(new Notification.MediaStyle().setMediaSession(mediaSession.getSessionToken()))
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

    static void updateMetadata(String title, String artist, String artwork, long durationMs) {
        pendingTitle = title;
        pendingArtist = artist;
        pendingArtwork = artwork == null ? "" : artwork;
        pendingDurationMs = Math.max(0, durationMs);
        if (!pendingArtwork.equals(loadedArtwork)) pendingArtworkBitmap = null;
        if (pendingArtwork.isEmpty()) {
            pendingArtworkBitmap = null;
            loadedArtwork = "";
        }
        if (activeService == null || activeService.mediaSession == null) return;
        activeService.applyMetadata();
        loadArtwork(pendingArtwork);
    }

    private void applyMetadata() {
        MediaMetadata metadata = new MediaMetadata.Builder()
                .putString(MediaMetadata.METADATA_KEY_TITLE, pendingTitle)
                .putString(MediaMetadata.METADATA_KEY_ARTIST, pendingArtist)
                .putString(MediaMetadata.METADATA_KEY_ALBUM, "SPlayer")
                .putLong(MediaMetadata.METADATA_KEY_DURATION, pendingDurationMs)
                .putString(MediaMetadata.METADATA_KEY_ALBUM_ART_URI, pendingArtwork)
                .putBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART, pendingArtworkBitmap)
                .build();
        mediaSession.setMetadata(metadata);
    }

    private static void loadArtwork(String artwork) {
        if (artwork == null || artwork.isEmpty() || artwork.equals(loadedArtwork)) return;
        artworkExecutor.execute(() -> {
            Bitmap bitmap = null;
            HttpURLConnection connection = null;
            try {
                connection = (HttpURLConnection) new URL(artwork).openConnection();
                connection.setConnectTimeout(10000);
                connection.setReadTimeout(10000);
                try (InputStream stream = connection.getInputStream()) {
                    bitmap = BitmapFactory.decodeStream(stream);
                }
            } catch (Exception ignored) {
                return;
            } finally {
                if (connection != null) connection.disconnect();
            }
            Bitmap loadedBitmap = bitmap;
            new Handler(Looper.getMainLooper()).post(() -> {
                if (!artwork.equals(pendingArtwork)) return;
                pendingArtworkBitmap = loadedBitmap;
                loadedArtwork = artwork;
                if (activeService != null && activeService.mediaSession != null) {
                    activeService.applyMetadata();
                }
            });
        });
    }

    static void updatePlaybackState(boolean playing, long positionMs) {
        pendingPlaying = playing;
        pendingPositionMs = positionMs;
        if (activeService == null || activeService.mediaSession == null) return;
        long safePosition = Math.max(0, positionMs);
        if (pendingDurationMs > 0) safePosition = Math.min(safePosition, pendingDurationMs);
        int state = playing ? PlaybackState.STATE_PLAYING : PlaybackState.STATE_PAUSED;
        PlaybackState playbackState = new PlaybackState.Builder()
                .setActions(PlaybackState.ACTION_PLAY | PlaybackState.ACTION_PAUSE
                        | PlaybackState.ACTION_SEEK_TO | PlaybackState.ACTION_PLAY_PAUSE
                        | PlaybackState.ACTION_SKIP_TO_NEXT | PlaybackState.ACTION_SKIP_TO_PREVIOUS)
                .setState(state, safePosition, 1.0f)
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
