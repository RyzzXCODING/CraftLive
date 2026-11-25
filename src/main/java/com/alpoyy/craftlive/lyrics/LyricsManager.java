package com.alpoyy.craftlive.lyrics;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.LongSupplier;

/**
 * Holds the user-provided lyrics (manual text or LRC sync), the playback
 * position source, and computes the active line.
 *
 * Copyright: lyrics come ONLY from the user (pasted text / .lrc file) —
 * CraftLive never downloads lyrics from third-party sources.
 */
public class LyricsManager {
    private final AtomicReference<List<LrcParser.Line>> lines = new AtomicReference<>(List.of());
    private volatile boolean synced = false;          // true when LRC timestamps exist
    private volatile String sourceTitle = "";
    private volatile boolean followPlayback = true;   // sync mode with authorized timestamps
    private volatile long manualOffsetMs = 0;
    private final LongSupplier playbackPosMs;

    public LyricsManager() {
        this(() -> 0L);
    }

    public LyricsManager(LongSupplier playbackPosMs) {
        this.playbackPosMs = playbackPosMs;
    }

    public boolean isEmpty() {
        return lines.get().isEmpty();
    }

    public boolean isSynced() {
        return synced;
    }

    public boolean isFollowing() {
        return followPlayback && synced;
    }

    public String sourceTitle() {
        return sourceTitle;
    }

    public void setFollowPlayback(boolean follow) {
        this.followPlayback = follow;
    }

    public void setManualOffsetMs(long offsetMs) {
        this.manualOffsetMs = offsetMs;
    }

    public long manualOffsetMs() {
        return manualOffsetMs;
    }

    /** Load plain (manual) lyrics — one line per row. */
    public void loadManualText(String title, String text) {
        LrcParser.ParsedLyrics parsed = LrcParser.parse(text);
        lines.set(parsed.lines);
        synced = false;
        sourceTitle = title == null ? "" : title;
    }

    /** Load LRC (sync) lyrics. */
    public void loadLrc(String title, String lrcText) {
        LrcParser.ParsedLyrics parsed = LrcParser.parse(lrcText);
        lines.set(parsed.lines);
        synced = parsed.valid && hasTimestamps(parsed.lines);
        sourceTitle = title == null ? "" : title;
    }

    private static boolean hasTimestamps(List<LrcParser.Line> lines) {
        // manual fallback assigns 4s spacing; if all lines are spaced exactly 4000ms it's manual
        if (lines.size() < 2) return lines.size() == 1 && lines.get(0).timeMs != 0;
        for (int i = 1; i < lines.size(); i++) {
            if (lines.get(i).timeMs != lines.get(i - 1).timeMs + 4000) {
                return true;
            }
        }
        return false;
    }

    public List<LrcParser.Line> lines() {
        return lines.get();
    }

    /** Index of the active line (sync mode), or manual position. */
    public int activeLineIndex() {
        List<LrcParser.Line> ls = lines.get();
        if (ls.isEmpty()) return -1;
        if (isFollowing()) {
            return LrcParser.activeLineIndex(ls, playbackPosMs.getAsLong() + manualOffsetMs);
        }
        // manual mode: user-controlled index
        return Math.max(0, Math.min(manualIndex, ls.size() - 1));
    }

    private volatile int manualIndex = 0;

    public void manualNext() {
        int i = activeLineIndex();
        manualIndex = Math.min(lines().size() - 1, Math.max(0, i + 1));
    }

    public void manualPrev() {
        int i = activeLineIndex();
        manualIndex = Math.max(0, i - 1);
    }

    public void clear() {
        lines.set(List.of());
        synced = false;
        sourceTitle = "";
        manualIndex = 0;
    }
}
