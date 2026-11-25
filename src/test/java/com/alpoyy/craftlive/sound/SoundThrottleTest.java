package com.alpoyy.craftlive.sound;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * UI sound throttle tests: no double-fire, per-key independence,
 * cooldown expiry, and reset.
 */
class SoundThrottleTest {
    @Test
    void firstPlayAllowedSecondBlockedWithinCooldown() {
        SoundThrottle t = new SoundThrottle(100);
        assertTrue(t.allow("ui.click", 1000));
        assertFalse(t.allow("ui.click", 1050)); // 50ms later — blocked
        assertTrue(t.allow("ui.click", 1101));  // 101ms later — ok
    }

    @Test
    void differentKeysAreIndependent() {
        SoundThrottle t = new SoundThrottle(100);
        assertTrue(t.allow("ui.click", 1000));
        assertTrue(t.allow("ui.tab", 1000));     // different sound — allowed
        assertTrue(t.allow("ui.toggle", 1005));  // toggle ON/OFF share "ui.toggle" key
        assertFalse(t.allow("ui.click", 1050));
    }

    @Test
    void minimumCooldownEnforced() {
        // constructor clamps to >= 30ms even if caller passes 0
        SoundThrottle t = new SoundThrottle(0);
        assertTrue(t.allow("k", 0));
        assertFalse(t.allow("k", 10));
        assertTrue(t.allow("k", 31));
    }

    @Test
    void clearAllowsImmediateReplay() {
        SoundThrottle t = new SoundThrottle(60_000);
        assertTrue(t.allow("ui.open", 0));
        assertFalse(t.allow("ui.open", 1));
        t.clear();
        assertTrue(t.allow("ui.open", 2));
    }
}
