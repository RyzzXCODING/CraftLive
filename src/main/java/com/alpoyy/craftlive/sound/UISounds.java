package com.alpoyy.craftlive.sound;

import com.alpoyy.craftlive.CraftLive;
import com.alpoyy.craftlive.config.CraftLiveConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;

import java.util.function.Supplier;

/**
 * CraftLive UI click-sound system.
 *
 * - Original, self-synthesized soft OGG assets (assets/craftlive/sounds/ui/*).
 * - Played through the vanilla UI sound channel (SimpleSoundInstance.forUI):
 *   respects the game's Master/UI audio levels, never touches global volume.
 * - Plays ONLY on successful interactions; disabled buttons and rejected
 *   clicks never reach the sound call.
 * - Per-type enable toggles + master toggle + separate volume + cooldown
 *   (SoundThrottle) to avoid duplicate/spam sounds.
 *
 * Pure logic (throttle) is split into {@link SoundThrottle} for unit tests.
 */
public final class UISounds {
    // ---- sound events ----
    public static SoundEvent CLICK;
    public static SoundEvent TOGGLE_ON;
    public static SoundEvent TOGGLE_OFF;
    public static SoundEvent TAB;
    public static SoundEvent OPEN;
    public static SoundEvent CLOSE;
    public static SoundEvent WIDGET;
    public static SoundEvent CONFIRM;
    public static SoundEvent HOVER;

    private static volatile boolean registered = false;
    private static volatile Supplier<CraftLiveConfig> configSupplier = () -> null;
    private static volatile SoundThrottle throttle = new SoundThrottle(60);

    private UISounds() {
    }

    /** Register sound events; call once from the client entrypoint. */
    public static synchronized void registerAll() {
        if (registered) return;
        CLICK = register("ui.click");
        TOGGLE_ON = register("ui.toggle_on");
        TOGGLE_OFF = register("ui.toggle_off");
        TAB = register("ui.tab");
        OPEN = register("ui.open");
        CLOSE = register("ui.close");
        WIDGET = register("ui.widget");
        CONFIRM = register("ui.confirm");
        HOVER = register("ui.hover");
        registered = true;
        CraftLive.LOGGER.info("CraftLive UI sounds registered");
    }

    /** Provide the config source (settings). */
    public static void attach(Supplier<CraftLiveConfig> supplier) {
        configSupplier = supplier;
    }

    /** Test/setup hook. */
    public static void setThrottle(SoundThrottle t) {
        throttle = t;
    }

    private static SoundEvent register(String path) {
        Identifier id = Identifier.fromNamespaceAndPath(CraftLive.MOD_ID, path);
        SoundEvent event = SoundEvent.createVariableRangeEvent(id);
        return net.minecraft.core.Registry.register(BuiltInRegistries.SOUND_EVENT, id, event);
    }

    // ---- public interaction API (call only after a successful interaction) ----

    public static void click() {
        play(CLICK, "ui.click", cfg -> cfg.uiSoundClick, 0.9f);
    }

    public static void toggle(boolean turningOn) {
        play(turningOn ? TOGGLE_ON : TOGGLE_OFF, "ui.toggle", cfg -> cfg.uiSoundToggle, 0.9f);
    }

    public static void tab() {
        play(TAB, "ui.tab", cfg -> cfg.uiSoundTab, 0.9f);
    }

    public static void open() {
        play(OPEN, "ui.open", cfg -> cfg.uiSoundMenuOpen, 1.0f);
    }

    public static void close() {
        play(CLOSE, "ui.close", cfg -> cfg.uiSoundMenuClose, 1.0f);
    }

    public static void widget() {
        play(WIDGET, "ui.widget", cfg -> cfg.uiSoundWidget, 0.9f);
    }

    public static void confirm() {
        play(CONFIRM, "ui.confirm", cfg -> cfg.uiSoundClick, 1.0f);
    }

    /** Slider handle release — soft, throttled harder to avoid spam. */
    public static void sliderRelease() {
        play(CLICK, "ui.slider", cfg -> cfg.uiSoundClick, 0.5f);
    }

    /** Optional hover tick — disabled by default in config. */
    public static void hover() {
        play(HOVER, "ui.hover", cfg -> cfg.uiSoundHover, 0.7f);
    }

    // ---- core playback ----

    private interface TypeEnabled {
        boolean get(CraftLiveConfig cfg);
    }

    private static void play(SoundEvent event, String key, TypeEnabled typeEnabled, float volumeScale) {
        if (!registered || event == null) return;
        CraftLiveConfig cfg;
        try {
            cfg = configSupplier.get();
        } catch (Exception e) {
            return;
        }
        if (cfg == null || !cfg.uiSoundsEnabled || !typeEnabled.get(cfg)) return;

        long now = System.currentTimeMillis();
        if (!throttle.allow(key, now)) return;

        try {
            Minecraft mc = Minecraft.getInstance();
            if (mc == null || mc.getSoundManager() == null) return;
            float volume = Math.max(0f, Math.min(1f, cfg.uiSoundVolume)) * volumeScale;
            if (volume <= 0.01f) return;
            mc.getSoundManager().play(SimpleSoundInstance.forUI(event, 1.0f, volume));
        } catch (Exception e) {
            // sound must never crash the GUI
            CraftLive.LOGGER.debug("UI sound playback failed: {}", e.toString());
        }
    }

    /** Apply fresh config (e.g. after Reset Sound Settings). */
    public static void resetThrottle() {
        throttle.clear();
    }
}
