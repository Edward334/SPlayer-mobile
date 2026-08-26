package com.imsyy.splayer;

import android.media.AudioAttributes;
import android.media.MediaPlayer;
import android.content.Intent;
import android.os.Handler;
import android.os.Looper;

import com.getcapacitor.JSObject;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;
import androidx.core.content.ContextCompat;

import java.io.IOException;

@CapacitorPlugin(name = "SPlayerAudio")
public class SPlayerAudioPlugin extends Plugin {
    private static SPlayerAudioPlugin activeInstance;
    private MediaPlayer player;
    private boolean prepared;
    private String metadataTitle = "SPlayer";
    private String metadataArtist = "";
    private String metadataArtwork = "";
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable progressTicker = new Runnable() {
        @Override
        public void run() {
            if (player != null && player.isPlaying()) {
                JSObject data = new JSObject();
                data.put("currentTime", player.getCurrentPosition() / 1000.0);
                data.put("duration", player.getDuration() / 1000.0);
                notifyListeners("timeUpdate", data);
                SPlayerAudioService.updatePlaybackState(true, player.getCurrentPosition());
                handler.postDelayed(this, 250);
            }
        }
    };

    @PluginMethod
    public void initialize(PluginCall call) {
        activeInstance = this;
        ensurePlayer();
        call.resolve();
    }

    @PluginMethod
    public void load(PluginCall call) {
        String url = call.getString("url");
        if (url == null || url.isEmpty()) {
            call.reject("Audio URL is required");
            return;
        }
        ensurePlayer();
        metadataTitle = call.getString("title", "SPlayer");
        metadataArtist = call.getString("artist", "");
        metadataArtwork = call.getString("artwork", "");
        try {
            player.reset();
            prepared = false;
            player.setDataSource(url);
            player.setOnPreparedListener(mp -> {
                prepared = true;
                SPlayerAudioService.updateMetadata(metadataTitle, metadataArtist, metadataArtwork, mp.getDuration());
                call.resolve();
            });
            player.setOnCompletionListener(mp -> {
                handler.removeCallbacks(progressTicker);
                SPlayerAudioService.updatePlaybackState(false, mp.getDuration());
                JSObject update = new JSObject();
                update.put("currentTime", mp.getDuration() / 1000.0);
                update.put("duration", mp.getDuration() / 1000.0);
                notifyListeners("timeUpdate", update);
                notifyListeners("ended", new JSObject());
            });
            player.setOnErrorListener((mp, what, extra) -> {
                prepared = false;
                handler.removeCallbacks(progressTicker);
                JSObject error = new JSObject();
                error.put("errorCode", extra);
                notifyListeners("error", error);
                call.reject("Unable to decode audio", error);
                return true;
            });
            player.prepareAsync();
        } catch (IOException error) {
            call.reject("Unable to load audio", error);
        }
    }

    @PluginMethod
    public void play(PluginCall call) {
        ensurePlayer();
        if (!prepared) {
            call.reject("Audio is not prepared");
            return;
        }
        ContextCompat.startForegroundService(getContext(), new Intent(getContext(), SPlayerAudioService.class));
        if (player.getDuration() > 0 && player.getCurrentPosition() >= player.getDuration()) player.seekTo(0);
        SPlayerAudioService.updateMetadata(metadataTitle, metadataArtist, metadataArtwork, player.getDuration());
        SPlayerAudioService.updatePlaybackState(true, player.getCurrentPosition());
        player.start();
        notifyListeners("play", new JSObject());
        handler.removeCallbacks(progressTicker);
        handler.post(progressTicker);
        call.resolve();
    }

    @PluginMethod
    public void pause(PluginCall call) {
        if (player != null && prepared && player.isPlaying()) player.pause();
        SPlayerAudioService.updatePlaybackState(false, player == null ? 0 : player.getCurrentPosition());
        notifyListeners("pause", new JSObject());
        call.resolve();
    }

