package com.alpoyy.craftlive.request;

/**
 * One song request from a viewer. Status flows:
 * PENDING → QUEUED → (PLAYING) → DONE / REJECTED / CANCELLED
 */
public class SongRequest {
    public enum Status {
        PENDING("Menunggu"),
        QUEUED("Di antrean"),
        PLAYING("Diputar"),
        DONE("Selesai"),
        REJECTED("Ditolak"),
        CANCELLED("Dibatalkan");

        private final String display;

        Status(String display) {
            this.display = display;
        }

        public String display() {
            return display;
        }
    }

    private static long idCounter = 0;

    public final long id;
    public final String title;       // full requested text, e.g. "Nadir - Sial" or "Sial"
    public final String artist;      // optional parsed artist
    public final String track;       // parsed song name
    public final String requester;   // TikTok username
    public final long requestedAtMs;
    public Status status = Status.PENDING;
    public String rejectReason = "";
    public transient boolean playedViaYt = false;   // auto-play hook fired once

    public SongRequest(String requester, String rawText, String artist, String track, long nowMs) {
        this.id = nextId();
        this.requester = requester == null ? "?" : requester;
        this.title = rawText == null ? "" : rawText.trim();
        this.artist = artist == null ? "" : artist.trim();
        this.track = track == null ? this.title : track.trim();
        this.requestedAtMs = nowMs;
    }

    private static synchronized long nextId() {
        return ++idCounter;
    }

    public static void resetIdCounterForTests() {
        idCounter = 0;
    }

    /** Normalized key for duplicate detection: lowercase, stripped spaces. */
    public String dedupKey() {
        String base = (track + "|" + (artist.isBlank() ? "" : artist)).toLowerCase();
        return base.replaceAll("\\s+", "");
    }
}
