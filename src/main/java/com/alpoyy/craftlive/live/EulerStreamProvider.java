package com.alpoyy.craftlive.live;

import com.alpoyy.craftlive.CraftLive;
import io.github.jwdeveloper.tiktok.TikTokLive;
import io.github.jwdeveloper.tiktok.data.events.TikTokCommentEvent;
import io.github.jwdeveloper.tiktok.data.events.TikTokConnectedEvent;
import io.github.jwdeveloper.tiktok.data.events.TikTokErrorEvent;
import io.github.jwdeveloper.tiktok.data.events.TikTokLiveEndedEvent;
import io.github.jwdeveloper.tiktok.data.events.TikTokSubscribeEvent;
import io.github.jwdeveloper.tiktok.data.events.gift.TikTokGiftEvent;
import io.github.jwdeveloper.tiktok.data.events.room.TikTokRoomInfoEvent;
import io.github.jwdeveloper.tiktok.data.events.social.TikTokFollowEvent;
import io.github.jwdeveloper.tiktok.data.events.social.TikTokJoinEvent;
import io.github.jwdeveloper.tiktok.data.events.social.TikTokLikeEvent;
import io.github.jwdeveloper.tiktok.data.events.social.TikTokShareEvent;
import io.github.jwdeveloper.tiktok.data.models.users.User;
import io.github.jwdeveloper.tiktok.live.LiveClient;
import io.github.jwdeveloper.tiktok.live.builder.LiveClientBuilder;

import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

/**
 * Real TikTok LIVE connection through EulerStream, using TikTokLiveJava
 * (the Java library documented by EulerStream) with the user's API key.
 *
 * All connection work happens on the library's own threads; this class
 * only translates library events into {@link LiveEvent}s.
 *
 * SECURITY: the API key is passed to the builder only — never logged,
 * never stored in config JSON, never included in exception messages.
 */
public class EulerStreamProvider implements LiveProvider {
    private final AtomicReference<LiveClient> client = new AtomicReference<>(null);
    private volatile Consumer<LiveEvent> listener = e -> {};
    private volatile String lastError = null;
    private volatile ConnectionState state = ConnectionState.NOT_CONFIGURED;

    @Override
    public String name() {
        return "EulerStream";
    }

    @Override
    public ConnectionState state() {
        return state;
    }

    @Override
    public boolean requiresApiKey() {
        return true;
    }

    @Override
    public String lastError() {
        return lastError;
    }

    @Override
    public void connect(String username, String apiKey, Consumer<LiveEvent> out) {
        disconnect();
        this.listener = out;

        if (apiKey == null || apiKey.isBlank()) {
            lastError = "EulerStream API Key belum diatur (Settings → EulerStream).";
            state = ConnectionState.ERROR;
            out.accept(LiveEvent.social(LiveEvent.Type.DISCONNECTED, null, null));
            return;
        }

        state = ConnectionState.CONNECTING;
        lastError = null;
        Thread connectThread = new Thread(() -> {
            try {
                LiveClientBuilder builder = TikTokLive.newClient(username)
                        .configure(settings -> {
                            settings.setApiKey(apiKey);
                        })
                        .onConnecting((c, e) -> state = ConnectionState.CONNECTING)
                        .onConnected(this::handleConnected)
                        .onRoomInfo(this::handleRoomInfo)
                        .onComment(this::handleComment)
                        .onGift(this::handleGift)
                        .onLike(this::handleLike)
                        .onFollow(this::handleFollow)
                        .onJoin(this::handleJoin)
                        .onShare(this::handleShare)
                        .onSubscribe(this::handleSubscribe)
                        .onLiveEnded(this::handleLiveEnded)
                        .onError(this::handleError)
                        .onDisconnected((c, e) -> handleDisconnected());

                LiveClient built = builder.buildAndConnectAsync().join();
                client.set(built);
            } catch (Exception e) {
                // never include the API key in error text
                lastError = sanitizeError(e);
                state = ConnectionState.ERROR;
                CraftLive.LOGGER.warn("EulerStream connect failed: {}", lastError);
                listener.accept(LiveEvent.social(LiveEvent.Type.DISCONNECTED, null, null));
            }
        }, "CraftLive-EulerStreamConnect");
        connectThread.setDaemon(true);
        connectThread.start();
    }

    @Override
    public void disconnect() {
        LiveClient c = client.getAndSet(null);
        if (c != null) {
            try {
                c.disconnect();
            } catch (Exception e) {
                CraftLive.LOGGER.debug("EulerStream disconnect issue: {}", e.toString());
            }
        }
        if (state != ConnectionState.NOT_CONFIGURED) {
            state = ConnectionState.DISCONNECTED;
        }
    }