    @PluginMethod
    public void stop(PluginCall call) {
        handler.removeCallbacks(progressTicker);
        if (player != null && prepared) {
            try {
                player.stop();
            } catch (IllegalStateException ignored) {
                // MediaPlayer may already be in the error state.
            }
        }
        prepared = false;
        SPlayerAudioService.updatePlaybackState(false, 0);
        getContext().stopService(new Intent(getContext(), SPlayerAudioService.class));
        notifyListeners("pause", new JSObject());
        call.resolve();
    }

    @PluginMethod
    public void seek(PluginCall call) {
        if (player != null && prepared) player.seekTo((int) (call.getDouble("time", 0.0) * 1000));
        call.resolve();
    }

    @PluginMethod
    public void setVolume(PluginCall call) {
        Double requestedVolume = call.getDouble("volume");
        float volume = requestedVolume == null ? 1.0f : requestedVolume.floatValue();
        ensurePlayer();
        if (prepared) player.setVolume(volume, volume);
        call.resolve();
    }

    @PluginMethod
    public void setRate(PluginCall call) {
        if (player != null && prepared && android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
            Double requestedRate = call.getDouble("rate");
            float rate = requestedRate == null ? 1.0f : requestedRate.floatValue();
            player.setPlaybackParams(player.getPlaybackParams().setSpeed(rate));
        }
        call.resolve();
    }

    @PluginMethod
    public void getState(PluginCall call) {
        JSObject state = new JSObject();
        state.put("src", "");
        state.put("duration", player == null || !prepared ? 0 : player.getDuration() / 1000.0);
        state.put("currentTime", player == null || !prepared ? 0 : player.getCurrentPosition() / 1000.0);
        state.put("paused", player == null || !prepared || !player.isPlaying());
        call.resolve(state);
    }

    @Override
    protected void handleOnDestroy() {
        handler.removeCallbacks(progressTicker);
        if (player != null) {
            player.release();
            player = null;
        }
        prepared = false;
        getContext().stopService(new Intent(getContext(), SPlayerAudioService.class));
        if (activeInstance == this) activeInstance = null;
        super.handleOnDestroy();
    }

    static void handleMediaPlay() {
        if (activeInstance != null && activeInstance.player != null && activeInstance.prepared) {
            if (activeInstance.player.getDuration() > 0
                    && activeInstance.player.getCurrentPosition() >= activeInstance.player.getDuration()) {
                activeInstance.player.seekTo(0);
            }
            activeInstance.player.start();
            SPlayerAudioService.updatePlaybackState(true, activeInstance.player.getCurrentPosition());
            activeInstance.notifyListeners("play", new JSObject());
            activeInstance.handler.removeCallbacks(activeInstance.progressTicker);
            activeInstance.handler.post(activeInstance.progressTicker);
        }
    }

    static void handleMediaPause() {
        if (activeInstance != null && activeInstance.player != null && activeInstance.prepared && activeInstance.player.isPlaying()) {
            activeInstance.player.pause();
            activeInstance.handler.removeCallbacks(activeInstance.progressTicker);
            SPlayerAudioService.updatePlaybackState(false, activeInstance.player.getCurrentPosition());
            activeInstance.notifyListeners("pause", new JSObject());
        }
    }

    static void handleMediaSeek(long positionMs) {
        if (activeInstance != null && activeInstance.player != null && activeInstance.prepared) {
            long duration = activeInstance.player.getDuration();
            long safePosition = Math.max(0, Math.min(positionMs, duration));
            activeInstance.player.seekTo((int) safePosition);
            SPlayerAudioService.updatePlaybackState(activeInstance.player.isPlaying(), safePosition);
        }
    }

    static void handleMediaNext() {
        notifyMediaAction("next");
    }

    static void handleMediaPrevious() {
        notifyMediaAction("previous");
    }

    private static void notifyMediaAction(String action) {
        if (activeInstance == null) return;
        JSObject event = new JSObject();
        event.put("action", action);
        activeInstance.notifyListeners("mediaAction", event);
    }

    private void ensurePlayer() {
        if (player != null) return;
        player = new MediaPlayer();
        player.setAudioAttributes(new AudioAttributes.Builder()
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .build());
    }
}
