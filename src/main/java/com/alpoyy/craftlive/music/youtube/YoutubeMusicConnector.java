package com.alpoyy.craftlive.music.youtube;

import com.alpoyy.craftlive.CraftLive;
import com.alpoyy.craftlive.music.LavaplayerEngine;
import com.alpoyy.craftlive.music.MusicPlatform;
import com.alpoyy.craftlive.music.PlayerState;
import com.alpoyy.craftlive.music.Track;
import com.alpoyy.craftlive.music.YtDlpClient;

import java.util.List;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

/**
 * YouTube Music platform via yt-dlp (user-authorized) + lavaplayer.
 *
 * Real capabilities:
 *  - search tracks
 *  - real audio playback with play/pause, volume, progress, seek
 *  - next/previous through the play history
 *  - permitted handoff (open track in browser)
 *
 * Requires yt-dlp installed on the system (path configurable). Without it,
 * the connector stays unavailable with an honest error — no fake playback.
 */
public class YoutubeMusicConnector implements MusicPlatform {
    public final YtDlpClient ytdlp = new YtDlpClient();
    private final LavaplayerEngine engine = new LavaplayerEngine();
    private final PlayerState state = new PlayerState();
    private final AtomicReference<List<Track>> history = new AtomicReference<>(List.of());
    private final java.util.Deque<Track> backStack = new java.util.ArrayDeque<>();
    private final java.util.Deque<Track> forwardStack = new java.util.ArrayDeque<>();

    public YoutubeMusicConnector() {
        state.volumeSupported = true;
        state.controlsSupported = true;
        engine.setTrackEndListener(t -> {
            state.status = PlayerState.Status.CONNECTED;
            state.progressMs = 0;
            gateActive = false;   // song finished → pipeline may advance
        });
    }

    public void setBinaryPath(String path) {
        ytdlp.setBinaryPath(path);
        state.status = PlayerState.Status.DISCONNECTED;
    }

    // ---- request pipeline gate ----
    // While a request-triggered track is actually audible, the Song Request
    // pipeline must wait (never cut the song with the 3–5s simulation delay).
    private volatile boolean gateActive = false;

    public boolean isGateActive() {
        return gateActive;
    }

    public YtDlpClient client() {
        return ytdlp;
    }

    @Override
    public String id() {
        return "youtube";
    }

    @Override
    public String displayName() {
        return "YouTube Music";
    }

    @Override
    public PlayerState state() {
        return state;
    }

    @Override
    public boolean isConfigured() {
        // cheap cached state — never spawns a process from render code
        return ytdlp.isAvailable();
    }

    /** Convenience alias for the cached yt-dlp availability. */
    public boolean isAvailable() {
        return ytdlp.isAvailable();
    }

    /** Trigger a background re-probe (throttled inside the client). */
    public void recheck() {
        ytdlp.probeAsync();
    }

    @Override
    public void connect() {
        // ensure a probe has run at least once, then react honestly
        ytdlp.probeAsync();
        Thread t = new Thread(() -> {
            long deadline = System.currentTimeMillis() + 10_000;
            while (System.currentTimeMillis() < deadline
                    && ytdlp.availability() == YtDlpClient.Availability.CHECKING) {
                try {
                    Thread.sleep(150);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                }
            }
            switch (ytdlp.availability()) {
                case AVAILABLE -> {
                    state.errorMessage = null;
                    state.status = PlayerState.Status.CONNECTED;
                    CraftLive.LOGGER.info("YouTube Music ready via yt-dlp {}", ytdlp.availableVersion());
                }
                case UNAVAILABLE -> {
                    state.status = PlayerState.Status.ERROR;
                    state.errorMessage = ytdlp.unavailableMessage();
                }
                default -> {
                    state.status = PlayerState.Status.ERROR;
                    state.errorMessage = "yt-dlp belum terverifikasi — coba Periksa Ulang.";
                }
            }
        }, "CraftLive-YtConnect");
        t.setDaemon(true);
        t.start();
    }

