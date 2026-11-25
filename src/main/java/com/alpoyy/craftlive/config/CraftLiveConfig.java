package com.alpoyy.craftlive.config;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Full CraftLive configuration. Persisted as JSON by {@link ConfigManager}.
 *
 * SECURITY: this file NEVER contains the EulerStream API key or music-platform
 * tokens — those live in {@link SecureStore} (owner-only-permission file outside git).
 */
public class CraftLiveConfig {
    public static final String CONFIG_FILE = "craftlive_config.json";
    public static final int CONFIG_VERSION = 1;

    // ---- General ----
    public boolean mockMode = false;                 // Mock Data Mode (clearly labeled in UI)
    public boolean notificationsEnabled = true;
    public boolean animationsEnabled = true;
    public boolean reducedMotion = false;            // respect reduced-motion preference
    public boolean showNotificationsOnScreen = true;

    // ---- Appearance ----
    public String accentColorHex = com.alpoyy.craftlive.theme.AccentColor.DEFAULT_HEX; // global accent (default red)

    // ---- UI & Interaction sounds ----
    public boolean uiSoundsEnabled = true;
    public float uiSoundVolume = 0.6f;               // separate from music volume
    public boolean uiSoundClick = true;
    public boolean uiSoundToggle = true;
    public boolean uiSoundTab = true;
    public boolean uiSoundMenu = true;
    public boolean uiSoundWidget = true;
    public boolean uiSoundHover = false;             // optional, off by default

    // ---- UI / HUD ----
    public float hudGlobalOpacity = 1.0f;            // 0.1..1
    public float hudGlobalScale = 1.0f;              // 0.5..2
    public boolean hudVisible = true;                // master toggle (T key)
    public boolean hudSnapToGrid = false;
    public int hudGridSize = 8;
    public boolean hudEditorShowGrid = true;
    public boolean guiGlassEffect = true;            // subtle transparency in main GUI

    // Per-widget layout (persisted between sessions)
    public Map<String, WidgetLayout> widgetLayouts = new LinkedHashMap<>();

    // ---- TikTok LIVE / EulerStream ----
    public String tiktokUsername = "";
    public boolean autoReconnect = true;
    public int reconnectMaxAttempts = 10;
    public int reconnectBaseDelayMs = 3000;
    public int connectTimeoutMs = 10000;

    // ---- Live Chat ----
    public int chatMaxMessages = 100;                // retention cap
    public boolean chatShowTimestamps = true;
    public boolean chatShowGifts = true;
    public boolean chatShowFollows = true;
    public boolean chatShowJoins = true;
    public boolean chatSpamProtection = true;
    public int chatSpamThreshold = 5;                // messages per 10s before muted
    public String chatHighlightWords = "";           // comma-separated
    public boolean chatAutoScroll = true;
    public int chatLongMessageWrapWidth = 46;        // chars before wrap in widget

    // ---- Music platforms ----
    public String preferredPlatform = "";            // "", "spotify", "youtube"
    public float musicVolume = 0.8f;                 // Spotify device volume 0..1
    public String spotifyClientId = "";              // user's own Spotify app client id (not a secret)
    public String ytdlpPath = "";                    // empty = "yt-dlp" on PATH
    public boolean showAlbumArt = true;

    // ---- Song Requests ----
    public boolean requestsEnabled = true;
    public boolean requestAutoApprove = true;
    public int requestMaxQueue = 30;
    public int requestDelayMs = 4000;                // 3000..5000 per spec
    public boolean requestDuplicateProtection = true;
    public boolean requestSpamProtection = true;
    public int requestUserCooldownSeconds = 30;
    public int requestMaxPerUser = 1;                // one active queued request per viewer (configurable)
    public boolean requestPauseOnQueueFull = false;
    public String requestQueueFullMessage = "Queue penuh (30/30), coba lagi nanti.";
    public String requestCommand = "!req";

    // ---- Lyrics ----
    public String lyricsMode = "manual";             // "manual" | "sync" (LRC)
    public boolean lyricsAutoScroll = true;
    public int lyricsVisibleLines = 5;
    public boolean lyricsHighlightCurrent = true;

