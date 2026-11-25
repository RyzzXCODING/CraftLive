package com.alpoyy.craftlive.theme;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class CraftLiveThemeTest {
    @Test
    void exactPalettePerSpec() {
        assertEquals(0x171719, CraftLiveTheme.BG);
        assertEquals(0x252629, CraftLiveTheme.PANEL);
        assertEquals(0x393A3E, CraftLiveTheme.PANEL_2);
        assertEquals(0x48494D, CraftLiveTheme.BORDER);
        assertEquals(0xC43A3A, CraftLiveTheme.ACCENT);
        assertEquals(0xE05252, CraftLiveTheme.ACCENT_HOVER);
        assertEquals(0xF1F1F1, CraftLiveTheme.TEXT);
        assertEquals(0xA7A7A7, CraftLiveTheme.TEXT_DIM);
        assertEquals(0x43C879, CraftLiveTheme.LIVE_GREEN);
        assertEquals(0xD9A441, CraftLiveTheme.WARNING);
        assertEquals(0xE05B5B, CraftLiveTheme.ERROR);
    }

    @Test
    void withAlphaClamps() {
        assertEquals(0x00C43A3A, CraftLiveTheme.withAlpha(0xC43A3A, 0));
        assertEquals(0xFFC43A3A, CraftLiveTheme.withAlpha(0xC43A3A, 255));
        assertEquals(0x80C43A3A, CraftLiveTheme.withAlpha(0xC43A3A, 128));
        assertEquals(0xFFC43A3A, CraftLiveTheme.withAlpha(0xC43A3A, 300)); // clamped high
        assertEquals(0x00C43A3A, CraftLiveTheme.withAlpha(0xC43A3A, -5)); // clamped low
    }

    @Test
    void floatAlpha() {
        assertEquals(0x00C43A3A, CraftLiveTheme.withAlpha(0xC43A3A, 0f));
        assertEquals(0xFFC43A3A, CraftLiveTheme.withAlpha(0xC43A3A, 1f));
        int half = CraftLiveTheme.withAlpha(0xC43A3A, 0.5f);
        assertEquals(0x7F, (half >>> 24) & 0xFF, 1);
    }

    @Test
    void connectionStateColorsMatchSpec() {
        assertEquals(CraftLiveTheme.LIVE_GREEN, CraftLiveTheme.connectionStateColor("CONNECTED"));
        assertEquals(CraftLiveTheme.WARNING, CraftLiveTheme.connectionStateColor("CONNECTING"));
        assertEquals(CraftLiveTheme.WARNING, CraftLiveTheme.connectionStateColor("RECONNECTING"));
        assertEquals(CraftLiveTheme.ERROR, CraftLiveTheme.connectionStateColor("ERROR"));
        assertEquals(CraftLiveTheme.ERROR, CraftLiveTheme.connectionStateColor("DISCONNECTED"));
        assertEquals(CraftLiveTheme.TEXT_DIM, CraftLiveTheme.connectionStateColor("NOT_CONFIGURED"));
    }
}
