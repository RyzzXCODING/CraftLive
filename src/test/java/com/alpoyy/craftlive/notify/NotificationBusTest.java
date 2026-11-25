package com.alpoyy.craftlive.notify;

import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;

class NotificationBusTest {
    @Test
    void pushAndExpire() {
        AtomicLong clock = new AtomicLong(0);
        NotificationBus bus = new NotificationBus(clock::get, 3, 5000);

        bus.push(NotificationBus.Notification.Level.INFO, "T1", "B1");
        assertEquals(1, bus.active().size());

        clock.addAndGet(6000);
        assertTrue(bus.active().isEmpty(), "toast should expire after display window");
    }

    @Test
    void cappedAtMax() {
        AtomicLong clock = new AtomicLong(0);
        NotificationBus bus = new NotificationBus(clock::get, 3, 5000);
        for (int i = 0; i < 10; i++) {
            bus.push(NotificationBus.Notification.Level.INFO, "T" + i, "B" + i);
        }
        assertEquals(3, bus.active().size());
        // oldest dropped
        assertEquals("T7", bus.active().get(0).title);
    }

    @Test
    void orderingOldestFirst() {
        AtomicLong clock = new AtomicLong(0);
        NotificationBus bus = new NotificationBus(clock::get, 5, 10_000);
        bus.push(NotificationBus.Notification.Level.INFO, "first", "");
        clock.addAndGet(10);
        bus.push(NotificationBus.Notification.Level.SUCCESS, "second", "");
        var active = bus.active();
        assertEquals("first", active.get(0).title);
        assertEquals("second", active.get(1).title);
    }

    @Test
    void clearWorks() {
        AtomicLong clock = new AtomicLong(0);
        NotificationBus bus = new NotificationBus(clock::get, 5, 10_000);
        bus.push(NotificationBus.Notification.Level.INFO, "x", "");
        bus.clear();
        assertTrue(bus.active().isEmpty());
    }

    @Test
    void levelsCarried() {
        AtomicLong clock = new AtomicLong(0);
        NotificationBus bus = new NotificationBus(clock::get, 5, 10_000);
        bus.push(NotificationBus.Notification.Level.ERROR, "err", "");
        assertEquals(NotificationBus.Notification.Level.ERROR, bus.active().get(0).level);
    }
}
