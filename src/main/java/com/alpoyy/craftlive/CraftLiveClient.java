package com.alpoyy.craftlive;

import com.alpoyy.craftlive.hud.WidgetManager;
import com.alpoyy.craftlive.keybind.ModKeybinds;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.resources.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Paths;

/**
 * CraftLive client entrypoint: wires config, services, keybinds, HUD
 * rendering and lifecycle. No blocking network work ever happens here.
 */
public class CraftLiveClient implements ClientModInitializer {
    public static final String MOD_ID = CraftLive.MOD_ID;
    public static final Logger LOGGER = CraftLive.LOGGER;

    @Override
    public void onInitializeClient() {
        LOGGER.info("CraftLive {} initializing — Minecraft Streaming Companion", CraftLive.VERSION);

        // Resolve the real game directory (works in dev and production) and
        // expose it to ConfigManager/SecureStore.
        String gameDir = net.fabricmc.loader.api.FabricLoader.getInstance()
                .getGameDir().toAbsolutePath().toString();
        System.setProperty("minecraft.client.dir", gameDir);

        // services (config, live connection, requests, chat, notifications)
        CraftLiveServices.initialize(Paths.get(gameDir, "config",
                com.alpoyy.craftlive.config.CraftLiveConfig.CONFIG_FILE));
        var services = CraftLiveServices.get();

        // widgets
        services.widgets = new WidgetManager(services.config, services.configManager);

        // keybinds (M/T/J/L)
        ModKeybinds.register();

        // HUD rendering via the modern HudElementRegistry (not deprecated callbacks)
        HudElementRegistry.addLast(
                Identifier.fromNamespaceAndPath(MOD_ID, "widgets"),
                (graphics, tickCounter) -> {
                    if (services.widgets != null) {
                        services.widgets.render(graphics, services.streamMode, System.currentTimeMillis());
                    }
                });

        // tick: keybinds + request pipeline + YT progress refresh
        services.requests.setPlaybackGate(() ->
                services.youtube != null && services.youtube.isGateActive());
        ClientTickEvents.END_CLIENT_TICK.register(mc -> {
            ModKeybinds.tick(mc);
            services.requests.tick();
            if (services.youtube != null) {
                services.youtube.refreshProgress();
            }
            // song-request → auto-play via YouTube Music when idle & enabled
            if (services.config.requestsEnabled && !services.config.mockMode) {
                autoPlayHook(services);
            }
        });

        ClientLifecycleEvents.CLIENT_STOPPING.register(mc -> {
            LOGGER.info("CraftLive shutting down cleanly...");
            services.shutdown();
        });

        LOGGER.info("CraftLive initialized (mockMode={})", services.config.mockMode);
    }

    private void autoPlayHook(CraftLiveServices services) {
        // If the queue advanced to a new request and YT connector is idle,
        // resolve and play it (user-authorized yt-dlp path).
        var current = services.requests.currentRequest();
        var yt = services.youtube;
        if (current == null || yt == null) return;
        if (!current.playedViaYt) {
            current.playedViaYt = true;
            if (yt.state().status != com.alpoyy.craftlive.music.PlayerState.Status.DISCONNECTED) {
                String q = current.artist.isBlank() ? current.track : current.artist + " " + current.track;
                yt.searchAndPlay(q, err -> services.notifications.push(
                        com.alpoyy.craftlive.notify.NotificationBus.Notification.Level.ERROR,
                        "YouTube Music", err));
            }
        }
    }
}
