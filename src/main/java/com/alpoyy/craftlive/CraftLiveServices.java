package com.alpoyy.craftlive;

import com.alpoyy.craftlive.chat.ChatStore;
import com.alpoyy.craftlive.config.ConfigManager;
import com.alpoyy.craftlive.live.LiveConnectionManager;
import com.alpoyy.craftlive.music.PlatformRegistry;
import com.alpoyy.craftlive.notify.NotificationBus;
import com.alpoyy.craftlive.request.RequestManager;
import com.alpoyy.craftlive.stream.StreamMode;

/**
 * Composition root for CraftLive's client services. Initialized once by
 * the client entrypoint; UI components read from here.
 * Pure-logic components remain independently constructible for tests.
 */
public final class CraftLiveServices {
    private static volatile CraftLiveServices instance;

    public final ConfigManager configManager;
    public final com.alpoyy.craftlive.config.CraftLiveConfig config;
    public final LiveConnectionManager live;
    public final RequestManager requests;
    public final ChatStore chat;
    public final NotificationBus notifications;
    public final StreamMode streamMode;
    public final PlatformRegistry platforms;
    public com.alpoyy.craftlive.hud.WidgetManager widgets;
    public com.alpoyy.craftlive.lyrics.LyricsManager lyrics;
    public com.alpoyy.craftlive.music.youtube.YoutubeMusicConnector youtube;
    public com.alpoyy.craftlive.music.spotify.SpotifyConnector spotify;

    private CraftLiveServices(ConfigManager configManager) {
        this.configManager = configManager;
        this.config = configManager.load();

        // apply the persisted global accent color (invalid → default red)
        com.alpoyy.craftlive.theme.CraftLiveTheme.setAccent(
                com.alpoyy.craftlive.theme.AccentColor.sanitize(config.accentColorHex));

        com.alpoyy.craftlive.live.LiveProvider provider = config.mockMode
                ? new com.alpoyy.craftlive.mock.MockLiveProvider()
                : new com.alpoyy.craftlive.live.EulerStreamProvider();
        this.live = new LiveConnectionManager(config, provider);
        this.requests = new RequestManager(config);
        this.chat = new ChatStore(System::currentTimeMillis);
        this.chat.configure(config.chatMaxMessages, config.chatShowGifts, config.chatShowFollows,
                config.chatShowJoins, config.chatSpamProtection, config.chatSpamThreshold,
                config.chatHighlightWords);
        this.notifications = new NotificationBus(System::currentTimeMillis,
                config.maxNotifications, config.notificationDisplaySeconds * 1000L);
        this.streamMode = new StreamMode(config);
        this.platforms = new PlatformRegistry();
        this.lyrics = new com.alpoyy.craftlive.lyrics.LyricsManager();

        this.youtube = new com.alpoyy.craftlive.music.youtube.YoutubeMusicConnector();
        if (config.ytdlpPath != null && !config.ytdlpPath.isBlank()) {
            this.youtube.setBinaryPath(config.ytdlpPath);
        }
        this.spotify = new com.alpoyy.craftlive.music.spotify.SpotifyConnector();
        this.spotify.setClientId(config.spotifyClientId);
        this.spotify.setErrorHandler(msg -> notifications.push(
                com.alpoyy.craftlive.notify.NotificationBus.Notification.Level.ERROR, "Spotify", msg));
        this.platforms.register(this.youtube);
        this.platforms.register(this.spotify);

        this.live.addListener(requests::onLiveEvent);
        this.live.addListener(this::onLiveEvent);
        this.live.addStateListener(state -> {
            if (state == com.alpoyy.craftlive.live.ConnectionState.DISCONNECTED
                    || state == com.alpoyy.craftlive.live.ConnectionState.ERROR) {
                requests.onConnectionLost();
            }
        });
    }

    private void onLiveEvent(com.alpoyy.craftlive.live.LiveEvent event) {
        chat.add(event);
        switch (event.type) {
            case GIFT -> {
                if (config.notificationsEnabled) {
                    notifications.push(NotificationBus.Notification.Level.SUCCESS,
                            "Gift", "@" + event.senderDisplay() + " " + event.summary());
                }
            }
            case FOLLOW -> {
                if (config.notificationsEnabled) {
                    notifications.push(NotificationBus.Notification.Level.INFO,
                            "Follow Baru", "@" + event.senderDisplay());
                }
            }
            case LIVE_END -> {
                notifications.push(NotificationBus.Notification.Level.WARNING, "TikTok LIVE", "Siaran berakhir");
            }
            default -> { }
        }
    }

    public static void initialize(java.nio.file.Path configFilePath) {
        ConfigManager manager = new ConfigManager(configFilePath);
        instance = new CraftLiveServices(manager);
        CraftLive.LOGGER.info("CraftLive services initialized (mockMode={})", instance.config.mockMode);
    }

    public static CraftLiveServices get() {        CraftLiveServices s = instance;
        if (s == null) {
            // headless/test fallback — build with default config path
            synchronized (CraftLiveServices.class) {
                if (instance == null) {
                    instance = new CraftLiveServices(new ConfigManager());
                }
            }
            s = instance;
        }
        return s;
    }

    /** The preferred connected platform, or null when none is connected. */
    public com.alpoyy.craftlive.music.MusicPlatform preferredPlatform() {
        if (!config.preferredPlatform.isBlank()) {
            com.alpoyy.craftlive.music.MusicPlatform p = platforms.get(config.preferredPlatform);
            if (p != null && p.state().status != com.alpoyy.craftlive.music.PlayerState.Status.DISCONNECTED) {
                return p;
            }
        }
        for (com.alpoyy.craftlive.music.MusicPlatform p : platforms.all().values()) {
            if (p.state().status != com.alpoyy.craftlive.music.PlayerState.Status.DISCONNECTED) {
                return p;
            }
        }
        return null;
    }

    public void shutdown() {
        live.shutdown();
        if (youtube != null) youtube.shutdown();
        if (spotify != null) spotify.disconnect();
        platforms.shutdownAll();
        if (widgets != null) widgets.shutdown();
        configManager.saveNow();
        ConfigManager.shutdownSaver();
    }
}
