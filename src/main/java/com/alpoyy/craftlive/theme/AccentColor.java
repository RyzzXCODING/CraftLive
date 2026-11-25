package com.alpoyy.craftlive.theme;

/**
 * Accent color parsing/validation — pure Java, unit-tested.
 * Accepts "#4287FF", "4287FF", "4287ff". Invalid input never throws.
 */
public final class AccentColor {
    public static final String DEFAULT_HEX = "#C43A3A";
    public static final int DEFAULT_RGB = 0xC43A3A;

    private AccentColor() {
    }

    /** Parse a hex color; returns -1 when invalid (caller decides fallback). */
    public static int parseHex(String input) {
        if (input == null) return -1;
        String s = input.trim();
        if (s.startsWith("#")) s = s.substring(1);
        if (s.length() != 6) return -1;
        int rgb = 0;
        for (int i = 0; i < 6; i++) {
            char c = s.charAt(i);
            int v = Character.digit(c, 16);
            if (v < 0) return -1;
            rgb = (rgb << 4) | v;
        }
        return rgb;
    }

    public static boolean isValid(String input) {
        return parseHex(input) >= 0;
    }

    /** Sanitize any input to a usable RGB: invalid → default red. Never throws. */
    public static int sanitize(String input) {
        int v = parseHex(input);
        return v >= 0 ? v : DEFAULT_RGB;
    }

    /** 0xRRGGBB → "#RRGGBB" (uppercase). */
    public static String toHex(int rgb) {
        return String.format("#%06X", rgb & 0xFFFFFF);
    }
}
