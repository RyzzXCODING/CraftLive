package com.alpoyy.craftlive.lyrics;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parses LRC-format lyrics (the standard timestamped lyrics format users
 * provide themselves, e.g. from their own files).
 *
 * Copyright rule: CraftLive never downloads or scrapes lyrics. It renders
 * only user-provided or authorized text.
 *
 * Supported:
 *   [mm:ss.xx] line text
 *   [mm:ss.xxx] line text
 *   [mm:ss] line text
 *   multiple timestamps for one line
 *   [offset:+/-ms] metadata
 */
public final class LrcParser {
    private static final Pattern TIME_TAG = Pattern.compile("\\[(\\d{1,2}):(\\d{1,2})(?:[.:](\\d{1,3}))?]");
    private static final Pattern OFFSET_TAG = Pattern.compile("\\[offset:\\s*([+-]?\\d+)\\s*]", Pattern.CASE_INSENSITIVE);

    private LrcParser() {
    }

    public static class Line {
        public final long timeMs;
        public final String text;

        public Line(long timeMs, String text) {
            this.timeMs = timeMs;
            this.text = text;
        }
    }

    public static class ParsedLyrics {
        public final List<Line> lines;
        public final boolean valid;
        public final String error;

        ParsedLyrics(List<Line> lines, boolean valid, String error) {
            this.lines = lines;
            this.valid = valid;
            this.error = error;
        }
    }

    public static ParsedLyrics parse(String lrcText) {
        if (lrcText == null || lrcText.isBlank()) {
            return new ParsedLyrics(List.of(), false, "Lyrics kosong.");
        }
        long offsetMs = 0;
        Matcher offsetMatcher = OFFSET_TAG.matcher(lrcText);
        if (offsetMatcher.find()) {
            try {
                offsetMs = Long.parseLong(offsetMatcher.group(1));
            } catch (NumberFormatException ignored) {
            }
        }

        List<Line> lines = new ArrayList<>();
        int timedLines = 0;
        for (String raw : lrcText.split("\\R")) {
            Matcher m = TIME_TAG.matcher(raw);
            List<Long> times = new ArrayList<>();
            int lastEnd = 0;
            while (m.find()) {
                try {
                    int min = Integer.parseInt(m.group(1));
                    int sec = Integer.parseInt(m.group(2));
                    String fracStr = m.group(3);
                    long frac = 0;
                    if (fracStr != null) {
                        int value = Integer.parseInt(fracStr);
                        frac = switch (fracStr.length()) {
                            case 1 -> value * 100L;
                            case 2 -> value * 10L;
                            default -> value;
                        };
                    }
                    long ms = (min * 60L + sec) * 1000L + frac;
                    times.add(ms);
                    lastEnd = m.end();
                } catch (NumberFormatException ignored) {
                }
            }
            String text = raw.substring(Math.min(lastEnd, raw.length())).trim();
            if (!times.isEmpty()) {
                timedLines++;
                for (Long t : times) {
                    lines.add(new Line(Math.max(0, t - offsetMs), text));
                }
            }
        }

        if (timedLines == 0) {
            // No timestamps: treat as manual (unsynced) lyrics
            List<Line> manual = new ArrayList<>();
            long fakeTime = 0;
            for (String raw : lrcText.split("\\R")) {
                String t = raw.trim();
                if (!t.isEmpty()) {
                    manual.add(new Line(fakeTime, t));
                    fakeTime += 4000;
                }
            }
            if (manual.isEmpty()) {
                return new ParsedLyrics(List.of(), false, "Tidak ada baris lirik.");
            }
            return new ParsedLyrics(manual, true, "");
        }

        lines.sort(Comparator.comparingLong(l -> l.timeMs));
        return new ParsedLyrics(lines, true, "");
    }

    /** Index of the active line for a playback position; -1 before the first. */
    public static int activeLineIndex(List<Line> lines, long positionMs) {
        int active = -1;
        for (int i = 0; i < lines.size(); i++) {
            if (lines.get(i).timeMs <= positionMs) {
                active = i;
            } else {
                break;
            }
        }
        return active;
    }
}
