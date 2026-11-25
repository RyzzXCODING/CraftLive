package com.alpoyy.craftlive.stream;

import com.alpoyy.craftlive.config.CraftLiveConfig;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class StreamModeTest {
    @Test
    void toggleBehavior() {
        StreamMode mode = new StreamMode(new CraftLiveConfig());
        assertFalse(mode.isActive());
        assertTrue(mode.toggle());
        assertTrue(mode.isActive());
        assertFalse(mode.toggle());
        assertFalse(mode.isActive());
    }

    @Test
    void defaultsShowEverythingWhenOff() {
        StreamMode mode = new StreamMode(new CraftLiveConfig());
        assertTrue(mode.shouldShowChat());
        assertTrue(mode.shouldShowStats());
        assertTrue(mode.shouldShowNotifications());
        assertFalse(mode.shouldHideConnectionInfo());
        assertFalse(mode.shouldSimplify());
    }

    @Test
    void configuredHidingAppliesOnlyWhenActive() {
        CraftLiveConfig config = new CraftLiveConfig();
        config.streamModeHideChat = true;
        config.streamModeHideStats = true;
        config.streamModeHideNotifications = true;
        config.streamModeHideConnectionInfo = true;
        config.streamModeSimplifyHud = true;
        StreamMode mode = new StreamMode(config);

        // inactive: everything normal
        assertTrue(mode.shouldShowChat());
        assertTrue(mode.shouldShowStats());
        assertFalse(mode.shouldSimplify());

        // active: hidden
        mode.setActive(true);
        assertFalse(mode.shouldShowChat());
        assertFalse(mode.shouldShowStats());
        assertFalse(mode.shouldShowNotifications());
        assertTrue(mode.shouldHideConnectionInfo());
        assertTrue(mode.shouldSimplify());
    }

    @Test
    void integrationsAlwaysKeepRunning() {
        StreamMode mode = new StreamMode(new CraftLiveConfig());
        mode.setActive(true);
        assertTrue(mode.keepIntegrationsRunning());
    }

    @Test
    void activeModeKeepsQueueStateIndependent() {
        // Stream Mode must not touch request state (that lives in RequestManager)
        CraftLiveConfig config = new CraftLiveConfig();
        StreamMode mode = new StreamMode(config);
        mode.setActive(true);
        mode.setActive(false);
        // config untouched by stream mode toggling
        assertEquals(30, config.requestMaxQueue);
        assertTrue(config.requestsEnabled);
    }
}
