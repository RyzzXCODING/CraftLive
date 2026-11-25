package com.alpoyy.craftlive.request;

import com.alpoyy.craftlive.live.LiveEvent;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Song Request command parsing tests.
 */
class RequestParserTest {
    private final RequestParser parser = new RequestParser("!req");
    private final long NOW = 1_000_000L;

    @Test
    void parsesSimpleRequest() {
        RequestParser.Parsed p = parser.parse("budi01", "!req Sial", NOW);
        assertNotNull(p);
        assertTrue(p.valid);
        assertEquals("budi01", p.requester);
        assertEquals("Sial", p.track);
        assertEquals("", p.artist);
    }

    @Test
    void parsesArtistTitleFormat() {
        RequestParser.Parsed p = parser.parse("budi01", "!req Nadir - Sial", NOW);
        assertTrue(p.valid);
        assertEquals("Nadir", p.artist);
        assertEquals("Sial", p.track);
        assertEquals("Nadir - Sial", p.raw);
    }

    @Test
    void parsesExtraSpaces() {
        RequestParser.Parsed p = parser.parse("budi01", "  !req   Hindia  -  Rumah ke Rumah  ", NOW);
        assertTrue(p.valid);
        assertEquals("Hindia", p.artist);
        assertEquals("Rumah ke Rumah", p.track);
    }

    @Test
    void caseInsensitiveCommand() {
        RequestParser.Parsed p = parser.parse("budi01", "!REQ Sial", NOW);
        assertTrue(p.valid);
        assertEquals("Sial", p.track);
    }

    @Test
    void missingSpaceStillParses() {
        RequestParser.Parsed p = parser.parse("budi01", "!reqArtis - Judul", NOW);
        assertTrue(p.valid);
        assertEquals("Artis", p.artist);
        assertEquals("Judul", p.track);
    }

    @Test
    void emptyRequestInvalid() {
        RequestParser.Parsed p = parser.parse("budi01", "!req", NOW);
        assertNotNull(p);
        assertFalse(p.valid);
        assertNotNull(p.invalidReason);
        assertFalse(p.invalidReason.isBlank());
    }

    @Test
    void blankRequestInvalid() {
        RequestParser.Parsed p = parser.parse("budi01", "!req    ", NOW);
        assertFalse(p.valid);
    }

    @Test
    void nonCommandReturnsNullSilently() {
        assertNull(parser.parse("budi01", "halo semua!", NOW));
        assertNull(parser.parse("budi01", "", NOW));
        // null message / missing username → invalid result (never crashes), not null
        assertFalse(parser.parse("budi01", null, NOW).valid);
        assertFalse(parser.parse(null, "!req Sial", NOW).valid);
        assertFalse(parser.parse("", "!req Sial", NOW).valid);
    }

    @Test
    void parsesFromLiveEvent() {
        LiveEvent e = LiveEvent.chat("budi01", "Budi", "!req Tulus - Hati-Hati di Jalan");
        RequestParser.Parsed p = parser.parse(e, NOW);
        assertNotNull(p);
        assertTrue(p.valid);
        assertEquals("Tulus", p.artist);
        assertEquals("Hati-Hati di Jalan", p.track);
    }

    @Test
    void ignoresNonChatEvents() {
        LiveEvent like = LiveEvent.like("budi01", "Budi", 3, 10);
        assertNull(parser.parse(like, NOW));
        LiveEvent follow = LiveEvent.social(LiveEvent.Type.FOLLOW, "budi01", "Budi");
        assertNull(parser.parse(follow, NOW));
    }

    @Test
    void customCommand() {
        RequestParser p2 = new RequestParser("!song");
        assertTrue(p2.parse("budi01", "!song Sial", NOW).valid);
        assertNull(p2.parse("budi01", "!req Sial", NOW));
    }

    @Test
    void longTrackIsTruncated() {
        String longName = "x".repeat(500);
        RequestParser.Parsed p = parser.parse("budi01", "!req " + longName, NOW);
        assertTrue(p.valid);
        assertEquals(120, p.track.length());
    }

    @Test
    void emDashArtistSplit() {
        RequestParser.Parsed p = parser.parse("budi01", "!req Bernadya – Untungnya", NOW);
        assertTrue(p.valid);
        assertEquals("Bernadya", p.artist);
        assertEquals("Untungnya", p.track);
    }
}
