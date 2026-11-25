package com.alpoyy.craftlive.request;

import com.alpoyy.craftlive.config.CraftLiveConfig;
import com.alpoyy.craftlive.live.LiveEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Full request pipeline with a fake clock.
 * Semantics: a new request enters the queue, then immediately becomes the
 * "current" request when nothing else is playing (countdown starts).
 * inFlight = waiting queue + current request.
 */
class RequestManagerTest {
    private CraftLiveConfig config;
    private AtomicLong clock;
    private RequestManager manager;
    private final List<String> messages = new ArrayList<>();

    @BeforeEach
    void setUp() {
        SongRequest.resetIdCounterForTests();
        config = new CraftLiveConfig();
        clock = new AtomicLong(1_000_000L);
        manager = new RequestManager(config, clock::get);
        manager.onMessage(messages::add);
        config.requestMaxPerUser = 5;   // focus tests on pipeline, not per-user cap
        manager.applyConfig();
    }

    private int inFlight() {
        return manager.queue().size() + (manager.currentRequest() != null ? 1 : 0);
    }

    private void chat(String user, String text) {
        manager.onLiveEvent(LiveEvent.chat(user, user, text));
    }

    @Test
    void chatCommandBecomesCurrentRequest() {
        chat("budi", "!req Nadir - Sial");
        SongRequest current = manager.currentRequest();
        assertNotNull(current, "first request should immediately become current");
        assertEquals("budi", current.requester);
        assertEquals("Sial", current.track);
        assertEquals("Nadir", current.artist);
        assertTrue(messages.stream().anyMatch(m -> m.contains("diterima")));
    }

    @Test
    void invalidCommandGetsFeedback() {
        chat("budi", "!req");
        assertNull(manager.currentRequest());
        assertEquals(0, manager.queue().size());
        assertTrue(messages.stream().anyMatch(m -> m.contains("!req")));
    }

    @Test
    void queueFullShowsExactMessage() {
        config.requestMaxQueue = 1;
        manager.applyConfig();
        chat("a", "!req A");           // → current
        clock.addAndGet(31_000);
        chat("b", "!req B");           // → waiting (queue 1/1)
        clock.addAndGet(31_000);
        chat("c", "!req C");           // → FULL
        assertTrue(messages.stream().anyMatch(m -> m.contains("Queue penuh (1/1)")),
                "expected queue-full message, got: " + messages);
        assertEquals(1, manager.queue().size());
    }

    @Test
    void duplicateRejectedInPipeline() {
        chat("a", "!req Sial");        // becomes current
        clock.addAndGet(31_000);
        chat("b", "!req Sial");        // same as PLAYING → duplicate
        assertEquals(1, inFlight());   // only the current one
        assertTrue(messages.stream().anyMatch(m -> m.contains("sudah ada di antrean")));
    }

    @Test
    void duplicateInWaitingQueueRejected() {
        chat("a", "!req Sial");            // current (1)
        clock.addAndGet(31_000);
        chat("b", "!req Hindia - Rumah");  // waiting (2)
        clock.addAndGet(31_000);
        chat("c", "!req Rumah");           // different song → allowed (3)
        assertEquals(3, inFlight());
        clock.addAndGet(31_000);
        chat("d", "!req Hindia - Rumah");  // same as waiting → duplicate
        assertEquals(3, inFlight());
        assertTrue(messages.stream().anyMatch(m -> m.contains("sudah ada di antrean")));
    }

    @Test
    void cooldownBlocksRapidSameUser() {
        chat("a", "!req Lagu Satu");
        clock.addAndGet(1_000);
        chat("a", "!req Lagu Dua");
        assertEquals(1, inFlight());
        assertTrue(messages.stream().anyMatch(m -> m.contains("tunggu 30 detik")));
    }

    @Test
    void countdownAndAutoAdvance() {
        chat("a", "!req Lagu Satu");
        assertNotNull(manager.currentRequest());
        assertEquals(config.requestDelayMs, manager.countdownRemainingMs());

        clock.addAndGet(config.requestDelayMs / 2);
        manager.tick();
        assertEquals("Lagu Satu", manager.currentRequest().track);
        assertEquals(config.requestDelayMs / 2, manager.countdownRemainingMs());

        clock.addAndGet(config.requestDelayMs);
        manager.tick();
        assertNull(manager.currentRequest(), "after delay the request completes");
        assertTrue(manager.queue().isEmpty());
    }

    @Test
    void twoRequestsProgressInOrder() {
        chat("a", "!req Lagu Satu");
        clock.addAndGet(31_000);
        chat("b", "!req Lagu Dua");
        assertEquals(2, inFlight());

        manager.tick(); // elapsed 31s > delay → first finishes, second starts
        assertEquals("Lagu Dua", manager.currentRequest().track);
        clock.addAndGet(config.requestDelayMs + 1);
        manager.tick();
        assertNull(manager.currentRequest());
    }

