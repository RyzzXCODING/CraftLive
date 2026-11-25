package com.alpoyy.craftlive.keybind;

import com.alpoyy.craftlive.CraftLive;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import org.lwjgl.glfw.GLFW;

/**
 * CraftLive keybinds — exactly FOUR (per spec):
 *   M — open/close Main GUI
 *   T — toggle all floating HUD widgets (systems keep running)
 *   J — open/close HUD Editor
 *   L — toggle Stream Mode
 *
 * Registered through Fabric's KeyBindingHelper so they appear in
 * Options → Controls → CraftLive, are remappable, resettable, and
 * conflict-detectable by vanilla. No per-widget keybinds by design.
 */
public final class ModKeybinds {
    public static KeyMapping OPEN_GUI;
    public static KeyMapping TOGGLE_HUD;
    public static KeyMapping HUD_EDITOR;
    public static KeyMapping STREAM_MODE;

    private ModKeybinds() {
    }

    public static void register() {
        KeyMapping.Category category = KeyMapping.Category.register(
                Identifier.fromNamespaceAndPath(CraftLive.MOD_ID, "general"));

        OPEN_GUI = KeyBindingHelper.registerKeyBinding(new KeyMapping(
                "key.craftlive.open_gui",
                GLFW.GLFW_KEY_M,
                category));
        TOGGLE_HUD = KeyBindingHelper.registerKeyBinding(new KeyMapping(
                "key.craftlive.toggle_hud",
                GLFW.GLFW_KEY_T,
                category));
        HUD_EDITOR = KeyBindingHelper.registerKeyBinding(new KeyMapping(
                "key.craftlive.hud_editor",
                GLFW.GLFW_KEY_J,
                category));
        STREAM_MODE = KeyBindingHelper.registerKeyBinding(new KeyMapping(
                "key.craftlive.stream_mode",
                GLFW.GLFW_KEY_L,
                category));
        CraftLive.LOGGER.info("CraftLive keybinds registered (M/T/J/L)");
    }

    /**
     * Poll per client tick. Text input safety: GUI screens consume their own
     * keys; these handlers run only when no screen (or our screens) are up,
     * and Minecraft does not deliver consumeClick while a Screen has focus.
     */
    public static void tick(Minecraft mc) {
        while (OPEN_GUI.consumeClick()) {
            if (mc.screen == null) {
                mc.setScreen(new com.alpoyy.craftlive.gui.CraftLiveScreen(com.alpoyy.craftlive.CraftLiveServices.get()));
            } else if (mc.screen instanceof com.alpoyy.craftlive.gui.CraftLiveScreen cls) {
                cls.requestClose();
            }
        }
        while (TOGGLE_HUD.consumeClick()) {
            var services = com.alpoyy.craftlive.CraftLiveServices.get();
            if (services.widgets == null) continue;   // widgets not initialized yet
            boolean nowVisible = services.widgets.toggleAllHud();
            configManagerSave();
            com.alpoyy.craftlive.CraftLiveServices.get().notifications.push(
                    com.alpoyy.craftlive.notify.NotificationBus.Notification.Level.INFO,
                    "HUD", nowVisible ? "Semua widget ditampilkan" : "Semua widget disembunyikan (sistem tetap jalan)");
        }
        while (HUD_EDITOR.consumeClick()) {
            if (mc.screen == null) {
                mc.setScreen(new com.alpoyy.craftlive.hud.HudEditorScreen(com.alpoyy.craftlive.CraftLiveServices.get()));
            } else if (mc.screen instanceof com.alpoyy.craftlive.hud.HudEditorScreen hes) {
                // J in editor closes+saves (handled by the screen itself)
            }
        }
        while (STREAM_MODE.consumeClick()) {
            var services = com.alpoyy.craftlive.CraftLiveServices.get();
            boolean active = services.streamMode.toggle();
            configManagerSave();
            services.notifications.push(
                    com.alpoyy.craftlive.notify.NotificationBus.Notification.Level.WARNING,
                    "Stream Mode", active ? "AKTIF — HUD disederhanakan" : "Nonaktif");
        }
    }

    private static void configManagerSave() {
        com.alpoyy.craftlive.CraftLiveServices.get().configManager.saveAsync();
    }

    /** Binding descriptor for the Settings tab display. */
    public record Binding(String name, KeyMapping mapping) {
    }

    /** All registered CraftLive bindings in display order. */
    public static java.util.List<Binding> all() {
        java.util.List<Binding> list = new java.util.ArrayList<>();
        if (OPEN_GUI != null) list.add(new Binding("Buka GUI (M)", OPEN_GUI));
        if (TOGGLE_HUD != null) list.add(new Binding("Toggle semua HUD (T)", TOGGLE_HUD));
        if (HUD_EDITOR != null) list.add(new Binding("HUD Editor (J)", HUD_EDITOR));
        if (STREAM_MODE != null) list.add(new Binding("Stream Mode (L)", STREAM_MODE));
        return list;
    }
}
