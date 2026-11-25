package com.alpoyy.craftlive.music;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Registry of music platform connectors. CraftLive supports ONLY
 * YouTube Music (yt-dlp + direct-stream playback, user-authorized) and
 * Spotify (official Web API) — no other platforms by design.
 */
public class PlatformRegistry {
    private final Map<String, MusicPlatform> platforms = new LinkedHashMap<>();

    public void register(MusicPlatform platform) {
        platforms.put(platform.id(), platform);
    }

    public MusicPlatform get(String id) {
        return platforms.get(id);
    }

    public Map<String, MusicPlatform> all() {
        return platforms;
    }

    public void shutdownAll() {
        for (MusicPlatform p : platforms.values()) {
            try {
                p.stopPolling();
                p.disconnect();
            } catch (Exception ignored) {
            }
        }
    }
}
