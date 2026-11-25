package com.alpoyy.craftlive.music;

/**
 * Shared playback state for the Music Player UI. Updated asynchronously
 * by platform connectors; read on the render thread. Where a platform
 * cannot report something, the field stays at its "unknown" value and the
 * UI must not fabricate it.
 */
public class PlayerState {
    public enum Status { DISCONNECTED, CONNECTING, CONNECTED, PLAYING, PAUSED, ERROR }

    public volatile Status status = Status.DISCONNECTED;
    public volatile Track current = null;
    public volatile long progressMs = 0;
    public volatile long lastProgressUpdateMs = 0;
    public volatile float volume = 0.8f;      // only applied when platform supports volume
    public volatile boolean volumeSupported = false;
    public volatile boolean controlsSupported = false; // play/pause/next/prev via API
    public volatile String errorMessage = null;

    /** Interpolated progress for smooth UI, or -1 when unknown. */
    public long displayedProgressMs(long nowMs) {
        if (current == null || current.durationMs <= 0) return -1;
        long p = progressMs;
        if (status == Status.PLAYING) {
            p += (nowMs - lastProgressUpdateMs);
            if (p > current.durationMs) p = current.durationMs;
        }
        return p;
    }

    public String statusDisplay() {
        return switch (status) {
            case DISCONNECTED -> "Tidak terhubung";
            case CONNECTING -> "Menghubungkan…";
            case CONNECTED -> "Terhubung";
            case PLAYING -> "Memutar";
            case PAUSED -> "Jeda";
            case ERROR -> "Error";
        };
    }
}
