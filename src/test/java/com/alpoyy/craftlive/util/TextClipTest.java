package com.alpoyy.craftlive.util;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class TextClipTest {
    /** Monospace measurer: 1px per char — deterministic. */
    private final TextClip.Measurer m = String::length;

    @Test
    void fitReturnsWholeWhenSmaller() {
        assertEquals("abc", TextClip.fit("abc", m, 10));
    }

    @Test
    void fitTruncatesToWidth() {
        assertEquals("abc", TextClip.fit("abcdef", m, 3));
        assertEquals("", TextClip.fit("abcdef", m, 0));
    }

    @Test
    void ellipsizeAddsDots() {
        String s = TextClip.ellipsize("abcdef", m, 4);
        assertEquals("abc…", s); // 3 chars + 1px ellipsis = 4
        assertEquals("abcdef", TextClip.ellipsize("abcdef", m, 6));
        assertEquals("abcdef", TextClip.ellipsize("abcdef", m, 100));
    }

    @Test
    void wrapRespectsWidth() {
        List<String> lines = TextClip.wrap("satu dua tiga empat", m, 9);
        for (String line : lines) {
            assertTrue(line.length() <= 9, "line too long: '" + line + "'");
        }
        assertEquals("satu dua", lines.get(0));
        assertEquals("tiga", lines.get(1));
        assertEquals("empat", lines.get(2));
    }

    @Test
    void wrapHardSplitsLongWords() {
        List<String> lines = TextClip.wrap("abcdefghijkl", m, 5);
        for (String line : lines) {
            assertTrue(line.length() <= 5);
        }
        assertEquals("abcde", lines.get(0));
        assertEquals("fghij", lines.get(1));
        assertEquals("kl", lines.get(2));
    }

    @Test
    void wrapEmptyAndNull() {
        assertTrue(TextClip.wrap(null, m, 10).isEmpty());
        assertTrue(TextClip.wrap("", m, 10).isEmpty());
    }

    @Test
    void wrapHandlesNewlines() {
        List<String> lines = TextClip.wrap("aaa\nbbb", m, 10);
        assertEquals(2, lines.size());
        assertEquals("aaa", lines.get(0));
        assertEquals("bbb", lines.get(1));
    }

    // ---- marquee ----

    @Test
    void marqueeInactiveForShortText() {
        TextClip.Marquee mq = new TextClip.Marquee(5, 10, 30f, 100, 100);
        assertFalse(mq.isActive());
        assertEquals(0, mq.offset());
    }

    @Test
    void marqueePausesAtStart() {
        TextClip.Marquee mq = new TextClip.Marquee(50, 10, 20f, 500, 500);
        assertTrue(mq.isActive());
        mq.advance(400);
        assertEquals(0f, mq.offset(), 0.001f); // still in start pause
    }

    @Test
    void marqueeScrollsAfterPause() {
        TextClip.Marquee mq = new TextClip.Marquee(50, 10, 20f, 100, 100);
        mq.advance(100 + 1000); // past start pause, 1s of scrolling
        float offset = mq.offset();
        assertTrue(offset < 0, "should scroll left");
        assertEquals(-20f, offset, 0.51f); // 20px/s * 1s
    }

    @Test
    void marqueePausesAtEndThenResets() {
        TextClip.Marquee mq = new TextClip.Marquee(50, 10, 20f, 0, 200);
        // scroll duration = (50-10)/20 = 2s; cycle = 2s scroll + 0.2s end pause
        mq.advance(1000);                       // mid scroll
        float mid = mq.offset();
        assertTrue(mid < 0 && mid > -40);
        mq.advance(1100);                       // elapsed 2.1s → inside end pause
        assertEquals(-40f, mq.offset(), 0.001f);
        mq.advance(200);                        // elapsed 2.3s → next cycle started
        assertTrue(mq.offset() > -40f, "cycle should restart smoothly, offset=" + mq.offset());
    }

    @Test
    void marqueeReset() {
        TextClip.Marquee mq = new TextClip.Marquee(50, 10, 20f, 0, 0);
        mq.advance(1000);
        assertTrue(mq.offset() < 0);
        mq.reset();
        assertEquals(0f, mq.offset(), 0.001f);
    }
}
