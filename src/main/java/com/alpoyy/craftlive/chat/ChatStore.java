package com.alpoyy.craftlive.chat;

import com.alpoyy.craftlive.live.LiveEvent;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.LongSupplier;

/**
 * Bounded chat/event store for the Live Chat tab and widget.
 *  - retention cap (config)
 *  - event type filtering (gifts/follows/joins)
 *  - spam protection (sliding window per user)
 *  - highlight words
 *  - pause auto-scroll state lives in the UI, not here
 *
 * Pure Java with injectable clock; unit-tested.
 */
public class ChatStore {
    public static class Entry {
        public final LiveEvent event;
        public final long receivedMs;
        public boolean highlighted;

        Entry(LiveEvent event, long receivedMs) {
            this.event = event;
            this.receivedMs = receivedMs;
        }
    }

    private final Deque<Entry> entries = new ArrayDeque<>();
    private final LongSupplier clock;

    private int maxEntries = 100;
    private boolean showGifts = true;
    private boolean showFollows = true;
    private boolean showJoins = true;
    private boolean spamProtection = true;
    private int spamThreshold = 5;
    private long spamWindowMs = 10_000;
    private final Set<String> highlightWords = new HashSet<>();
    private final Map<String, Deque<Long>> recentPerUser = new HashMap<>();

    public ChatStore(LongSupplier clock) {
        this.clock = clock;
    }

    public void configure(int maxEntries, boolean showGifts, boolean showFollows, boolean showJoins,
                          boolean spamProtection, int spamThreshold, String highlightWordsCsv) {
        this.maxEntries = Math.max(1, maxEntries);
        this.showGifts = showGifts;
        this.showFollows = showFollows;
        this.showJoins = showJoins;
        this.spamProtection = spamProtection;
        this.spamThreshold = Math.max(1, spamThreshold);
        this.highlightWords.clear();
        if (highlightWordsCsv != null) {
            for (String w : highlightWordsCsv.split(",")) {
                String t = w.trim().toLowerCase();
                if (!t.isEmpty()) this.highlightWords.add(t);
            }
        }
    }

    /**
     * Add an event. Returns the stored Entry, or null when filtered out
     * (type filter or spam).
     */
    public synchronized Entry add(LiveEvent event) {
        if (event == null) return null;
        switch (event.type) {
            case GIFT -> { if (!showGifts) return null; }
            case FOLLOW -> { if (!showFollows) return null; }
            case JOIN -> { if (!showJoins) return null; }
            case CHAT -> { /* always kept unless spam */ }
            default -> { }
        }
        long now = clock.getAsLong();

        if (spamProtection && event.type == LiveEvent.Type.CHAT && isSpam(event.username, now)) {
            return null;
        }

        Entry entry = new Entry(event, now);
        entry.highlighted = isHighlighted(event);
        entries.addLast(entry);
        while (entries.size() > maxEntries) {
            entries.removeFirst();
        }
        return entry;
    }

    private boolean isSpam(String username, long now) {
        Deque<Long> recent = recentPerUser.computeIfAbsent(
                username == null ? "" : username.toLowerCase(), k -> new ArrayDeque<>());
        while (!recent.isEmpty() && now - recent.peekFirst() > spamWindowMs) {
            recent.removeFirst();
        }
        recent.addLast(now);
        if (recent.size() > spamThreshold) {
            // drop oldest tracking to avoid unbounded growth on spam floods
            while (recent.size() > spamThreshold * 2) recent.removeFirst();
            return true;
        }
        return false;
    }

    private boolean isHighlighted(LiveEvent event) {
        if (highlightWords.isEmpty()) return false;
        if (event.type == LiveEvent.Type.CHAT && event.message != null) {
            String msg = event.message.toLowerCase();
            for (String w : highlightWords) {
                if (msg.contains(w)) return true;
            }
        }
        return false;
    }

    /** Newest last. */
    public synchronized List<Entry> snapshot() {
        return new ArrayList<>(entries);
    }

    public synchronized int size() {
        return entries.size();
    }

    public synchronized void clear() {
        entries.clear();
        recentPerUser.clear();
    }

    /** Trim memory on disconnect (retention rule). */
    public synchronized void trimTo(int n) {
        while (entries.size() > n) entries.removeFirst();
    }
}
