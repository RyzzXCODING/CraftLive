package com.alpoyy.craftlive.clock;

import org.junit.jupiter.api.Test;

import java.time.ZoneId;
import java.time.ZonedDateTime;

import static org.junit.jupiter.api.Assertions.*;

class ClockEngineTest {
    private static final ZoneId JAKARTA = ZoneId.of("Asia/Jakarta");

    @Test
    void zoneResolutionFallsBackToSystem() {
        assertEquals(ZoneId.systemDefault(), ClockEngine.resolveZone(""));
        assertEquals(ZoneId.systemDefault(), ClockEngine.resolveZone(null));
        assertEquals(ZoneId.systemDefault(), ClockEngine.resolveZone("Not/AZone"));
        assertEquals(JAKARTA, ClockEngine.resolveZone("Asia/Jakarta"));
    }

    @Test
    void formats24HourWithSeconds() {
        // 2026-11-25 10:30:45 Jakarta
        ZonedDateTime t = ZonedDateTime.of(2026, 11, 25, 10, 30, 45, 0, JAKARTA);
        String s = ClockEngine.formatReal(JAKARTA, true, true, t.toInstant().toEpochMilli());
        assertEquals("10:30:45", s);
    }

    @Test
    void formats24HourNoSeconds() {
        ZonedDateTime t = ZonedDateTime.of(2026, 11, 25, 10, 30, 45, 0, JAKARTA);
        assertEquals("10:30", ClockEngine.formatReal(JAKARTA, true, false, t.toInstant().toEpochMilli()));
    }

    @Test
    void formats12Hour() {
        ZonedDateTime t = ZonedDateTime.of(2026, 11, 25, 15, 5, 0, 0, JAKARTA);
        String s = ClockEngine.formatReal(JAKARTA, false, false, t.toInstant().toEpochMilli());
        assertEquals("03:05 PM", s);
        ZonedDateTime midnight = ZonedDateTime.of(2026, 11, 25, 0, 5, 0, 0, JAKARTA);
        assertEquals("12:05 AM", ClockEngine.formatReal(JAKARTA, false, false, midnight.toInstant().toEpochMilli()));
    }

    @Test
    void zoneLabelsIndonesian() {
        assertEquals("WIB", ClockEngine.zoneLabel(ZoneId.of("Asia/Jakarta")));
        assertEquals("WITA", ClockEngine.zoneLabel(ZoneId.of("Asia/Makassar")));
        assertEquals("WIT", ClockEngine.zoneLabel(ZoneId.of("Asia/Jayapura")));
        assertEquals("Europe/London", ClockEngine.zoneLabel(ZoneId.of("Europe/London")));
    }

    @Test
    void minecraftDayMath() {
        assertEquals(0, ClockEngine.tickOfDay(0));
        assertEquals(0, ClockEngine.tickOfDay(24000));
        assertEquals(500, ClockEngine.tickOfDay(24500));
        assertEquals(23999, ClockEngine.tickOfDay(-1)); // negative wraps

        assertEquals(1, ClockEngine.dayNumber(0));
        assertEquals(1, ClockEngine.dayNumber(23999));
        assertEquals(2, ClockEngine.dayNumber(24000));
        assertEquals(3, ClockEngine.dayNumber(48000));
    }

    @Test
    void minecraftClockFormat() {
        // tick 0 = 06:00 in-game
        assertEquals("06:00", ClockEngine.formatMinecraft(0, true, false));
        // tick 6000 = noon
        assertEquals("12:00", ClockEngine.formatMinecraft(6000, true, false));
        // tick 18000 = midnight (0:00)
        assertEquals("00:00", ClockEngine.formatMinecraft(18000, true, false));
        // 13:00 = tick 7000
        assertEquals("13:00", ClockEngine.formatMinecraft(7000, true, false));
    }

    @Test
    void dayNightIndicator() {
        assertEquals("☀", ClockEngine.dayNightIcon(1000));    // morning
        assertTrue(ClockEngine.isDaytime(6000));               // noon
        assertFalse(ClockEngine.isDaytime(18000));             // midnight
        assertTrue(ClockEngine.isDaytime(23460));              // sunrise
        assertTrue(ClockEngine.describeMinecraft(24000).contains("Hari 2"));
    }

    @Test
    void commonZonesIncludeWib() {
        boolean hasJakarta = false;
        for (String z : ClockEngine.COMMON_ZONES) {
            if (z.equals("Asia/Jakarta")) hasJakarta = true;
            assertDoesNotThrow(() -> ZoneId.of(z));
        }
        assertTrue(hasJakarta);
    }
}