    // ---- Clock ----
    public String clockTimezone = "";                // IANA zone id; empty = system default
    public boolean clock24Hour = true;
    public boolean clockShowSeconds = true;
    public boolean clockShowMinecraftTime = true;
    public boolean clockShowDayCounter = true;
    public boolean clockShowDayNightIndicator = true;
    public boolean clockDualMode = false;            // real + minecraft side by side

    // ---- Stream Mode ----
    public boolean streamModeHideChat = false;
    public boolean streamModeHideStats = false;
    public boolean streamModeHideNotifications = true;
    public boolean streamModeHideConnectionInfo = true;
    public boolean streamModeSimplifyHud = true;
    public boolean streamModeKeepIntegrations = true; // never disconnect platforms

    // ---- Performance ----
    public int maxNotifications = 4;
    public int notificationDisplaySeconds = 5;
    public boolean performanceCullHiddenWidgets = true;
    public int targetEventQueueSize = 512;

    public static class WidgetLayout {
        public float x = 12f;
        public float y = 12f;
        public float scale = 1.0f;
        public float opacity = 1.0f;
        public boolean visible = true;

        public WidgetLayout() {
        }

        public WidgetLayout(float x, float y, boolean visible) {
            this.x = x;
            this.y = y;
            this.visible = visible;
        }
    }

