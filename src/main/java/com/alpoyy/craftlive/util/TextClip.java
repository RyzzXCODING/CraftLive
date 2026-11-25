package com.alpoyy.craftlive.util;

/**
 * Text measurement/clipping helpers and the marquee state machine.
 *
 * Pure Java: a "measure" function maps text to pixel width so this class
 * is unit-testable without Minecraft. The renderer passes
 * (text, font::width).
 *
 * Marquee behavior (spec):
 *  - pause at the start
 *  - scroll slowly
 *  - pause at the end
 *  - smooth reset/reverse
 *  - short text: never animated
 */
public final class TextClip {
    public interface Measurer {
        int width(String text);
    }

    private TextClip() {
    }

    /** Binary-search the longest prefix of text that fits maxWidth. */
    public static String fit(String text, Measurer measure, int maxWidth) {
        if (text == null || text.isEmpty()) return "";
        if (measure.width(text) <= maxWidth) return text;
        int lo = 0;
        int hi = text.length();
        while (lo < hi) {
            int mid = (lo + hi + 1) >>> 1;
            if (measure.width(text.substring(0, mid)) <= maxWidth) {
                lo = mid;
            } else {
                hi = mid - 1;
            }
        }
        if (lo <= 0) return "";
        if (lo >= text.length()) return text;
        return text.substring(0, lo);
    }

    /** Fit with an ellipsis when truncated. */
    public static String ellipsize(String text, Measurer measure, int maxWidth) {
        if (text == null || text.isEmpty()) return "";
        if (measure.width(text) <= maxWidth) return text;
        String fitted = fit(text, measure, Math.max(0, maxWidth - measure.width("…")));
        return fitted + "…";
    }

    /**
     * Greedy word wrap. Returns lines; words longer than maxWidth are
     * hard-split. No line ever exceeds maxWidth.
     */
    public static java.util.List<String> wrap(String text, Measurer measure, int maxWidth) {
        java.util.List<String> out = new java.util.ArrayList<>();
        if (text == null || text.isEmpty()) return out;
        for (String paragraph : text.split("\n", -1)) {
            String[] words = paragraph.split(" ");
            StringBuilder line = new StringBuilder();
            for (String word : words) {
                while (measure.width(word) > maxWidth && !word.isEmpty()) {
                    // hard split long word
                    String fitted = fit(word, measure, maxWidth - (line.length() > 0 ? measure.width(" ") : 0));
                    if (fitted.isEmpty()) {
                        fitted = fit(word, measure, maxWidth);
                    }
                    if (line.length() > 0) {
                        out.add(line.toString());
                        line.setLength(0);
                    }
                    out.add(fitted);
                    word = word.substring(fitted.length());
                }
                String candidate = line.length() == 0 ? word : line + " " + word;
                if (measure.width(candidate) <= maxWidth) {
                    line.append(line.length() == 0 ? word : " " + word);
                } else {
                    if (line.length() > 0) out.add(line.toString());
                    line = new StringBuilder(word);
                }
            }
            out.add(line.toString());
        }
        return out;
    }

    // ---- marquee ----

    /**
     * Marquee state: pauses at start, scrolls, pauses at end, then jumps
     * smoothly back to start. FPS-independent (driven by elapsed ms).
     */
    public static class Marquee {
        public final int textWidth;
        public final int viewWidth;
        private final long startPauseMs;
        private final long endPauseMs;
        private final float pixelsPerSecond;

        private long elapsed = 0;

        public Marquee(int textWidth, int viewWidth) {
            this(textWidth, viewWidth, 30f, 900, 900);
        }

        public Marquee(int textWidth, int viewWidth, float pixelsPerSecond, long startPauseMs, long endPauseMs) {
            this.textWidth = Math.max(0, textWidth);
            this.viewWidth = Math.max(0, viewWidth);
            this.pixelsPerSecond = Math.max(1f, pixelsPerSecond);
            this.startPauseMs = startPauseMs;
            this.endPauseMs = endPauseMs;
        }

        public void advance(long deltaMs) {
            elapsed += deltaMs;
        }

        public void reset() {
            elapsed = 0;
        }

        public boolean isActive() {
            return textWidth > viewWidth;
        }

        /** Current X offset (<= 0) for drawing text so it stays inside the view. */
        public float offset() {
            if (!isActive()) return 0;
            long scrollStart = startPauseMs;
            long totalScrollMs = (long) ((textWidth - viewWidth) / pixelsPerSecond * 1000f);
            long cycle = scrollStart + totalScrollMs + endPauseMs;
            long phase = elapsed % cycle;
            if (phase < scrollStart) return 0;
            phase -= scrollStart;
            if (phase >= totalScrollMs) return -(textWidth - viewWidth);
            return -(phase / 1000f * pixelsPerSecond);
        }
    }
}
