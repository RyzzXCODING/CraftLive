package com.alpoyy.craftlive.music;

import java.util.function.Consumer;

/**
 * A music platform integration. Implementations must do all network work
 * off the Minecraft main thread and never block it.
 *
 * Rules honored by all implementations:
 *  - OAuth / official authorization only; never passwords or cookies.
 *  - No DRM bypass, no unauthorized extraction, no scraping.
 *  - Unsupported features stay unavailable and are reported honestly.
 */
public interface MusicPlatform {
    String id();          // "spotify" / "youtube"
    String displayName();

    PlayerState state();

    boolean isConfigured();                       // has needed credentials/settings
    void connect();                               // async
    void disconnect();                            // clears tokens

    /** Start a background poll loop for currently-playing metadata (if supported). */
    void startPolling(long intervalMs);
    void stopPolling();

    // ---- optional capabilities (default: unsupported) ----
    default boolean supportsPlaybackControl() { return false; }
    default void playPause() { }
    default void next() { }
    default void previous() { }
    default void setVolume(float v) { }

    default boolean supportsSearch() { return false; }
    default void search(String query, Consumer<java.util.List<Track>> callback) { callback.accept(java.util.List.of()); }

    /** Open a track in the user's browser (permitted handoff). */
    default void handoff(Track track) {
        if (track == null || track.trackUrl == null || track.trackUrl.isBlank()) return;
        try {
            java.net.URI uri = java.net.URI.create(track.trackUrl);
            Class<?> mcClass = Class.forName("net.minecraft.client.Minecraft");
            Object mc = mcClass.getMethod("getInstance").invoke(null);
            Object handler = mcClass.getMethod("getLanguageManager").invoke(mc);
            // Delegating to a util that is safe if Minecraft classes are absent (tests)
            com.alpoyy.craftlive.util.BrowserOpen.open(uri);
        } catch (Throwable ignored) {
            // headless/test environment without Minecraft — handoff unavailable
        }
    }
}
