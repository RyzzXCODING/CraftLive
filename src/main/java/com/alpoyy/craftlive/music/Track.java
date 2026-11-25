package com.alpoyy.craftlive.music;

/**
 * A track shown in the Music Player. Only metadata actually provided by
 * the connected platform is filled; unavailable fields stay null and the
 * UI shows honest placeholders (never fake data).
 */
public class Track {
    public String title;
    public String artist;
    public String album;
    public String artworkUrl;     // permitted source only (Spotify CDN via Web API)
    public long durationMs;       // -1 when unknown
    public String platform;       // "Spotify" / "YouTube Music"
    public String trackUrl;       // for permitted handoff (open in browser)

    public Track(String title, String artist, String album, long durationMs, String platform) {
        this.title = title;
        this.artist = artist;
        this.album = album;
        this.durationMs = durationMs;
        this.platform = platform;
    }

    public String durationDisplay() {
        if (durationMs <= 0) return "--:--";
        long totalSec = durationMs / 1000;
        return String.format("%d:%02d", totalSec / 60, totalSec % 60);
    }

    public String primaryText() {
        return title == null || title.isBlank() ? "—" : title;
    }

    public String secondaryText() {
        String a = artist == null || artist.isBlank() ? "" : artist;
        String al = album == null || album.isBlank() ? "" : " — " + album;
        if (a.isEmpty() && al.isEmpty()) return "";
        return a + al;
    }
}