    @Override
    public void disconnect() {
        engine.stop();
        state.status = PlayerState.Status.DISCONNECTED;
        state.current = null;
    }

    @Override
    public void startPolling(long intervalMs) {
        // progress is tracked by the engine directly; no remote polling needed
    }

    @Override
    public void stopPolling() {
    }

    // ---- playback control (real) ----

    @Override
    public boolean supportsPlaybackControl() {
        return true;
    }

    /** Search and play the first result; used by song-request auto-play. */
    public void searchAndPlay(String query, Consumer<String> onError) {
        ytdlp.search(query, 1).thenAccept(result -> {
            if (result.error != null) {
                if (onError != null) onError.accept(result.error);
                return;
            }
            if (result.tracks.isEmpty()) {
                if (onError != null) onError.accept("Tidak ada hasil untuk: " + query);
                return;
            }
            gateActive = true;
            play(result.tracks.get(0), err -> {
                gateActive = false;
                if (onError != null) onError.accept(err);
            });
        });
    }

    /** Play a concrete track. Async. */
    public void play(Track track, Consumer<String> onError) {
        if (track == null || track.trackUrl == null) {
            if (onError != null) onError.accept("Track tidak memiliki URL.");
            return;
        }
        if (state.current != null) {
            backStack.push(state.current);
            forwardStack.clear();
        }
        state.status = PlayerState.Status.CONNECTING;
        ytdlp.resolveStream(track.trackUrl).thenAccept(resolved -> {
            if (resolved.error != null) {
                state.status = PlayerState.Status.ERROR;
                state.errorMessage = resolved.error;
                if (onError != null) onError.accept(resolved.error);
                return;
            }
            engine.play(resolved.streamUrl, track).thenAccept(err -> {
                if (err != null) {
                    state.status = PlayerState.Status.ERROR;
                    state.errorMessage = err;
                    if (onError != null) onError.accept(err);
                    return;
                }
                state.errorMessage = null;
                state.status = PlayerState.Status.PLAYING;
                state.current = track;
                state.lastProgressUpdateMs = System.currentTimeMillis();
                state.progressMs = 0;
            });
        });
    }

    @Override
    public void playPause() {
        if (state.current == null) return;
        boolean paused = !engine.isPaused();
        engine.pause(paused);
        state.status = paused ? PlayerState.Status.PAUSED : PlayerState.Status.PLAYING;
        state.progressMs = engine.positionMs();
        state.lastProgressUpdateMs = System.currentTimeMillis();
    }

    @Override
    public void next() {
        gateActive = false;   // manual skip releases the pipeline gate
        Track fwd = forwardStack.poll();
        if (fwd != null) {
            if (state.current != null) backStack.push(state.current);
            play(fwd, null);
        } else {
            engine.seek(engine.durationMs() > 0 ? engine.durationMs() + 1 : 0);
            // natural end will fire the track-end listener
            engine.stop();
            state.status = PlayerState.Status.CONNECTED;
            state.current = null;
        }
    }

    @Override
    public void previous() {
        Track back = backStack.poll();
        if (back != null) {
            if (state.current != null) forwardStack.push(state.current);
            play(back, null);
        }
    }

    @Override
    public void setVolume(float v) {
        engine.setVolume(v);
        state.volume = v;
    }

    /** Refresh displayed progress from the engine; call from a periodic tick. */
    public void refreshProgress() {
        if (state.current != null && engine.isPlaying()) {
            state.progressMs = engine.positionMs();
            state.lastProgressUpdateMs = System.currentTimeMillis();
            if (state.status != PlayerState.Status.PLAYING) {
                state.status = PlayerState.Status.PLAYING;
            }
        }
    }

    @Override
    public boolean supportsSearch() {
        return true;
    }

    @Override
    public void search(String query, Consumer<List<Track>> callback) {
        ytdlp.search(query, 12).thenAccept(r -> callback.accept(r.tracks));
    }

    public void shutdown() {
        engine.shutdown();
        ytdlp.shutdown();
    }
}
