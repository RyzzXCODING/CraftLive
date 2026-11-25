package com.alpoyy.craftlive.live;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Accumulated live statistics. Thread-safe: events arrive on provider
 * threads and are read from the render thread.
 * Only counts what the provider actually reports — no fabricated numbers.
 */
public class LiveStats {
    public final AtomicInteger viewers = new AtomicInteger(0);
    public final AtomicLong totalLikes = new AtomicLong(0);
    public final AtomicLong follows = new AtomicLong(0);
    public final AtomicLong gifts = new AtomicLong(0);
    public final AtomicLong shares = new AtomicLong(0);
    public final AtomicLong joins = new AtomicLong(0);
    public volatile boolean live = false;          // creator streaming right now
    public volatile String roomTitle = "";
    public volatile long lastEventMs = 0;
    public volatile long streamStartMs = 0;

    /** Apply a normalized event to the stats. Returns true if stats changed. */
    public boolean apply(LiveEvent event) {
        if (event == null) return false;
        lastEventMs = System.currentTimeMillis();
        switch (event.type) {
            case LIKE -> {
                if (event.totalLikes > 0) {
                    totalLikes.set(event.totalLikes);
                } else if (event.likeCount > 0) {
                    totalLikes.addAndGet(event.likeCount);
                }
                return true;
            }
            case FOLLOW -> {
                follows.incrementAndGet();
                return true;
            }
            case GIFT -> {
                gifts.addAndGet(Math.max(1, event.giftCount));
                return true;
            }
            case SHARE -> {
                shares.incrementAndGet();
                return true;
            }
            case JOIN -> {
                joins.incrementAndGet();
                return true;
            }
            case ROOM_INFO -> {
                viewers.set(event.viewers);
                if (event.roomTitle != null) roomTitle = event.roomTitle;
                live = true;
                if (streamStartMs == 0) streamStartMs = System.currentTimeMillis();
                return true;
            }
            case LIVE_END -> {
                live = false;
                viewers.set(0);
                return true;
            }
            default -> {
                return false;
            }
        }
    }

    public void reset() {
        viewers.set(0);
        totalLikes.set(0);
        follows.set(0);
        gifts.set(0);
        shares.set(0);
        joins.set(0);
        live = false;
        roomTitle = "";
        lastEventMs = 0;
        streamStartMs = 0;
    }
}
