package com.alpoyy.craftlive.theme;

/**
 * CraftLive visual theme — red / black / gray palette.
 * Central place for all colors so the UI stays consistent.
 * Pure Java: unit-testable, no Minecraft dependency.
 */
public final class CraftLiveTheme {
    private CraftLiveTheme() {
    }

    // ---- Backgrounds ----
    public static final int BG = 0x171719;            // app background
    public static final int PANEL = 0x252629;         // main panels
    public static final int PANEL_2 = 0x393A3E;       // secondary panels
    public static final int BORDER = 0x48494D;        // borders

    // ---- Accents ----
    public static final int ACCENT = 0xC43A3A;        // primary red accent
    public static final int ACCENT_HOVER = 0xE05252;  // accent hover

    // ---- Text ----
    public static final int TEXT = 0xF1F1F1;          // main text
    public static final int TEXT_DIM = 0xA7A7A7;      // secondary text

    // ---- Status ----
    public static final int LIVE_GREEN = 0x43C879;    // connected / live
    public static final int WARNING = 0xD9A441;       // connecting / reconnecting
    public static final int ERROR = 0xE05B5B;         // error / disconnected

    /**
     * Apply an 8-bit alpha to a 0xRRGGBB color, returning 0xAARRGGBB.
     * Alpha is clamped to [0,255].
     */
    public static int withAlpha(int rgb, int alpha) {
        int a = alpha;
        if (a < 0) a = 0;
        if (a > 255) a = 255;
        return (a << 24) | (rgb & 0xFFFFFF);
    }

    /**
     * Apply a float alpha (0..1) to a 0xRRGGBB color.
     */
    public static int withAlpha(int rgb, float alpha) {
        return withAlpha(rgb, (int) (alpha * 255f));
    }

    /**
     * Connection state color per spec:
     * Green Connected, Yellow Connecting/Reconnecting, Red Error/Disconnected, Gray Not Configured.
     */
    public static int connectionStateColor(String state) {
        return switch (state) {
            case "CONNECTED", "LIVE" -> LIVE_GREEN;
            case "CONNECTING", "RECONNECTING" -> WARNING;
            case "ERROR", "DISCONNECTED" -> ERROR;
            default -> TEXT_DIM; // NOT_CONFIGURED -> gray
        };
    }
}
