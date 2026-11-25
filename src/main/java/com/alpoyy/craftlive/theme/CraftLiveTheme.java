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
    public static final int ACCENT = 0xC43A3A;        // DEFAULT red accent (CraftLive brand)
    public static final int ACCENT_HOVER = 0xE05252;  // DEFAULT accent hover

    // Global accent: one centralized value every accent-colored element reads.
    // Defaults to the CraftLive red; user-customizable via Settings → Appearance.
    private static volatile int accent = ACCENT;
    private static volatile int accentHover = ACCENT_HOVER;

    /** Current global accent color (0xRRGGBB). */
    public static int accent() {
        return accent;
    }

    /** Current global accent hover (auto-derived: accent mixed toward white). */
    public static int accentHover() {
        return accentHover;
    }

    /** Change the global accent instantly — every accent element follows. */
    public static void setAccent(int rgb) {
        int clean = rgb & 0xFFFFFF;
        accent = clean;
        accentHover = mixTowardWhite(clean, 0.28f);
    }

    /** Back to the CraftLive default red. */
    public static void resetAccent() {
        setAccent(ACCENT);
        accentHover = ACCENT_HOVER; // exact brand hover, not derived
    }

    private static int mixTowardWhite(int rgb, float factor) {
        int r = (rgb >> 16) & 0xFF;
        int g = (rgb >> 8) & 0xFF;
        int b = rgb & 0xFF;
        r = Math.round(r + (255 - r) * factor);
        g = Math.round(g + (255 - g) * factor);
        b = Math.round(b + (255 - b) * factor);
        return (r << 16) | (g << 8) | b;
    }

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
