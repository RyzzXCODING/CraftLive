package com.alpoyy.craftlive.render;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.util.Mth;

/**
 * CraftLive rendering utilities on top of {@link GuiGraphics}.
 * Rounded rectangles are drawn with layered fills (cheap, no textures),
 * consistent with the red/black/gray glass theme.
 */
public final class ClRender {
    private ClRender() {
    }

    /** Rounded rectangle via body fill + curved corner bands. */
    public static void roundedRect(GuiGraphics g, int x, int y, int w, int h, int radius, int colorARGB) {
        if (w <= 0 || h <= 0) return;
        if (colorARGB >>> 24 == 0) return; // fully transparent: skip
        if (radius <= 0) {
            g.fill(x, y, x + w, y + h, colorARGB);
            return;
        }
        int r = Math.min(radius, Math.min(w / 2, h / 2));
        // middle band
        g.fill(x, y + r, x + w, y + h - r, colorARGB);
        // top/bottom caps
        g.fill(x + r, y, x + w - r, y + r, colorARGB);
        g.fill(x + r, y + h - r, x + w - r, y + h, colorARGB);
        // corner curves
        for (int i = 0; i < r; i++) {
            int inset = cornerInset(i, r);
            g.fill(x + r - inset, y + i, x + w - r + inset, y + i + 1, colorARGB);
            g.fill(x + r - inset, y + h - i - 1, x + w - r + inset, y + h - i, colorARGB);
        }
    }

    private static int cornerInset(int row, int r) {
        double dx = r - row - 0.5;
        double dy = Math.sqrt(Math.max(0, r * (double) r - dx * dx));
        return (int) Math.round(r - dy);
    }

    /** Rounded rect with 1px border. */
    public static void roundedRectBordered(GuiGraphics g, int x, int y, int w, int h, int radius,
                                           int fillARGB, int borderARGB) {
        roundedRect(g, x, y, w, h, radius, borderARGB);
        roundedRect(g, x + 1, y + 1, w - 2, h - 2, Math.max(0, radius - 1), fillARGB);
    }

    /** Panel with subtle shadow (theme look). */
    public static void panel(GuiGraphics g, int x, int y, int w, int h, int radius,
                             int fillARGB, int borderARGB) {
        // soft shadow
        int shadow = (fillARGB & 0xFF000000) == 0 ? 0x40000000 : ((fillARGB >>> 28) > 12 ? 0x50000000 : 0x30000000);
        g.fill(x + 2, y + h, x + w + 2, y + h + 3, shadow);
        roundedRectBordered(g, x, y, w, h, radius, fillARGB, borderARGB);
    }

    /** Draw a string clipped to maxWidth; returns drawn width. */
    public static int drawClipped(GuiGraphics g, Font font, String text, int x, int y, int maxWidth, int colorARGB, boolean shadow) {
        if (text == null || text.isEmpty()) return 0;
        String clipped = clip(font, text, maxWidth);
        g.drawString(font, clipped, x, y, colorARGB, shadow);
        return font.width(clipped);
    }

    public static String clip(Font font, String text, int maxWidth) {
        if (font.width(text) <= maxWidth) return text;
        String ell = "…";
        int lo = 0;
        int hi = text.length();
        int maxW = maxWidth - font.width(ell);
        while (lo < hi) {
            int mid = (lo + hi + 1) >>> 1;
            if (font.width(text.substring(0, mid)) <= maxW) lo = mid;
            else hi = mid - 1;
        }
        if (lo <= 0) return ell;
        return text.substring(0, lo) + ell;
    }

    /** Multi-line wrap for widgets/GUI using the real font. */
    public static java.util.List<String> wrap(Font font, String text, int maxWidth) {
        java.util.List<String> out = new java.util.ArrayList<>();
        if (text == null || text.isEmpty()) return out;
        for (String para : text.split("\n", -1)) {
            StringBuilder line = new StringBuilder();
            for (String word : para.split(" ")) {
                while (font.width(word) > maxWidth && !word.isEmpty()) {
                    String piece = fitPiece(font, word, maxWidth);
                    if (line.length() > 0) {
                        out.add(line.toString());
                        line.setLength(0);
                    }
                    out.add(piece);
                    word = word.substring(piece.length());
                }
                String candidate = line.length() == 0 ? word : line + " " + word;
                if (font.width(candidate) <= maxWidth) {
                    if (line.length() > 0) line.append(' ');
                    line.append(word);
                } else {
                    if (line.length() > 0) out.add(line.toString());
                    line = new StringBuilder(word);
                }
            }
            out.add(line.toString());
        }
        return out;
    }

    private static String fitPiece(Font font, String word, int maxWidth) {
        int lo = 1;
        int hi = word.length();
        while (lo < hi) {
            int mid = (lo + hi + 1) >>> 1;
            if (font.width(word.substring(0, mid)) <= maxWidth) lo = mid;
            else hi = mid - 1;
        }
        return word.substring(0, Math.max(1, lo));
    }

    /** Progress bar with rounded caps. */
    public static void progressBar(GuiGraphics g, int x, int y, int w, int h, float progress, int bgARGB, int fgARGB) {
        roundedRect(g, x, y, w, h, h / 2, bgARGB);
        int pw = (int) (w * Mth.clamp(progress, 0f, 1f));
        if (pw > h / 2) {
            roundedRect(g, x, y, pw, h, h / 2, fgARGB);
        }
    }

    /** Live pulse alpha (0.55..1) — 1.4s period. */
    public static float livePulse(long epochMs) {
        double phase = (epochMs % 1400) / 1400.0;
        return 0.72f + 0.28f * (float) Math.sin(phase * Math.PI * 2);
    }
}
