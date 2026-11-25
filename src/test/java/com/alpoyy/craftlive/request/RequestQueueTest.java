package com.alpoyy.craftlive.request;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * FIFO queue behavior: capacity, ordering, duplicates, cooldown,
 * per-user limits, pause, removal, clear, position.
 */
class RequestQueueTest {
    private RequestQueue queue;
    private long now = 100_000L;

    @BeforeEach
    void setUp() {
        queue = new RequestQueue(3, 30_000, 1, true);
        SongRequest.resetIdCounterForTests();
    }

    private SongRequest request(String user, String track) {
        return new SongRequest(user, track, "", track, now);
    }

    @Test
    void fifoOrderPreserved() {
        assertEquals(RequestQueue.AddResult.ADDED, queue.add(request("a", "Song A"), now));
        now += 31_000;
        assertEquals(RequestQueue.AddResult.ADDED, queue.add(request("b", "Song B"), now));
        now += 31_000;
        assertEquals(RequestQueue.AddResult.ADDED, queue.add(request("c", "Song C"), now));

        assertEquals("Song A", queue.poll().track);
        assertEquals("Song B", queue.poll().track);
        assertEquals("Song C", queue.poll().track);
        assertTrue(queue.isEmpty());
    }

    @Test
    void capacityRejected() {
        queue.add(request("a", "A"), now);
        now += 31_000;
        queue.add(request("b", "B"), now);
        now += 31_000;
        queue.add(request("c", "C"), now);
        now += 31_000;
        assertEquals(RequestQueue.AddResult.FULL, queue.add(request("d", "D"), now));
        assertEquals(3, queue.size());
    }

    @Test
    void duplicateRejected() {
        queue.add(request("a", "Sial"), now);
        now += 31_000;
        assertEquals(RequestQueue.AddResult.DUPLICATE, queue.add(request("b", "Sial"), now));
        now += 31_000;
        // different spacing still duplicate (normalized)
        now += 31_000;
        assertEquals(RequestQueue.AddResult.DUPLICATE, queue.add(request("c", "si al"), now));
    }

    @Test
    void duplicateAllowedWhenProtectionOff() {
        queue.configure(3, 30_000, 1, false);
        queue.add(request("a", "Sial"), now);
        now += 31_000;
        assertEquals(RequestQueue.AddResult.ADDED, queue.add(request("b", "Sial"), now));
    }

    @Test
    void cooldownEnforced() {
        // maxPerUser=2 so the cooldown (not the per-user cap) is what rejects
        queue.configure(3, 30_000, 2, true);
        assertEquals(RequestQueue.AddResult.ADDED, queue.add(request("a", "A"), now));
        now += 5_000;
        assertEquals(RequestQueue.AddResult.COOLDOWN, queue.add(request("a", "B"), now));
        now += 26_000; // 31s since first
        assertEquals(RequestQueue.AddResult.ADDED, queue.add(request("a", "B"), now));
    }

    @Test
    void cooldownRemainingMs() {
        queue.add(request("a", "A"), now);
        now += 10_000;
        assertEquals(20_000, queue.cooldownRemainingMs("a", now));
        assertEquals(0, queue.cooldownRemainingMs("unknown", now));
    }

    @Test
    void perUserLimitEnforced() {
        queue.configure(10, 0, 1, true); // no cooldown, 1 per user
        assertEquals(RequestQueue.AddResult.ADDED, queue.add(request("a", "A"), now));
        assertEquals(RequestQueue.AddResult.USER_LIMIT, queue.add(request("a", "B"), now));
        // other users still fine
        assertEquals(RequestQueue.AddResult.ADDED, queue.add(request("b", "B"), now));
    }

    @Test
    void userLimitFreedAfterPoll() {
        queue.configure(10, 0, 1, true);
        queue.add(request("a", "A"), now);
        assertEquals(RequestQueue.AddResult.USER_LIMIT, queue.add(request("a", "B"), now));
        queue.poll();
        assertEquals(RequestQueue.AddResult.ADDED, queue.add(request("a", "B"), now));
    }

    @Test
    void pauseBlocksAdds() {
        queue.setPaused(true);
        assertEquals(RequestQueue.AddResult.PAUSED, queue.add(request("a", "A"), now));
        queue.setPaused(false);
        assertEquals(RequestQueue.AddResult.ADDED, queue.add(request("a", "A"), now));
    }

    @Test
    void removeByIdWorks() {
        SongRequest r1 = request("a", "A");
        queue.add(r1, now);
        now += 31_000;
        SongRequest r2 = request("b", "B");
        queue.add(r2, now);

        assertTrue(queue.remove(r1.id));
        assertEquals(1, queue.size());
        assertEquals("B", queue.peek().track);
        assertFalse(queue.remove(999_999));
    }

    @Test
    void positionsAreCorrect() {
        SongRequest r1 = request("a", "A");
        queue.add(r1, now);
        now += 31_000;
        SongRequest r2 = request("b", "B");
        queue.add(r2, now);

        assertEquals(1, queue.position(r1.id));
        assertEquals(2, queue.position(r2.id));
        assertEquals(-1, queue.position(42));
        queue.poll();
        assertEquals(-1, queue.position(r1.id));
        assertEquals(1, queue.position(r2.id));
    }

    @Test
    void clearEmptiesEverything() {
        queue.add(request("a", "A"), now);
        now += 31_000;
        queue.add(request("b", "B"), now);
        queue.clear();
        assertEquals(0, queue.size());
        assertTrue(queue.snapshot().isEmpty());
        // dedup map cleared: same song can be requested again
        assertEquals(RequestQueue.AddResult.ADDED, queue.add(request("a", "A"), now));
    }

    @Test
    void resetCooldowns() {
        queue.add(request("a", "A"), now);
        now += 5_000;
        assertEquals(RequestQueue.AddResult.COOLDOWN, queue.add(request("a", "B"), now));
        queue.resetCooldowns();
        assertEquals(0, queue.cooldownRemainingMs("a", now));
    }

    @Test
    void peekNextReturnsSecond() {
        queue.add(request("a", "A"), now);
        now += 31_000;
        queue.add(request("b", "B"), now);
        assertEquals("A", queue.peek().track);
        assertEquals("B", queue.peekNext().track);
        queue.poll();
        assertEquals("B", queue.peek().track);
        assertNull(queue.peekNext());
    }

    @Test
    void snapshotIsCopy() {
        queue.add(request("a", "A"), now);
        List<SongRequest> snap = queue.snapshot();
        snap.clear();
        assertEquals(1, queue.size());
    }

    @Test
    void skipHeadRemovesFirst() {
        queue.add(request("a", "A"), now);
        now += 31_000;
        queue.add(request("b", "B"), now);
        SongRequest skipped = queue.skipHead();
        assertEquals("A", skipped.track);
        assertEquals(1, queue.size());
    }
}
