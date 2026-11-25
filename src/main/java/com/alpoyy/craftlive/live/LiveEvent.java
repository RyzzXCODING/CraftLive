package com.alpoyy.craftlive.live;

/**
 * A single TikTok LIVE event, normalized across the real EulerStream
 * provider and the mock provider. Only fields actually delivered by the
 * provider are filled — null/absent means "not provided", never fabricated.
 */
public class LiveEvent {
    public enum Type {
        CHAT, GIFT, LIKE, FOLLOW, JOIN, SHARE, SUBSCRIBE, ROOM_INFO, LIVE_END, CONNECTED, DISCONNECTED
    }

    public Type type;
    public long timestampMs = System.currentTimeMillis();

    // Sender (may be null for system events)
    public String username;      // unique TikTok handle
    public String displayName;   // profile/nickname
    public boolean subscriber;
    public boolean moderator;
    public boolean follower;

    // CHAT
    public String message;

    // GIFT
    public String giftName;
    public int giftCount;
    public int giftDiamonds;

    // LIKE
    public int likeCount;        // likes in this event
    public int totalLikes;       // running total when provided

    // ROOM_INFO
    public int viewers;
    public String roomTitle;

    public static LiveEvent chat(String username, String displayName, String message) {
        LiveEvent e = new LiveEvent();
        e.type = Type.CHAT;
        e.username = username;
        e.displayName = displayName;
        e.message = message;
        return e;
    }

    public static LiveEvent gift(String username, String displayName, String giftName, int count, int diamonds) {
        LiveEvent e = new LiveEvent();
        e.type = Type.GIFT;
        e.username = username;
        e.displayName = displayName;
        e.giftName = giftName;
        e.giftCount = count;
        e.giftDiamonds = diamonds;
        return e;
    }

    public static LiveEvent like(String username, String displayName, int likeCount, int totalLikes) {
        LiveEvent e = new LiveEvent();
        e.type = Type.LIKE;
        e.username = username;
        e.displayName = displayName;
        e.likeCount = likeCount;
        e.totalLikes = totalLikes;
        return e;
    }

    public static LiveEvent social(Type type, String username, String displayName) {
        LiveEvent e = new LiveEvent();
        e.type = type;
        e.username = username;
        e.displayName = displayName;
        return e;
    }

    public static LiveEvent roomInfo(int viewers, String roomTitle) {
        LiveEvent e = new LiveEvent();
        e.type = Type.ROOM_INFO;
        e.viewers = viewers;
        e.roomTitle = roomTitle;
        return e;
    }

    public String senderDisplay() {
        if (displayName != null && !displayName.isBlank()) return displayName;
        if (username != null && !username.isBlank()) return username;
        return "?";
    }

    /** Human-readable one-line summary for chat feed / notifications. */
    public String summary() {
        return switch (type) {
            case CHAT -> message == null ? "" : message;
            case GIFT -> "sent " + (giftCount > 1 ? giftCount + "x " : "") + (giftName == null ? "a gift" : giftName);
            case LIKE -> "liked (" + likeCount + ")";
            case FOLLOW -> "started following";
            case JOIN -> "joined the live";
            case SHARE -> "shared the live";
            case SUBSCRIBE -> "subscribed";
            case ROOM_INFO -> "viewers: " + viewers;
            case LIVE_END -> "LIVE ended";
            case CONNECTED -> "connected";
            case DISCONNECTED -> "disconnected";
        };
    }
}