    @Test
    void playbackGateHoldsCurrentRequest() {
        chat("a", "!req Lagu Satu");
        manager.setPlaybackGate(() -> true);   // real audio playing
        clock.addAndGet(config.requestDelayMs * 10);
        manager.tick();
        assertNotNull(manager.currentRequest(), "gate must hold the current request");
        assertEquals(0, manager.countdownRemainingMs());

        manager.setPlaybackGate(() -> false);
        manager.tick();
        assertNull(manager.currentRequest());
    }

    @Test
    void skipCurrentAdvancesQueue() {
        chat("a", "!req Lagu Satu");
        clock.addAndGet(31_000);
        chat("b", "!req Lagu Dua");
        assertEquals("Lagu Satu", manager.currentRequest().track);

        manager.skipCurrent();
        assertEquals("Lagu Dua", manager.currentRequest().track);
        assertEquals(0, manager.queue().size());
        assertEquals(1, inFlight());
    }

    @Test
    void manualRemoveFromQueue() {
        chat("a", "!req Lagu Satu");
        clock.addAndGet(31_000);
        chat("b", "!req Lagu Dua");
        long waitingId = manager.queueSnapshot().get(0).id;
        assertTrue(manager.removeById(waitingId));
        assertEquals(0, manager.queue().size());
        assertFalse(manager.removeById(waitingId));
        assertEquals(1, inFlight()); // current survives
    }

    @Test
    void clearQueueWorksButKeepsCurrent() {
        chat("a", "!req Lagu Satu");
        clock.addAndGet(31_000);
        chat("b", "!req Lagu Dua");
        manager.clearQueue();
        assertEquals(0, manager.queue().size());
        assertNotNull(manager.currentRequest(), "current request keeps playing");
        assertTrue(messages.stream().anyMatch(m -> m.contains("dikosongkan")));
    }

    @Test
    void pauseAndResume() {
        chat("a", "!req Lagu Satu");
        manager.setPaused(true);
        clock.addAndGet(31_000);
        chat("b", "!req Lagu Dua");
        assertEquals(1, inFlight()); // b blocked while paused
        manager.setPaused(false);
        clock.addAndGet(31_000);
        chat("b", "!req Lagu Dua");
        assertEquals(2, inFlight());
        assertTrue(messages.stream().anyMatch(m -> m.contains("dijeda")));
        assertTrue(messages.stream().anyMatch(m -> m.contains("dilanjutkan")));
    }

    @Test
    void queueSurvivesConnectionLoss() {
        chat("a", "!req Lagu Satu");
        clock.addAndGet(31_000);
        chat("b", "!req Lagu Dua");
        assertEquals(2, inFlight());

        manager.onConnectionLost();
        assertEquals(2, inFlight(), "queue must be preserved on disconnect");

        chat("c", "!req Lagu Tiga");
        assertEquals(3, inFlight());
    }

    @Test
    void disabledRequestsIgnoreCommands() {
        config.requestsEnabled = false;
        chat("a", "!req Lagu Satu");
        assertEquals(0, inFlight());
    }

    @Test
    void manualApprovalFlow() {
        config.requestAutoApprove = false;
        chat("a", "!req Lagu Satu");
        assertEquals(0, inFlight());
        assertEquals(1, manager.pendingApproval().size());

        manager.approve(manager.pendingApproval().get(0));
        assertNotNull(manager.currentRequest());
        assertEquals(0, manager.pendingApproval().size());
    }

    @Test
    void rejectFlow() {
        config.requestAutoApprove = false;
        chat("a", "!req Lagu Satu");
        manager.reject(manager.pendingApproval().get(0));
        assertEquals(0, inFlight());
        assertTrue(messages.stream().anyMatch(m -> m.contains("ditolak")));
    }

    @Test
    void positionIncludesPlayingRequest() {
        chat("a", "!req Lagu Satu");    // playing
        clock.addAndGet(31_000);
        chat("b", "!req Lagu Dua");     // position 2 (after the playing one)
        assertTrue(messages.stream().anyMatch(m -> m.contains("Posisi antrean: 2/" + config.requestMaxQueue)),
                "got: " + messages);
    }

    @Test
    void nonCommandChatIgnored() {
        chat("a", "halo semua!");
        assertEquals(0, inFlight());
    }

    @Test
    void nonChatEventsIgnored() {
        manager.onLiveEvent(LiveEvent.like("a", "A", 1, 2));
        manager.onLiveEvent(LiveEvent.gift("a", "A", "Rose", 1, 1));
        manager.onLiveEvent(null);
        assertEquals(0, inFlight());
    }
}
