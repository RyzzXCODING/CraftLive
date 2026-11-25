package com.alpoyy.craftlive.live;

/**
 * Connection states for the TikTok LIVE provider.
 * Colors per spec: green Connected, yellow Connecting/Reconnecting,
 * red Error/Disconnected, gray Not Configured.
 */
public enum ConnectionState {
    NOT_CONFIGURED("Not Configured"),
    DISCONNECTED("Disconnected"),
    CONNECTING("Connecting"),
    CONNECTED("Connected"),
    RECONNECTING("Reconnecting"),
    ERROR("Error");

    private final String display;

    ConnectionState(String display) {
        this.display = display;
    }

    public String display() {
        return display;
    }

    /** True when events are flowing. */
    public boolean isConnected() {
        return this == CONNECTED;
    }

    /** True while a connection attempt is in progress. */
    public boolean isBusy() {
        return this == CONNECTING || this == RECONNECTING;
    }
}
