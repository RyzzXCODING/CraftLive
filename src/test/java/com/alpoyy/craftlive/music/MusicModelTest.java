package com.alpoyy.craftlive.music;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class MusicModelTest {
    @Test
    void trackDurationDisplay() {
        Track t = new Track("Title", "Artist", "Album", 213_000, "YouTube Music");
        assertEquals("3:33", t.durationDisplay());
        assertEquals("Title", t.primaryText());
        assertEquals("Artist — Album", t.secondaryText());
    }

    @Test
    void trackNullSafety() {
        Track t = new Track(null, null, null, -1, null);
        assertEquals("—", t.primaryText());
        assertEquals("--:--", t.durationDisplay());
        assertEquals("", t.secondaryText());
    }

    @Test
    void playerStateProgressInterpolation() {
        PlayerState state = new PlayerState();
        state.current = new Track("T", "A", null, 200_000, "Test");
        state.status = PlayerState.Status.PLAYING;
        state.progressMs = 100_000;
        state.lastProgressUpdateMs = 0;

        assertEquals(105_000, state.displayedProgressMs(5_000));
        assertEquals(100_000, state.displayedProgressMs(0));
        // paused: no interpolation
        state.status = PlayerState.Status.PAUSED;
        assertEquals(100_000, state.displayedProgressMs(5_000));
        // clamps to duration
        state.status = PlayerState.Status.PLAYING;
        assertEquals(200_000, state.displayedProgressMs(999_999));
    }

    @Test
    void playerStateUnknownProgress() {
        PlayerState state = new PlayerState();
        assertEquals(-1, state.displayedProgressMs(1000)); // no track
        state.current = new Track("T", null, null, -1, null);
        assertEquals(-1, state.displayedProgressMs(1000)); // unknown duration
    }

    @Test
    void playerStateStatusDisplay() {
        PlayerState state = new PlayerState();
        assertEquals("Tidak terhubung", state.statusDisplay());
        state.status = PlayerState.Status.PLAYING;
        assertEquals("Memutar", state.statusDisplay());
        state.status = PlayerState.Status.ERROR;
        assertEquals("Error", state.statusDisplay());
    }
}
