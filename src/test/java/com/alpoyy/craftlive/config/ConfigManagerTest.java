package com.alpoyy.craftlive.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class ConfigManagerTest {
    @TempDir
    Path tempDir;

    @Test
    void createsDefaultsWhenMissing() {
        Path path = tempDir.resolve("cfg.json");
        ConfigManager manager = new ConfigManager(path);
        CraftLiveConfig config = manager.load();
        assertNotNull(config);
        assertTrue(Files.exists(path), "default config should be written");
        assertEquals(30, config.requestMaxQueue);
        assertTrue(config.hudVisible);
    }

    @Test
    void roundTripPersistsValues() throws Exception {
        Path path = tempDir.resolve("cfg.json");
        ConfigManager first = new ConfigManager(path);
        CraftLiveConfig config = first.load();
        config.tiktokUsername = "streamerku";
        config.requestMaxQueue = 15;
        config.mockMode = true;
        config.hudGlobalOpacity = 0.7f;
        config.widgetLayouts.put("clock", new CraftLiveConfig.WidgetLayout(33.5f, 44.25f, false));
        first.saveNow();

        // reload in a new manager
        ConfigManager second = new ConfigManager(path);
        CraftLiveConfig loaded = second.load();
        assertEquals("streamerku", loaded.tiktokUsername);
        assertEquals(15, loaded.requestMaxQueue);
        assertTrue(loaded.mockMode);
        assertEquals(0.7f, loaded.hudGlobalOpacity, 0.001f);
        assertTrue(loaded.widgetLayouts.containsKey("clock"));
        assertEquals(33.5f, loaded.widgetLayouts.get("clock").x, 0.001f);
        assertFalse(loaded.widgetLayouts.get("clock").visible);
    }

    @Test
    void corruptedJsonBacksUpAndDefaults() throws Exception {
        Path path = tempDir.resolve("cfg.json");
        Files.writeString(path, "{ this is not valid json !!!");
        ConfigManager manager = new ConfigManager(path);
        CraftLiveConfig config = manager.load();
        assertNotNull(config);
        assertEquals(30, config.requestMaxQueue); // defaults restored
        // backup created
        Path backup = tempDir.resolve("craftlive_config_corrupted_backup.json");
        assertTrue(Files.exists(backup), "corrupted config should be backed up");
        // and a fresh valid config written
        assertTrue(Files.readString(path).contains("\"requestMaxQueue\""));
    }

    @Test
    void invalidValuesAreClampedOnLoad() {
        String json = """
                {
                  "requestMaxQueue": 9999,
                  "requestDelayMs": 50,
                  "chatMaxMessages": 1,
                  "hudGlobalOpacity": 42.0,
                  "hudGlobalScale": 0.01,
                  "requestUserCooldownSeconds": -5
                }
                """;
        CraftLiveConfig loaded = new com.google.gson.Gson().fromJson(json, CraftLiveConfig.class);
        CraftLiveConfig target = new CraftLiveConfig();
        target.mergeFrom(loaded);
        assertEquals(200, target.requestMaxQueue);
        assertEquals(3000, target.requestDelayMs);
        assertEquals(10, target.chatMaxMessages);
        assertEquals(1.0f, target.hudGlobalOpacity, 0.001f);
        assertEquals(0.5f, target.hudGlobalScale, 0.001f);
        assertEquals(0, target.requestUserCooldownSeconds);
    }

    @Test
    void emptyFileDefaults() throws Exception {
        Path path = tempDir.resolve("cfg.json");
        Files.writeString(path, "");
        ConfigManager manager = new ConfigManager(path);
        assertNotNull(manager.load());
        assertEquals(30, manager.config().requestMaxQueue);
    }

    @Test
    void resetRestoresDefaults() throws Exception {
        Path path = tempDir.resolve("cfg.json");
        ConfigManager manager = new ConfigManager(path);
        manager.load();
        manager.config().mockMode = true;
        manager.config().requestMaxQueue = 77;
        manager.saveNow();
        manager.resetToDefaults();
        assertFalse(manager.config().mockMode);
        assertEquals(30, manager.config().requestMaxQueue);
    }

    @Test
    void secretsNeverWrittenToConfig() throws Exception {
        Path path = tempDir.resolve("cfg.json");
        ConfigManager manager = new ConfigManager(path);
        CraftLiveConfig config = manager.load();
        config.tiktokUsername = "user";
        manager.saveNow();
        String content = Files.readString(path);
        assertFalse(content.contains("api"), "no api key material should exist in config");
        assertFalse(content.toLowerCase().contains("secret"));
        assertFalse(content.toLowerCase().contains("token"));
    }

    @Test
    void accentColorAndSoundSettingsRoundTrip() throws Exception {
        Path path = tempDir.resolve("cfg.json");
        ConfigManager first = new ConfigManager(path);
        CraftLiveConfig config = first.load();
        config.accentColorHex = "#4287FF";
        config.uiSoundsEnabled = false;
        config.uiSoundVolume = 0.35f;
        config.uiSoundClick = false;
        config.uiSoundHover = true;
        first.saveNow();

        ConfigManager second = new ConfigManager(path);
        CraftLiveConfig loaded = second.load();
        assertEquals("#4287FF", loaded.accentColorHex);
        assertFalse(loaded.uiSoundsEnabled);
        assertEquals(0.35f, loaded.uiSoundVolume, 0.001f);
        assertFalse(loaded.uiSoundClick);
        assertTrue(loaded.uiSoundHover);
    }

    @Test
    void invalidAccentHexInFileFallsBackToDefault() throws Exception {
        Path path = tempDir.resolve("cfg.json");
        Files.writeString(path, "{\"accentColorHex\": \"#NOPE!!\", \"uiSoundVolume\": 2.5}");
        ConfigManager manager = new ConfigManager(path);
        CraftLiveConfig config = manager.load();
        assertEquals(com.alpoyy.craftlive.theme.AccentColor.DEFAULT_HEX, config.accentColorHex,
                "invalid hex must fall back to the default red");
        assertEquals(1.0f, config.uiSoundVolume, 0.001f, "volume must clamp to 0..1");
    }

    @Test
    void defaultsForNewFields() {
        CraftLiveConfig config = new CraftLiveConfig();
        assertEquals(com.alpoyy.craftlive.theme.AccentColor.DEFAULT_HEX, config.accentColorHex);
        assertTrue(config.uiSoundsEnabled);
        assertEquals(0.8f, config.uiSoundVolume, 0.001f);
        assertTrue(config.uiSoundClick);
        assertTrue(config.uiSoundToggle);
        assertTrue(config.uiSoundTab);
        assertTrue(config.uiSoundMenuOpen);
        assertTrue(config.uiSoundMenuClose);
        assertTrue(config.uiSoundWidget);
        assertFalse(config.uiSoundHover);
    }

    @Test
    void menuOpenCloseTogglesRoundTrip() throws Exception {
        Path path = tempDir.resolve("cfg.json");
        ConfigManager first = new ConfigManager(path);
        CraftLiveConfig config = first.load();
        config.uiSoundMenuOpen = false;
        config.uiSoundMenuClose = false;
        config.uiSoundVolume = 1.0f;
        first.saveNow();

        ConfigManager second = new ConfigManager(path);
        CraftLiveConfig loaded = second.load();
        assertFalse(loaded.uiSoundMenuOpen);
        assertFalse(loaded.uiSoundMenuClose);
        assertEquals(1.0f, loaded.uiSoundVolume, 0.001f);
    }
}
