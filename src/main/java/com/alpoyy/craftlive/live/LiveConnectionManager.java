package com.alpoyy.craftlive.live;

import com.alpoyy.craftlive.CraftLive;
import com.alpoyy.craftlive.config.CraftLiveConfig;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * Owns the live connection state machine:
 * dispatches provider events to listeners, tracks stats, and drives
 * reconnection with capped attempts. All provider I/O stays off the
 * Minecraft main thread.
 */
public class LiveConnectionManager implements Consumer<LiveEvent> {
    private final CraftLiveConfig config;
    private volatile LiveProvider provider;
    private final LiveStats stats = new LiveStats();
    private final List<Consumer<LiveEvent>> listeners = new CopyOnWriteArrayList<>();
    private final List<Consumer<ConnectionState>> stateListeners = new CopyOnWriteArrayList<>();

    private ConnectionState state = ConnectionState.NOT_CONFIGURED;
    private String username = "";
    private int reconnectAttempts = 0;
    private long lastConnectAttemptMs = 0;

    public LiveConnectionManager(CraftLiveConfig config, LiveProvider provider) {
        this.config = config;
        this.provider = provider;
    }

    /** Swap the provider (e.g. toggling Mock Mode). Disconnects the old one. */
    public synchronized void setProvider(LiveProvider newProvider) {
        disconnect();
        this.provider = newProvider;
        refreshConfiguredState();
    }

    public LiveProvider provider() {
        return provider;
    }

    public LiveStats stats() {
        return stats;
    }

    public ConnectionState state() {
        return state;
    }

    public String username() {
        return username;
    }

    public void addListener(Consumer<LiveEvent> listener) {
        listeners.add(listener);
    }

    public void addStateListener(Consumer<ConnectionState> listener) {
        stateListeners.add(listener);
    }

    public boolean isMock() {
        return provider.requiresApiKey() == false && "Mock Mode".equals(provider.name());
    }

    // ---- configuration ----

    public void configure(String username) {
        this.username = username == null ? "" : username.trim();
        refreshConfiguredState();
    }

    /** Recompute NOT_CONFIGURED from current config; keeps states otherwise. */
    public void refreshConfiguredState() {
        if (state == ConnectionState.NOT_CONFIGURED || state == ConnectionState.DISCONNECTED) {
            boolean hasUser = !username.isBlank();
            boolean keyNeeded = provider.requiresApiKey();
            state = hasUser ? ConnectionState.DISCONNECTED : ConnectionState.NOT_CONFIGURED;
            if (keyNeeded && hasUser) {
                state = ConnectionState.DISCONNECTED; // key presence checked at connect()
            }
            notifyState();
        }
    }

    // ---- connection control ----

    public void connect() {
        if (state.isBusy() || state == ConnectionState.CONNECTED) {
            return;
        }
        if (username.isBlank()) {
            if (provider.requiresApiKey()) {
                setState(ConnectionState.NOT_CONFIGURED);
                return;
            }
            // mock provider: no username needed
            username = "mock_streamer";
        }
        reconnectAttempts = 0;
        stats.reset();
        setState(ConnectionState.CONNECTING);
        lastConnectAttemptMs = System.currentTimeMillis();
        provider.connect(username, readApiKey(), this);
    }

    /** API key comes from the SecureStore at call time; kept out of config JSON. */
    protected String readApiKey() {
        return com.alpoyy.craftlive.config.SecureStore.getEulerStreamKey();
    }

    public void disconnect() {
        provider.disconnect();
        setState(username.isBlank() ? ConnectionState.NOT_CONFIGURED : ConnectionState.DISCONNECTED);
    }

    public void manualReconnect() {
        disconnect();
        // small delay so provider sockets settle, then connect
        Thread delayThread = new Thread(() -> {
            try {
                Thread.sleep(500);
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
                return;
            }
            connect();
        }, "CraftLive-Reconnect");
        delayThread.setDaemon(true);
        delayThread.start();
    }

    // ---- event pipeline (provider thread) ----

    @Override
    public void accept(LiveEvent event) {
        if (event == null) return;
        switch (event.type) {
            case CONNECTED -> {
                reconnectAttempts = 0;
                setState(ConnectionState.CONNECTED);
            }
            case DISCONNECTED -> {
                setState(ConnectionState.DISCONNECTED);
                scheduleReconnect();
            }
            case LIVE_END -> {
                stats.apply(event);
                // stream ended; keep socket open unless provider drops it
            }
            default -> stats.apply(event);
        }
        for (Consumer<LiveEvent> l : listeners) {
            try {
                l.accept(event);
            } catch (Exception e) {
                CraftLive.LOGGER.error("Live event listener failed", e);
            }
        }
    }

    /** Called by the provider when the underlying connection errored. */
    public void onError(String message) {
        setState(ConnectionState.ERROR);
        scheduleReconnect();
    }

    // ---- reconnection ----

    private void scheduleReconnect() {
        if (!config.autoReconnect) return;
        if (reconnectAttempts >= config.reconnectMaxAttempts) {
            CraftLive.LOGGER.warn("EulerStream: max reconnect attempts reached ({})", reconnectAttempts);
            return;
        }
        long delay = (long) config.reconnectBaseDelayMs * (reconnectAttempts + 1);
        reconnectAttempts++;
        setState(ConnectionState.RECONNECTING);
        CraftLive.LOGGER.info("EulerStream: reconnecting in {}ms (attempt {}/{})", delay, reconnectAttempts, config.reconnectMaxAttempts);
        Thread t = new Thread(() -> {
            try {
                Thread.sleep(delay);
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
                return;
            }
            if (state == ConnectionState.RECONNECTING) {
                lastConnectAttemptMs = System.currentTimeMillis();
                provider.connect(username, readApiKey(), this);
            }
        }, "CraftLive-LiveReconnect");
        t.setDaemon(true);
        t.start();
    }

    // ---- state plumbing ----

    private void setState(ConnectionState newState) {
        if (state == newState) return;
        state = newState;
        notifyState();
    }

    private void notifyState() {
        for (Consumer<ConnectionState> l : stateListeners) {
            try {
                l.accept(state);
            } catch (Exception e) {
                CraftLive.LOGGER.error("Live state listener failed", e);
            }
        }
    }

    public void shutdown() {
        provider.disconnect();
    }
}
