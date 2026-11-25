package com.alpoyy.craftlive.notify;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.function.LongSupplier;

/**
 * Simple toast-notification queue, capped (performance rule).
 * Pure Java with injectable clock for tests.
 */
public class NotificationBus {
    public static class Notification {
        public enum Level { INFO, SUCCESS, WARNING, ERROR }

        public final Level level;
        public final String title;
        public final String body;
        public final long bornMs;

        Notification(Level level, String title, String body, long bornMs) {
            this.level = level;
            this.title = title;
            this.body = body;
            this.bornMs = bornMs;
        }

        public float ageSeconds(long nowMs) {
            return (nowMs - bornMs) / 1000f;
        }
    }

    private final Deque<Notification> toasts = new ArrayDeque<>();
    private final LongSupplier clock;
    private final int max;
    private final long displayMs;

    public NotificationBus(LongSupplier clock, int max, long displayMs) {
        this.clock = clock;
        this.max = Math.max(1, max);
        this.displayMs = displayMs;
    }

    public synchronized void push(Notification.Level level, String title, String body) {
        toasts.addLast(new Notification(level, title, body, clock.getAsLong()));
        while (toasts.size() > max) {
            toasts.removeFirst();
        }
    }

    /** Expire old toasts and return the currently visible ones (oldest first). */
    public synchronized List<Notification> active() {
        long now = clock.getAsLong();
        while (!toasts.isEmpty() && now - toasts.peekFirst().bornMs > displayMs) {
            toasts.removeFirst();
        }
        return new ArrayList<>(toasts);
    }

    public synchronized void clear() {
        toasts.clear();
    }
}
