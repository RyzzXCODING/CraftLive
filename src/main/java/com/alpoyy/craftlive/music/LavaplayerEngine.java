package com.alpoyy.craftlive.music;

import com.alpoyy.craftlive.CraftLive;
import com.sedmelluq.discord.lavaplayer.player.AudioLoadResultHandler;
import com.sedmelluq.discord.lavaplayer.player.AudioPlayer;
import com.sedmelluq.discord.lavaplayer.player.AudioPlayerManager;
import com.sedmelluq.discord.lavaplayer.player.DefaultAudioPlayerManager;
import com.sedmelluq.discord.lavaplayer.source.http.HttpAudioSourceManager;
import com.sedmelluq.discord.lavaplayer.source.local.LocalAudioSourceManager;
import com.sedmelluq.discord.lavaplayer.tools.FriendlyException;
import com.sedmelluq.discord.lavaplayer.track.AudioPlaylist;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * Audio playback engine on top of lavaplayer. Plays direct media URLs
 * (resolved by yt-dlp) and local files. Fully off the Minecraft main
 * thread; volume, pause, position and duration supported.
 */
public class LavaplayerEngine {
    private final AudioPlayerManager manager;
    private final AudioPlayer player;

    private volatile float volume = 0.8f;
    private volatile Consumer<Track> onTrackEnded = t -> {};
    private volatile Track currentTrack;

    public LavaplayerEngine() {
        manager = new DefaultAudioPlayerManager();
        manager.registerSourceManager(new HttpAudioSourceManager());
        manager.registerSourceManager(new LocalAudioSourceManager());
        player = manager.createPlayer();
        player.addListener(new com.sedmelluq.discord.lavaplayer.player.event.AudioEventListener() {
            @Override
            public void onEvent(com.sedmelluq.discord.lavaplayer.player.event.AudioEvent event) {
                if (event instanceof com.sedmelluq.discord.lavaplayer.player.event.TrackEndEvent) {
                    Track ended = currentTrack;
                    currentTrack = null;
                    if (ended != null) {
                        Consumer<Track> cb = onTrackEnded;
                        if (cb != null) cb.accept(ended);
                    }
                }
            }
        });
    }

    public void setTrackEndListener(Consumer<Track> listener) {
        this.onTrackEnded = listener == null ? t -> {} : listener;
    }

    /** Play a direct stream URL (from yt-dlp) or a local file path. */
    public CompletableFuture<String> play(String url, Track display) {
        currentTrack = display;
        CompletableFuture<String> future = new CompletableFuture<>();
        manager.loadItem(url, new AudioLoadResultHandler() {
            @Override
            public void trackLoaded(AudioTrack track) {
                player.playTrack(track);
                player.setVolume((int) (volume * 100));
                future.complete(null);
            }

            @Override
            public void playlistLoaded(AudioPlaylist playlist) {
                if (playlist.getTracks().isEmpty()) {
                    future.complete("Tidak ada audio di URL tersebut.");
                } else {
                    player.playTrack(playlist.getTracks().get(0));
                    player.setVolume((int) (volume * 100));
                    future.complete(null);
                }
            }

            @Override
            public void noMatches() {
                future.complete("Format audio tidak didukung.");
            }

            @Override
            public void loadFailed(FriendlyException exception) {
                future.complete(exception.getMessage() == null ? "Gagal memuat audio." : exception.getMessage());
            }
        });
        return future;
    }

    public void stop() {
        player.stopTrack();
        currentTrack = null;
    }

    public void pause(boolean paused) {
        player.setPaused(paused);
    }

    public boolean isPaused() {
        return player.isPaused();
    }

    public boolean isPlaying() {
        return player.getPlayingTrack() != null && !player.isPaused();
    }

    public long positionMs() {
        AudioTrack t = player.getPlayingTrack();
        return t == null ? 0 : t.getPosition();
    }

    public long durationMs() {
        AudioTrack t = player.getPlayingTrack();
        return t == null ? -1 : t.getDuration();
    }

    public void seek(long ms) {
        AudioTrack t = player.getPlayingTrack();
        if (t != null && t.isSeekable()) {
            t.setPosition(ms);
        }
    }

    /** 0..1 */
    public void setVolume(float v) {
        volume = Math.max(0f, Math.min(1f, v));
        player.setVolume((int) (volume * 100));
    }

    public float volume() {
        return volume;
    }

    public void shutdown() {
        try {
            player.stopTrack();
            manager.shutdown();
        } catch (Exception e) {
            CraftLive.LOGGER.debug("Lavaplayer shutdown: {}", e.toString());
        }
    }
}
