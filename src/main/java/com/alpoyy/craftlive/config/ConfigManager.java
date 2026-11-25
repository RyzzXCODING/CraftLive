package com.alpoyy.craftlive.config;

import com.alpoyy.craftlive.CraftLive;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Loads/saves {@link CraftLiveConfig} as JSON.
 * Handles: missing files (defaults), corrupted JSON (backup + defaults),
 * missing fields (Gson defaults), invalid values (clamped in config).
 *
 * NOTE: secrets are NEVER written here — they live in {@link SecureStore}.
 */
public class ConfigManager {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final ExecutorService SAVER = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "CraftLive-ConfigSave");
        t.setDaemon(true);
        return t;
    });

    private final Path configPath;
    private CraftLiveConfig config = new CraftLiveConfig();

    public ConfigManager() {
        String minecraftDir = System.getProperty("minecraft.client.dir", ".");
        this.configPath = Paths.get(minecraftDir, "config", CraftLiveConfig.CONFIG_FILE);
    }

    public ConfigManager(Path configPath) {
        this.configPath = configPath;
    }

    public CraftLiveConfig config() {
        return config;
    }

    public Path path() {
        return configPath;
    }

    /** Load config; always yields a usable config object. */
    public CraftLiveConfig load() {
        if (!Files.exists(configPath)) {
            CraftLive.LOGGER.info("CraftLive config not found; using defaults");
            saveNow();
            return config;
        }
        try {
            String json = Files.readString(configPath);
            CraftLiveConfig loaded = GSON.fromJson(json, CraftLiveConfig.class);
            if (loaded == null) {
                throw new IllegalStateException("config is empty");
            }
            config.mergeFrom(loaded);
            CraftLive.LOGGER.info("CraftLive config loaded from {}", configPath);
        } catch (Exception e) {
            CraftLive.LOGGER.error("CraftLive config corrupted ({}); backing up and using defaults", e.toString());
            backupCorrupted();
            config = new CraftLiveConfig();
            saveNow();
        }
        return config;
    }

    private void backupCorrupted() {
        try {
            Path backup = configPath.resolveSibling(
                    CraftLiveConfig.CONFIG_FILE.replace(".json", "") + "_corrupted_backup.json");
            Files.copy(configPath, backup, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException ignored) {
        }
    }

    public void saveAsync() {
        String snapshot = GSON.toJson(config);
        SAVER.execute(() -> write(snapshot));
    }

    public void saveNow() {
        write(GSON.toJson(config));
    }

    private synchronized void write(String json) {
        try {
            Files.createDirectories(configPath.getParent());
            Files.writeString(configPath, json);
        } catch (IOException e) {
            CraftLive.LOGGER.error("Failed to save CraftLive config: {}", e.toString());
        }
    }

    public void resetToDefaults() {
        config = new CraftLiveConfig();
        saveNow();
    }

    /** Flush pending async saves; call on shutdown. */
    public static void shutdownSaver() {
        SAVER.shutdown();
        try {
            SAVER.awaitTermination(2, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
