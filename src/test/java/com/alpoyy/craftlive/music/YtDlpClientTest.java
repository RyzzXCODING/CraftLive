package com.alpoyy.craftlive.music;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for the async yt-dlp availability state machine — the fix for the
 * 5-FPS bug (the old code spawned a probe process on EVERY render frame
 * when yt-dlp was missing, because failures were never cached).
 */
class YtDlpClientTest {

    @Test
    void commandCandidatesIncludeCustomPathFirst() {
        YtDlpClient c = new YtDlpClient();
        c.setBinaryPath("/opt/tools/yt-dlp");
        List<String[]> candidates = c.commandCandidates();
        assertEquals("/opt/tools/yt-dlp", candidates.get(0)[0]);
        // defaults follow
        boolean hasPlain = false, hasPythonModule = false;
        for (String[] cand : candidates) {
            if (cand[0].equals("yt-dlp")) hasPlain = true;
            if (cand.length == 2 && cand[0].equals("python3") && cand[1].equals("yt_dlp")) hasPythonModule = true;
        }
        assertTrue(hasPlain, "must probe plain yt-dlp from PATH");
    }

    @Test
    void commandCandidatesWithoutCustomPathHaveDefaults() {
        YtDlpClient c = new YtDlpClient();
        c.setBinaryPath("");
        List<String[]> candidates = c.commandCandidates();
        assertTrue(candidates.size() >= 4, "must include the default candidates");
        assertEquals("yt-dlp", candidates.get(0)[0]);
        // python module fallback for environments without a standalone binary
        assertTrue(candidates.stream().anyMatch(cmd ->
                cmd.length == 3 && cmd[0].equals("python3") && cmd[1].equals("-m") && cmd[2].equals("yt_dlp")));
    }

    @Test
    void initialStateIsUnknown() {
        YtDlpClient c = new YtDlpClient();
        assertEquals(YtDlpClient.Availability.UNKNOWN, c.availability());
        assertFalse(c.isAvailable());
    }

    @Test
    void resetAvailabilityReturnsToUnknown() {
        YtDlpClient c = new YtDlpClient();
        c.probeAsync();
        c.resetAvailability();
        assertEquals(YtDlpClient.Availability.UNKNOWN, c.availability());
        assertFalse(c.isAvailable());
    }

    @Test
    @Timeout(20)
    void probeCachesFailureInsteadOfReprobing() throws Exception {
        YtDlpClient c = new YtDlpClient();
        c.setBinaryPath("/nonexistent/path/yt-dlp");
        c.probeAsync();
        assertEquals(YtDlpClient.Availability.CHECKING, c.availability(), "must enter CHECKING immediately");

        // wait for the background probe to finish (all candidates fail here)
        long deadline = System.currentTimeMillis() + 15_000;
        while (c.availability() == YtDlpClient.Availability.CHECKING
                && System.currentTimeMillis() < deadline) {
            Thread.sleep(100);
        }
        assertEquals(YtDlpClient.Availability.UNAVAILABLE, c.availability(),
                "failure must be cached — render code must never re-probe per frame");
        assertFalse(c.isAvailable());
        assertTrue(c.unavailableMessage().contains("yt-dlp"), "message must be actionable");
    }

    @Test
    void unavailableMessageIsActionable() {
        YtDlpClient c = new YtDlpClient();
        String msg = c.unavailableMessage();
        assertTrue(msg.contains("Instal"), "must tell the user what to do");
        assertTrue(msg.contains("Periksa Ulang"), "must mention the recheck option");
    }

    @Test
    void searchFailsFastWhenUnavailable() throws Exception {
        YtDlpClient c = new YtDlpClient();
        c.setBinaryPath("/nonexistent/path/yt-dlp");
        // no probe → still UNKNOWN; search must not hang or spawn anything
        YtDlpClient.SearchResult result = c.search("test", 5).get(5, TimeUnit.SECONDS);
        assertNotNull(result.error);
        assertTrue(result.tracks.isEmpty());
        assertTrue(result.error.contains("yt-dlp"));
    }
}
