package com.alpoyy.craftlive.chat;

import com.alpoyy.craftlive.live.LiveEvent;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;

class ChatStoreTest {
    private final AtomicLong clock = new AtomicLong(1000);
    private final ChatStore store = new ChatStore(clock::get);

    private void configure(ChatStore s) {
        s.configure(100, true, true, true, true, 3, "keren,mantap");
    }

    @Test
    void addsAndOrdersEntries() {
        configure(store);
        assertNotNull(store.add(LiveEvent.chat("a", "A", "halo")));
        clock.addAndGet(1000);
        assertNotNull(store.add(LiveEvent.chat("b", "B", "hai")));
        var snap = store.snapshot();
        assertEquals(2, snap.size());
        assertEquals("a", snap.get(0).event.username);
        assertEquals("b", snap.get(1).event.username);
    }

    @Test
    void retentionCapTrimsOldest() {
        store.configure(10, true, true, true, false, 3, "");
        for (int i = 0; i < 20; i++) {
            store.add(LiveEvent.chat("u" + i, "U" + i, "msg " + i));
            clock.addAndGet(1000);
        }
        assertEquals(10, store.size());
        var snap = store.snapshot();
        assertEquals("msg 10", snap.get(0).event.message); // oldest kept
        assertEquals("msg 19", snap.get(9).event.message);
    }

    @Test
    void typeFilters() {
        store.configure(100, false, false, false, false, 3, "");
        assertNull(store.add(LiveEvent.gift("a", "A", "Rose", 1, 1)));
        assertNull(store.add(LiveEvent.social(LiveEvent.Type.FOLLOW, "a", "A")));
        assertNull(store.add(LiveEvent.social(LiveEvent.Type.JOIN, "a", "A")));
        assertNotNull(store.add(LiveEvent.chat("a", "A", "halo")));
        assertNotNull(store.add(LiveEvent.like("a", "A", 1, 2))); // likes always pass
    }

    @Test
    void spamProtectionBlocksFlood() {
        configure(store);
        // threshold 3 per 10s window
        assertNotNull(store.add(LiveEvent.chat("spammer", "S", "1")));
        clock.addAndGet(500);
        assertNotNull(store.add(LiveEvent.chat("spammer", "S", "2")));
        clock.addAndGet(500);
        assertNotNull(store.add(LiveEvent.chat("spammer", "S", "3")));
        clock.addAndGet(500);
        assertNull(store.add(LiveEvent.chat("spammer", "S", "4"))); // blocked
        // other user unaffected
        assertNotNull(store.add(LiveEvent.chat("calm", "C", "halo")));
        // after window passes, allowed again
        clock.addAndGet(11_000);
        assertNotNull(store.add(LiveEvent.chat("spammer", "S", "5")));
    }

    @Test
    void highlightDetection() {
        configure(store);
        var e = store.add(LiveEvent.chat("a", "A", "Ini KEREN banget"));
        assertTrue(e.highlighted);
        var e2 = store.add(LiveEvent.chat("a", "A", "biasa saja"));
        assertFalse(e2.highlighted);
    }

    @Test
    void nullAndInvalidHandled() {
        configure(store);
        assertNull(store.add(null));
        assertNotNull(store.add(LiveEvent.chat("a", null, null)));
    }

    @Test
    void clearAndTrim() {
        configure(store);
        for (int i = 0; i < 10; i++) store.add(LiveEvent.chat("u", "U", "m" + i));
        store.trimTo(3);
        assertEquals(3, store.size());
        store.clear();
        assertEquals(0, store.size());
    }
}
