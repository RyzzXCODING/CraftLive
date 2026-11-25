package com.alpoyy.craftlive.sound;

/**
 * Pure cooldown logic for UI sounds — prevents double-fire and spam.
 * Unit-tested; no Minecraft classes.
 */
public class SoundThrottle {
    private final long cooldownMs;
    private final java.util.Map<String, Long> lastPlay = new java.util.HashMap<>();

    public SoundThrottle(long cooldownMs) {
        this.cooldownMs = Math.max(30, cooldownMs);
    }

    /** Returns true when this key may play now (and records the play). */
    public synchronized boolean allow(String key, long nowMs) {
        Long last = lastPlay.get(key);
        if (last != null && nowMs - last < cooldownMs) {
            return false;
        }
        lastPlay.put(key, nowMs);
        return true;
    }

    public synchronized void clear() {
        lastPlay.clear();
    }
}
