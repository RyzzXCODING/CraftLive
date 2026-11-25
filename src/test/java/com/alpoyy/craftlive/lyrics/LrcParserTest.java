package com.alpoyy.craftlive.lyrics;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class LrcParserTest {
    @Test
    void parsesTimestampedLines() {
        String lrc = """
                [00:10.00] baris pertama
                [00:20.50] baris kedua
                [00:35] baris ketiga
                """;
        LrcParser.ParsedLyrics parsed = LrcParser.parse(lrc);
        assertTrue(parsed.valid);
        assertEquals(3, parsed.lines.size());
        assertEquals(10_000, parsed.lines.get(0).timeMs);
        assertEquals("baris pertama", parsed.lines.get(0).text);
        assertEquals(20_500, parsed.lines.get(1).timeMs);
        assertEquals(35_000, parsed.lines.get(2).timeMs);
    }

    @Test
    void sortsLinesByTime() {
        String lrc = """
                [00:20.00] kedua
                [00:10.00] pertama
                """;
        LrcParser.ParsedLyrics parsed = LrcParser.parse(lrc);
        assertEquals("pertama", parsed.lines.get(0).text);
        assertEquals("kedua", parsed.lines.get(1).text);
    }

    @Test
    void multipleTimestampsPerLine() {
        String lrc = "[00:10.00][00:30.00] chorus\n";
        LrcParser.ParsedLyrics parsed = LrcParser.parse(lrc);
        assertEquals(2, parsed.lines.size());
        assertEquals("chorus", parsed.lines.get(0).text);
        assertEquals("chorus", parsed.lines.get(1).text);
    }

    @Test
    void offsetApplied() {
        String lrc = """
                [offset:+1000]
                [00:10.00] line
                """;
        LrcParser.ParsedLyrics parsed = LrcParser.parse(lrc);
        assertEquals(9_000, parsed.lines.get(0).timeMs);
    }

    @Test
    void negativeOffset() {
        String lrc = """
                [offset:-500]
                [00:10.00] line
                """;
        LrcParser.ParsedLyrics parsed = LrcParser.parse(lrc);
        assertEquals(10_500, parsed.lines.get(0).timeMs);
    }

    @Test
    void plainTextFallsBackToManual() {
        String text = "satu\ndua\ntiga\n";
        LrcParser.ParsedLyrics parsed = LrcParser.parse(text);
        assertTrue(parsed.valid);
        assertEquals(3, parsed.lines.size());
        assertEquals("satu", parsed.lines.get(0).text);
        // manual mode: evenly spaced 4s lines
        assertEquals(0, parsed.lines.get(0).timeMs);
        assertEquals(4000, parsed.lines.get(1).timeMs);
    }

    @Test
    void emptyAndNullInvalid() {
        assertFalse(LrcParser.parse(null).valid);
        assertFalse(LrcParser.parse("").valid);
        assertFalse(LrcParser.parse("   ").valid);
    }

    @Test
    void activeLineIndex() {
        List<LrcParser.Line> lines = List.of(
                new LrcParser.Line(0, "a"),
                new LrcParser.Line(10_000, "b"),
                new LrcParser.Line(20_000, "c"));
        assertEquals(-1, LrcParser.activeLineIndex(lines, -1));
        assertEquals(0, LrcParser.activeLineIndex(lines, 0));
        assertEquals(0, LrcParser.activeLineIndex(lines, 9_999));
        assertEquals(1, LrcParser.activeLineIndex(lines, 10_000));
        assertEquals(1, LrcParser.activeLineIndex(lines, 19_999));
        assertEquals(2, LrcParser.activeLineIndex(lines, 20_000));
        assertEquals(2, LrcParser.activeLineIndex(lines, 999_999));
    }
}
