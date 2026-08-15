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
                SPlayerAudioService.updateMetadata(metadataTitle, metadataArtist, metadataArtwork);
                call.resolve();
            });
            player.setOnCompletionListener(mp -> notifyListeners("ended", new JSObject()));
            player.setOnErrorListener((mp, what, extra) -> {
                JSObject error = new JSObject();
                error.put("errorCode", extra);
                notifyListeners("error", error);
                return false;
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
        SPlayerAudioService.updateMetadata(metadataTitle, metadataArtist, metadataArtwork);
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
        if (player != null && prepared) player.stop();
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
        float volume = (float) call.getDouble("volume", 1.0);
        ensurePlayer();
        if (prepared) player.setVolume(volume, volume);
        call.resolve();
    }

    @PluginMethod
    public void setRate(PluginCall call) {
        if (player != null && prepared && android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
            player.setPlaybackParams(player.getPlaybackParams().setSpeed((float) call.getDouble("rate", 1.0)));
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
            activeInstance.player.start();
            SPlayerAudioService.updatePlaybackState(true, activeInstance.player.getCurrentPosition());
            activeInstance.notifyListeners("play", new JSObject());
        }
    }

    static void handleMediaPause() {
        if (activeInstance != null && activeInstance.player != null && activeInstance.prepared && activeInstance.player.isPlaying()) {
            activeInstance.player.pause();
            SPlayerAudioService.updatePlaybackState(false, activeInstance.player.getCurrentPosition());
            activeInstance.notifyListeners("pause", new JSObject());
        }
    }

    static void handleMediaSeek(long positionMs) {
        if (activeInstance != null && activeInstance.player != null && activeInstance.prepared) {
            activeInstance.player.seekTo((int) positionMs);
            SPlayerAudioService.updatePlaybackState(activeInstance.player.isPlaying(), positionMs);
        }
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
