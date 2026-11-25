package com.alpoyy.craftlive.clock;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Real-world and Minecraft world time calculations.
 * Pure Java and deterministic where it matters (testable).
 *
 * Minecraft time: one in-game day = 24000 ticks = 20 real minutes.
 * dayTime ticks since world start; a new day starts at tick multiple of 24000.
 * Day/night: daytime is roughly ticks 0..12000 (sun up), 13000..23000 night
 * with dusk/dawn transitions — we use the vanilla daylight threshold of
 * 12542 (bed usable) for a simple indicator.
 */
public final class ClockEngine {
    public static final int TICKS_PER_DAY = 24000;
    public static final int TICKS_PER_HOUR = 1000;    // 24000 ticks / 24h
    private static final long NIGHT_START_TICK = 13000;
    private static final long DAY_START_TICK = 23460; // sunrise in vanilla

    private ClockEngine() {
    }

    /** Resolve a zone from config (IANA id); empty/invalid falls back to system default. */
    public static ZoneId resolveZone(String configZone) {
        if (configZone == null || configZone.isBlank()) return ZoneId.systemDefault();
        try {
            return ZoneId.of(configZone.trim());
        } catch (Exception e) {
            return ZoneId.systemDefault();
        }
    }

    /** Known quick-pick zones for the settings UI. */
    public static final String[] COMMON_ZONES = {
            "Asia/Jakarta",   // WIB
            "Asia/Makassar",  // WITA
            "Asia/Jayapura",  // WIT
            "Asia/Singapore",
            "Europe/London",
            "America/New_York",
            "America/Los_Angeles",
            "UTC"
    };

    // ---- real time formatting ----

    public static String formatReal(ZoneId zone, boolean hour24, boolean showSeconds, long epochMs) {
        ZonedDateTime t = Instant.ofEpochMilli(epochMs).atZone(zone);
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern(
                hour24
                        ? (showSeconds ? "HH:mm:ss" : "HH:mm")
                        : (showSeconds ? "hh:mm:ss a" : "hh:mm a"));
        return t.format(formatter);
    }

    public static String zoneLabel(ZoneId zone) {
        String id = zone.getId();
        // Friendly WIB/WITA/WIT labels for Indonesian zones
        return switch (id) {
            case "Asia/Jakarta" -> "WIB";
            case "Asia/Makassar" -> "WITA";
            case "Asia/Jayapura" -> "WIT";
            default -> id;
        };
    }

    // ---- Minecraft world time ----

    /** Ticks within the current day (0..23999). */
    public static long tickOfDay(long dayTime) {
        return ((dayTime % TICKS_PER_DAY) + TICKS_PER_DAY) % TICKS_PER_DAY;
    }

    /** Day counter (1-based, like the F3 day count). */
    public static long dayNumber(long dayTime) {
        return Math.floorDiv(dayTime, TICKS_PER_DAY) + 1;
    }

    /** In-game "clock" as HH:mm (24h) from ticks. */
    public static String formatMinecraft(long dayTime, boolean hour24, boolean showSeconds) {
        long tick = tickOfDay(dayTime);
        // 0 ticks = 06:00 in-game (sunrise). Shift so midnight aligns:
        // in-game hour = (tick / 1000 + 6) % 24
        int totalMinutes = (int) ((tick * 60L) / TICKS_PER_HOUR);
        int shifted = (totalMinutes + 6 * 60) % (24 * 60);
        int hour = shifted / 60;
        int minute = shifted % 60;
        if (hour24) {
            return showSeconds ? String.format("%02d:%02d", hour, minute) : String.format("%02d:%02d", hour, minute);
        }
        int hour12 = hour % 12 == 0 ? 12 : hour % 12;
        return String.format("%s%02d:%02d %s", "", hour12, minute, hour < 12 ? "AM" : "PM");
    }

    /** True when the sun is up (vanilla thresholds). */
    public static boolean isDaytime(long dayTime) {
        long tick = tickOfDay(dayTime);
        return tick < NIGHT_START_TICK || tick >= DAY_START_TICK;
    }

    /** ☀ / ☾ indicator. */
    public static String dayNightIcon(long dayTime) {
        return isDaytime(dayTime) ? "☀" : "☾";
    }

    /** Human label: "Hari 12 — Siang" etc. */
    public static String describeMinecraft(long dayTime) {
        return "Hari " + dayNumber(dayTime) + (isDaytime(dayTime) ? " — Siang" : " — Malam");
    }
}