    // ---- event translation ----

    private void handleConnected(LiveClient c, TikTokConnectedEvent e) {
        state = ConnectionState.CONNECTED;
        listener.accept(LiveEvent.social(LiveEvent.Type.CONNECTED, null, null));
    }

    private void handleRoomInfo(LiveClient c, TikTokRoomInfoEvent e) {
        if (e.getRoomInfo() == null) return;
        int viewers = e.getRoomInfo().getViewersCount();
        LiveEvent ev = LiveEvent.roomInfo(viewers, null);
        state = ConnectionState.CONNECTED;
        listener.accept(ev);
    }

    private void handleComment(LiveClient c, TikTokCommentEvent e) {
        User u = e.getUser();
        listener.accept(LiveEvent.chat(
                u == null ? "?" : u.getName(),
                u == null ? null : u.getProfileName(),
                e.getText()));
    }

    private void handleGift(LiveClient c, TikTokGiftEvent e) {
        User u = e.getUser();
        String giftName = e.getGift() == null ? null : e.getGift().getName();
        int diamonds = e.getGift() == null ? 0 : e.getGift().getDiamondCost();
        int repeat = Math.max(1, e.getCombo());
        listener.accept(LiveEvent.gift(
                u == null ? "?" : u.getName(),
                u == null ? null : u.getProfileName(),
                giftName, repeat, diamonds));
    }

    private void handleLike(LiveClient c, TikTokLikeEvent e) {
        User u = e.getUser();
        listener.accept(LiveEvent.like(
                u == null ? "?" : u.getName(),
                u == null ? null : u.getProfileName(),
                e.getLikes(),
                e.getTotalLikes()));
    }

    private void handleFollow(LiveClient c, TikTokFollowEvent e) {
        User u = e.getUser();
        listener.accept(LiveEvent.social(LiveEvent.Type.FOLLOW,
                u == null ? "?" : u.getName(),
                u == null ? null : u.getProfileName()));
    }

    private void handleJoin(LiveClient c, TikTokJoinEvent e) {
        User u = e.getUser();
        listener.accept(LiveEvent.social(LiveEvent.Type.JOIN,
                u == null ? "?" : u.getName(),
                u == null ? null : u.getProfileName()));
    }

    private void handleShare(LiveClient c, TikTokShareEvent e) {
        User u = e.getUser();
        listener.accept(LiveEvent.social(LiveEvent.Type.SHARE,
                u == null ? "?" : u.getName(),
                u == null ? null : u.getProfileName()));
    }

    private void handleSubscribe(LiveClient c, TikTokSubscribeEvent e) {
        User u = e.getUser();
        listener.accept(LiveEvent.social(LiveEvent.Type.SUBSCRIBE,
                u == null ? "?" : u.getName(),
                u == null ? null : u.getProfileName()));
    }

    private void handleLiveEnded(LiveClient c, TikTokLiveEndedEvent e) {
        listener.accept(LiveEvent.social(LiveEvent.Type.LIVE_END, null, null));
    }

    private void handleError(LiveClient c, TikTokErrorEvent e) {
        if (e != null && e.getException() != null) {
            lastError = sanitizeError(e.getException());
        } else {
            lastError = "Unknown EulerStream error";
        }
        state = ConnectionState.ERROR;
        CraftLive.LOGGER.warn("EulerStream error: {}", lastError);
        scheduleDisconnectedNotice();
    }

    private void handleDisconnected() {
        state = ConnectionState.DISCONNECTED;
        scheduleDisconnectedNotice();
    }

    private void scheduleDisconnectedNotice() {
        Thread t = new Thread(() -> listener.accept(LiveEvent.social(LiveEvent.Type.DISCONNECTED, null, null)),
                "CraftLive-EulerNotify");
        t.setDaemon(true);
        t.start();
    }

    /** Strip anything that could contain credentials from provider errors. */
    private static String sanitizeError(Throwable t) {
        String msg = t.getClass().getSimpleName();
        if (t.getMessage() != null) {
            String m = t.getMessage();
            // Redact any apiKey=... or apiKey value that may appear
            m = m.replaceAll("(?i)(api[_-]?key=)[^&\\s\"']+", "$1***");
            if (m.length() > 300) m = m.substring(0, 300) + "…";
            msg += ": " + m;
        }
        return msg;
    }
}
