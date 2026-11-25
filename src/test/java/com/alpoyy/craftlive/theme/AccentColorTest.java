package com.alpoyy.craftlive.theme;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Accent color system tests: HEX parsing, global propagation, reset,
 * and preservation of unrelated (semantic) colors.
 */
class AccentColorTest {
    @AfterEach
    void reset() {
        CraftLiveTheme.resetAccent();
    }

    // ---- hex parsing ----

    @Test
    void parsesValidFormats() {
        assertEquals(0x4287FF, AccentColor.parseHex("#4287FF"));
        assertEquals(0x4287FF, AccentColor.parseHex("4287FF"));
        assertEquals(0x4287ff, AccentColor.parseHex("4287ff"));
        assertEquals(0x9B59F6, AccentColor.parseHex("#9B59F6"));
    }

    @Test
    void rejectsInvalidInputWithoutThrowing() {
        assertEquals(-1, AccentColor.parseHex(null));
        assertEquals(-1, AccentColor.parseHex(""));
        assertEquals(-1, AccentColor.parseHex("#"));
        assertEquals(-1, AccentColor.parseHex("#12345"));   // 5 digits
        assertEquals(-1, AccentColor.parseHex("#1234567")); // 7 digits
        assertEquals(-1, AccentColor.parseHex("#ZZZZZZ"));  // not hex
        assertEquals(-1, AccentColor.parseHex("#428 7FF")); // space
        assertDoesNotThrow(() -> AccentColor.parseHex(";&#<html>"));
    }

    @Test
    void sanitizeFallsBackToDefault() {
        assertEquals(AccentColor.DEFAULT_RGB, AccentColor.sanitize("garbage"));
        assertEquals(AccentColor.DEFAULT_RGB, AccentColor.sanitize(null));
        assertEquals(0x4287FF, AccentColor.sanitize("#4287FF"));
    }

    @Test
    void toHexRoundTrip() {
        assertEquals("#4287FF", AccentColor.toHex(0x4287FF));
        assertEquals("#C43A3A", AccentColor.toHex(AccentColor.DEFAULT_RGB));
        int parsed = AccentColor.parseHex(AccentColor.toHex(0x12ABCD));
        assertEquals(0x12ABCD, parsed);
    }

    // ---- global accent propagation ----

    @Test
    void setAccentChangesGlobalValue() {
        CraftLiveTheme.setAccent(0x4287FF);
        assertEquals(0x4287FF, CraftLiveTheme.accent());
        CraftLiveTheme.setAccent(0x9B59F6);
        assertEquals(0x9B59F6, CraftLiveTheme.accent());
        CraftLiveTheme.setAccent(0x43C879);
        assertEquals(0x43C879, CraftLiveTheme.accent());
    }

    @Test
    void resetReturnsToCraftLiveRed() {
        CraftLiveTheme.setAccent(0x4287FF);
        CraftLiveTheme.resetAccent();
        assertEquals(CraftLiveTheme.ACCENT, CraftLiveTheme.accent());
        assertEquals(0xC43A3A, CraftLiveTheme.accent());
        assertEquals(CraftLiveTheme.ACCENT_HOVER, CraftLiveTheme.accentHover());
    }

    @Test
    void hoverIsDerivedLighterThanAccent() {
        CraftLiveTheme.setAccent(0x4287FF);
        int hover = CraftLiveTheme.accentHover();
        // hover must be lighter than the accent in every channel
        assertTrue(((hover >> 16) & 0xFF) >= ((0x4287FF >> 16) & 0xFF));
        assertTrue(((hover >> 8) & 0xFF) >= ((0x4287FF >> 8) & 0xFF));
        assertTrue((hover & 0xFF) >= (0x4287FF & 0xFF));
        assertNotEquals(0x4287FF, hover);
    }

    @Test
    void unrelatedSemanticColorsNeverChange() {
        int bg = CraftLiveTheme.BG, panel = CraftLiveTheme.PANEL, panel2 = CraftLiveTheme.PANEL_2;
        int border = CraftLiveTheme.BORDER, text = CraftLiveTheme.TEXT, dim = CraftLiveTheme.TEXT_DIM;
        int live = CraftLiveTheme.LIVE_GREEN, warn = CraftLiveTheme.WARNING, err = CraftLiveTheme.ERROR;

        CraftLiveTheme.setAccent(0x43C879);
        CraftLiveTheme.setAccent(0x9B59F6);

        assertEquals(bg, CraftLiveTheme.BG);
        assertEquals(panel, CraftLiveTheme.PANEL);
        assertEquals(panel2, CraftLiveTheme.PANEL_2);
        assertEquals(border, CraftLiveTheme.BORDER);
        assertEquals(text, CraftLiveTheme.TEXT);
        assertEquals(dim, CraftLiveTheme.TEXT_DIM);
        assertEquals(live, CraftLiveTheme.LIVE_GREEN);
        assertEquals(warn, CraftLiveTheme.WARNING);
        assertEquals(err, CraftLiveTheme.ERROR);
    }

    @Test
    void withAlphaStillWorksWithCustomAccent() {
        CraftLiveTheme.setAccent(0x4287FF);
        int argb = CraftLiveTheme.withAlpha(CraftLiveTheme.accent(), 128);
        assertEquals(0x80, (argb >>> 24) & 0xFF);
        assertEquals(0x4287FF, argb & 0xFFFFFF);
    }
}
