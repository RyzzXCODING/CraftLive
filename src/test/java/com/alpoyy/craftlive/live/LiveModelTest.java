package com.alpoyy.craftlive.live;

import com.alpoyy.craftlive.mock.MockLiveProvider;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class LiveModelTest {
    @Test
    void connectionStateTransitions() {
        assertFalse(ConnectionState.NOT_CONFIGURED.isConnected());
        assertTrue(ConnectionState.CONNECTED.isConnected());
        assertTrue(ConnectionState.CONNECTING.isBusy());
        assertTrue(ConnectionState.RECONNECTING.isBusy());
        assertFalse(ConnectionState.CONNECTED.isBusy());
        assertFalse(ConnectionState.ERROR.isBusy());
        // display strings for UI
        assertEquals("Not Configured", ConnectionState.NOT_CONFIGURED.display());
        assertEquals("Connected", ConnectionState.CONNECTED.display());
    }

    @Test
    void eventFactoriesFillFields() {
        LiveEvent chat = LiveEvent.chat("budi", "Budi", "halo");
        assertEquals(LiveEvent.Type.CHAT, chat.type);
        assertEquals("budi", chat.username);
        assertEquals("Budi", chat.displayName);
        assertEquals("halo", chat.message);

        LiveEvent gift = LiveEvent.gift("a", "A", "Rose", 3, 5);
        assertEquals("Rose", gift.giftName);
        assertEquals(3, gift.giftCount);
        assertEquals(5, gift.giftDiamonds);

        LiveEvent info = LiveEvent.roomInfo(1234, "Room");
        assertEquals(1234, info.viewers);
        assertEquals("Room", info.roomTitle);
    }

    @Test
    void senderDisplayFallsBack() {
        LiveEvent e = LiveEvent.social(LiveEvent.Type.FOLLOW, null, null);
        assertEquals("?", e.senderDisplay());
        LiveEvent e2 = LiveEvent.social(LiveEvent.Type.FOLLOW, "user", "");
        assertEquals("user", e2.senderDisplay());
        LiveEvent e3 = LiveEvent.social(LiveEvent.Type.FOLLOW, "user", "Pretty Name");
        assertEquals("Pretty Name", e3.senderDisplay());
    }

    @Test
    void summaryStringsHonest() {
        assertEquals("started following", LiveEvent.social(LiveEvent.Type.FOLLOW, "a", "a").summary());
        assertTrue(LiveEvent.gift("a", "a", "Rose", 2, 1).summary().contains("2x Rose"));
        assertTrue(LiveEvent.roomInfo(100, "r").summary().contains("100"));
    }

    @Test
    void statsAccumulate() {
        LiveStats stats = new LiveStats();
        assertTrue(stats.apply(LiveEvent.roomInfo(1500, "Room")));
        assertEquals(1500, stats.viewers.get());
        assertTrue(stats.live);

        assertTrue(stats.apply(LiveEvent.like("a", "A", 5, 0)));
        assertEquals(5, stats.totalLikes.get());
        assertTrue(stats.apply(LiveEvent.like("a", "A", 3, 100)));
        assertEquals(100, stats.totalLikes.get()); // running total preferred

        assertTrue(stats.apply(LiveEvent.social(LiveEvent.Type.FOLLOW, "a", "a")));
        assertEquals(1, stats.follows.get());
        assertTrue(stats.apply(LiveEvent.gift("a", "a", "Rose", 2, 1)));
        assertEquals(2, stats.gifts.get());

        assertTrue(stats.apply(LiveEvent.social(LiveEvent.Type.LIVE_END, null, null)));
        assertFalse(stats.live);
        assertEquals(0, stats.viewers.get());
    }

    @Test
    void statsReset() {
        LiveStats stats = new LiveStats();
        stats.apply(LiveEvent.roomInfo(10, "x"));
        stats.apply(LiveEvent.like("a", "a", 1, 100));
        stats.reset();
        assertEquals(0, stats.viewers.get());
        assertEquals(0, stats.totalLikes.get());
        assertFalse(stats.live);
    }

    @Test
    void statsIgnoreNull() {
        LiveStats stats = new LiveStats();
        assertFalse(stats.apply(null));
    }

    @Test
    void mockProviderScriptedScenarioIsSane() {
        List<LiveEvent> scenario = MockLiveProvider.scriptedScenario();
        assertFalse(scenario.isEmpty());
        assertEquals(LiveEvent.Type.ROOM_INFO, scenario.get(0).type);
        assertTrue(scenario.stream().anyMatch(e -> e.type == LiveEvent.Type.CHAT));
        assertTrue(scenario.stream().anyMatch(e -> e.type == LiveEvent.Type.GIFT));
        assertTrue(scenario.stream().anyMatch(e -> e.type == LiveEvent.Type.FOLLOW));
    }
}
