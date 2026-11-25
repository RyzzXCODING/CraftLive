package com.alpoyy.craftlive.stream;

import com.alpoyy.craftlive.config.CraftLiveConfig;

/**
 * Stream Mode: simplifies the HUD for streaming WITHOUT disconnecting
 * platforms or shutting down background systems.
 * Configurable per spec: hide chat/stats/notifications/connection info,
 * simplify widgets; integrations keep running; queue state preserved.
 */
public class StreamMode {
    private final CraftLiveConfig config;
    private boolean active = false;

    public StreamMode(CraftLiveConfig config) {
        this.config = config;
    }

    public boolean isActive() {
        return active;
    }

    /** Toggle and return the new state. */
    public boolean toggle() {
        setActive(!active);
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    // ---- per-widget visibility resolution ----

    public boolean shouldShowChat() {
        return active ? !config.streamModeHideChat : true;
    }

    public boolean shouldShowStats() {
        return active ? !config.streamModeHideStats : true;
    }

    public boolean shouldShowNotifications() {
        return active ? !config.streamModeHideNotifications : true;
    }

    /** Mask usernames / connection details on HUD widgets. */
    public boolean shouldHideConnectionInfo() {
        return active && config.streamModeHideConnectionInfo;
    }

    /** Simplified rendering: fewer decorations, compact layout. */
    public boolean shouldSimplify() {
        return active && config.streamModeSimplifyHud;
    }

    /** Integrations always keep running in Stream Mode. */
    public boolean keepIntegrationsRunning() {
        return true;
    }
}
