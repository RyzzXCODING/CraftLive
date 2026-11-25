package com.alpoyy.craftlive.live;

import java.util.function.Consumer;

/**
 * A pluggable TikTok LIVE connection provider. Implementations must never
 * block the Minecraft main thread: connect/disconnect return immediately
 * and events are delivered on the provider's own thread.
 */
public interface LiveProvider {
    /** Provider name, e.g. "EulerStream" or "Mock Mode". */
    String name();

    /** Current state of this provider. */
    ConnectionState state();

    /**
     * Begin connecting. Never blocks. No-op if misconfigured or already
     * connected; call {@link #disconnect()} first to restart.
     */
    void connect(String username, String apiKey, Consumer<LiveEvent> listener);

    /** Stop the connection and release resources. Safe to call anytime. */
    void disconnect();

    /** Last error message for UI display, or null. Never contains the API key. */
    String lastError();

    /** True when this provider needs an API key before connect is possible. */
    boolean requiresApiKey();
}