    /** Merge values from a loaded (possibly older/partial) config, ignoring nulls. */
    public void mergeFrom(CraftLiveConfig loaded) {
        if (loaded == null) return;
        // accent: invalid hex from an old/corrupted file falls back to default
        this.accentColorHex = com.alpoyy.craftlive.theme.AccentColor.isValid(loaded.accentColorHex)
                ? com.alpoyy.craftlive.theme.AccentColor.toHex(com.alpoyy.craftlive.theme.AccentColor.sanitize(loaded.accentColorHex))
                : com.alpoyy.craftlive.theme.AccentColor.DEFAULT_HEX;
        this.uiSoundsEnabled = loaded.uiSoundsEnabled;
        this.uiSoundVolume = clamp(loaded.uiSoundVolume, 0f, 1f);
        this.uiSoundClick = loaded.uiSoundClick;
        this.uiSoundToggle = loaded.uiSoundToggle;
        this.uiSoundTab = loaded.uiSoundTab;
        this.uiSoundMenu = loaded.uiSoundMenu;
        this.uiSoundWidget = loaded.uiSoundWidget;
        this.uiSoundHover = loaded.uiSoundHover;
        this.mockMode = loaded.mockMode;
        this.notificationsEnabled = loaded.notificationsEnabled;
        this.animationsEnabled = loaded.animationsEnabled;
        this.reducedMotion = loaded.reducedMotion;
        this.showNotificationsOnScreen = loaded.showNotificationsOnScreen;
        this.hudGlobalOpacity = clamp(loaded.hudGlobalOpacity, 0.05f, 1f);
        this.hudGlobalScale = clamp(loaded.hudGlobalScale, 0.5f, 2f);
        this.hudVisible = loaded.hudVisible;
        this.hudSnapToGrid = loaded.hudSnapToGrid;
        this.hudGridSize = clamp(loaded.hudGridSize, 2, 64);
        this.hudEditorShowGrid = loaded.hudEditorShowGrid;
        this.guiGlassEffect = loaded.guiGlassEffect;
        if (loaded.widgetLayouts != null) this.widgetLayouts = loaded.widgetLayouts;
        this.tiktokUsername = loaded.tiktokUsername == null ? "" : loaded.tiktokUsername;
        this.autoReconnect = loaded.autoReconnect;
        this.reconnectMaxAttempts = clamp(loaded.reconnectMaxAttempts, 0, 100);
        this.reconnectBaseDelayMs = clamp(loaded.reconnectBaseDelayMs, 1000, 60000);
        this.connectTimeoutMs = clamp(loaded.connectTimeoutMs, 2000, 60000);
        this.chatMaxMessages = clamp(loaded.chatMaxMessages, 10, 1000);
        this.chatShowTimestamps = loaded.chatShowTimestamps;
        this.chatShowGifts = loaded.chatShowGifts;
        this.chatShowFollows = loaded.chatShowFollows;
        this.chatShowJoins = loaded.chatShowJoins;
        this.chatSpamProtection = loaded.chatSpamProtection;
        this.chatSpamThreshold = clamp(loaded.chatSpamThreshold, 1, 100);
        this.chatHighlightWords = loaded.chatHighlightWords == null ? "" : loaded.chatHighlightWords;
        this.chatAutoScroll = loaded.chatAutoScroll;
        this.chatLongMessageWrapWidth = clamp(loaded.chatLongMessageWrapWidth, 10, 120);
        this.preferredPlatform = loaded.preferredPlatform == null ? "" : loaded.preferredPlatform;
        this.musicVolume = clamp(loaded.musicVolume, 0f, 1f);
        this.spotifyClientId = loaded.spotifyClientId == null ? "" : loaded.spotifyClientId;
        this.ytdlpPath = loaded.ytdlpPath == null ? "" : loaded.ytdlpPath;
        this.showAlbumArt = loaded.showAlbumArt;
        this.requestsEnabled = loaded.requestsEnabled;
        this.requestAutoApprove = loaded.requestAutoApprove;
        this.requestMaxQueue = clamp(loaded.requestMaxQueue, 1, 200);
        this.requestDelayMs = clamp(loaded.requestDelayMs, 3000, 5000);
        this.requestDuplicateProtection = loaded.requestDuplicateProtection;
        this.requestSpamProtection = loaded.requestSpamProtection;
        this.requestUserCooldownSeconds = clamp(loaded.requestUserCooldownSeconds, 0, 600);
        this.requestMaxPerUser = clamp(loaded.requestMaxPerUser, 1, 10);
        this.requestPauseOnQueueFull = loaded.requestPauseOnQueueFull;
        if (loaded.requestQueueFullMessage != null && !loaded.requestQueueFullMessage.isBlank()) {
            this.requestQueueFullMessage = loaded.requestQueueFullMessage;
        }
        this.requestCommand = loaded.requestCommand == null || loaded.requestCommand.isBlank()
                ? "!req" : loaded.requestCommand.trim().split("\\s+")[0];
        this.lyricsMode = loaded.lyricsMode == null ? "manual" : loaded.lyricsMode;
        this.lyricsAutoScroll = loaded.lyricsAutoScroll;
        this.lyricsVisibleLines = clamp(loaded.lyricsVisibleLines, 3, 15);
        this.lyricsHighlightCurrent = loaded.lyricsHighlightCurrent;
        this.clockTimezone = loaded.clockTimezone == null ? "" : loaded.clockTimezone;
        this.clock24Hour = loaded.clock24Hour;
        this.clockShowSeconds = loaded.clockShowSeconds;
        this.clockShowMinecraftTime = loaded.clockShowMinecraftTime;
        this.clockShowDayCounter = loaded.clockShowDayCounter;
        this.clockShowDayNightIndicator = loaded.clockShowDayNightIndicator;
        this.clockDualMode = loaded.clockDualMode;
        this.streamModeHideChat = loaded.streamModeHideChat;
        this.streamModeHideStats = loaded.streamModeHideStats;
        this.streamModeHideNotifications = loaded.streamModeHideNotifications;
        this.streamModeHideConnectionInfo = loaded.streamModeHideConnectionInfo;
        this.streamModeSimplifyHud = loaded.streamModeSimplifyHud;
        this.streamModeKeepIntegrations = loaded.streamModeKeepIntegrations;
        this.maxNotifications = clamp(loaded.maxNotifications, 1, 10);
        this.notificationDisplaySeconds = clamp(loaded.notificationDisplaySeconds, 2, 30);
        this.performanceCullHiddenWidgets = loaded.performanceCullHiddenWidgets;
        this.targetEventQueueSize = clamp(loaded.targetEventQueueSize, 64, 4096);
    }

    private static float clamp(float v, float min, float max) {
        return v < min ? min : Math.min(v, max);
    }

    private static int clamp(int v, int min, int max) {
        return v < min ? min : Math.min(v, max);
    }
}
