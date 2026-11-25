package com.alpoyy.craftlive.request;

import com.alpoyy.craftlive.live.LiveEvent;

/**
 * Parses the "!req <song>" chat command into a {@link SongRequest} input.
 * Pure Java, fully unit-tested.
 *
 * Accepted forms:
 *   "!req judul lagu"
 *   "!req   judul  lagu   "   (extra spaces)
 *   "!req Artis - Judul"      (artist/title split)
 *   "!reqArtis - Judul"       (missing space tolerated)
 *   "!REQ Judul"              (case-insensitive command)
 */
public class RequestParser {
    public static final String DEFAULT_COMMAND = "!req";

    private final String command;

    public RequestParser() {
        this(DEFAULT_COMMAND);
    }

    public RequestParser(String command) {
        this.command = (command == null || command.isBlank()) ? DEFAULT_COMMAND
                : command.trim().toLowerCase();
    }

    public Parsed parse(LiveEvent event, long nowMs) {
        if (event == null || event.type != LiveEvent.Type.CHAT) return null;
        return parse(event.username, event.message, nowMs);
    }

    public Parsed parse(String username, String message, long nowMs) {
        if (username == null || username.isBlank()) return Parsed.invalid(username, "");
        if (message == null) return Parsed.invalid(username, "");

        String trimmed = message.trim();
        String lower = trimmed.toLowerCase();
        if (!lower.startsWith(command)) return null;   // not a request at all — silent

        String rest = trimmed.substring(command.length());
        if (rest.isEmpty() || rest.isBlank()) {
            return Parsed.invalid(username, "Format: " + command + " <judul lagu>");
        }
        rest = rest.trim();

        // Only "!req" alone followed by nothing
        if (rest.equals("-")) {
            return Parsed.invalid(username, "Format: " + command + " <judul lagu>");
        }

        // Artist - Title split on the first " - "
        String artist = "";
        String track = rest;
        int dash = rest.indexOf(" - ");
        if (dash > 0 && dash < rest.length() - 3) {
            artist = rest.substring(0, dash).trim();
            track = rest.substring(dash + 3).trim();
        } else {
            int emDash = rest.indexOf("–");
            if (emDash > 0 && emDash < rest.length() - 1) {
                artist = rest.substring(0, emDash).trim();
                track = rest.substring(emDash + 1).trim();
            }
        }
        if (track.isBlank()) {
            return Parsed.invalid(username, "Judul lagu tidak valid.");
        }
        if (track.length() > 120) {
            track = track.substring(0, 120);
        }
        return Parsed.valid(username, rest, artist, track, nowMs);
    }

    /** Result of parsing. */
    public static class Parsed {
        public final String requester;
        public final String raw;
        public final String artist;
        public final String track;
        public final long atMs;
        public final boolean valid;
        public final String invalidReason;

        private Parsed(String requester, String raw, String artist, String track, long atMs,
                       boolean valid, String invalidReason) {
            this.requester = requester;
            this.raw = raw;
            this.artist = artist;
            this.track = track;
            this.atMs = atMs;
            this.valid = valid;
            this.invalidReason = invalidReason;
        }

        static Parsed invalid(String requester, String reason) {
            return new Parsed(requester, "", "", "", 0, false, reason);
        }

        static Parsed valid(String requester, String raw, String artist, String track, long atMs) {
            return new Parsed(requester, raw, artist, track, atMs, true, "");
        }
    }
}
