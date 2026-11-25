package com.alpoyy.craftlive.hud;

import com.alpoyy.craftlive.CraftLive;
import com.alpoyy.craftlive.config.CraftLiveConfig;
import com.alpoyy.craftlive.hud.widgets.ChatFeedWidget;
import com.alpoyy.craftlive.hud.widgets.ClockWidget;
import com.alpoyy.craftlive.hud.widgets.DashboardSummaryWidget;
import com.alpoyy.craftlive.hud.widgets.LiveStatusWidget;
import com.alpoyy.craftlive.hud.widgets.LyricsWidget;
import com.alpoyy.craftlive.hud.widgets.MusicPlayerWidget;
import com.alpoyy.craftlive.hud.widgets.SongRequestWidget;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Registry + renderer for all HUD widgets. Handles:
 *  - creation and registration (7 widgets)
 *  - persistence (positions/scale/opacity/visibility)
 *  - toggle-all HUD (T key) — does NOT touch connections/systems
 *  - Stream Mode filtering
 *  - editor interactions (drag/select)
 */
public class WidgetManager {
    private final CraftLiveConfig config;
    private final com.alpoyy.craftlive.config.ConfigManager configManager;
    private final Map<String, HudWidget> widgets = new LinkedHashMap<>();
    private boolean editorMode = false;

    public WidgetManager(CraftLiveConfig config, com.alpoyy.craftlive.config.ConfigManager configManager) {
        this.config = config;
        this.configManager = configManager;
        registerDefaults();
        loadAll();
    }

    private void registerDefaults() {
        // order: Live Status, Song Request (priority), Chat, Music, Lyrics, Clock, Summary
        register(new LiveStatusWidget(12, 12));
        register(new SongRequestWidget(12, 68));
        register(new ChatFeedWidget(12, 210));
        register(new MusicPlayerWidget(12, 470));
        register(new LyricsWidget(12, 570));
        register(new ClockWidget(12, 700));
        register(new DashboardSummaryWidget(330, 12));
    }

    public void register(HudWidget widget) {
        widgets.put(widget.id, widget);
    }

    public HudWidget get(String id) {
        return widgets.get(id);
    }

    public List<HudWidget> all() {
        return new ArrayList<>(widgets.values());
    }

    public void loadAll() {
        for (HudWidget w : widgets.values()) {
            w.loadFrom(config);
        }
    }

    public void saveAll() {
        for (HudWidget w : widgets.values()) {
            w.saveTo(config);
        }
        configManager.saveNow();
    }

    public void saveAllWith(com.alpoyy.craftlive.config.ConfigManager manager) {
        for (HudWidget w : widgets.values()) {
            w.saveTo(manager.config());
        }
        manager.saveNow();
    }

    // ---- visibility ----

    /** T key: toggle all floating widgets; systems keep running. */
    public boolean toggleAllHud() {
        config.hudVisible = !config.hudVisible;
        return config.hudVisible;
    }

    public boolean hudVisible() {
        return config.hudVisible;
    }

    public void setEditorMode(boolean editorMode) {
        this.editorMode = editorMode;
        if (!editorMode) {
            for (HudWidget w : widgets.values()) {
                w.setSelected(false);
                w.stopDrag();
            }
            saveAll();
        }
    }

    public boolean isEditorMode() {
        return editorMode;
    }

    // ---- editor interactions ----

    public HudWidget widgetAt(double mouseX, double mouseY) {
        // topmost = last registered that contains the point
        List<HudWidget> list = all();
        for (int i = list.size() - 1; i >= 0; i--) {
            HudWidget w = list.get(i);
            if (w.isMouseOver(mouseX, mouseY)) return w;
        }
        return null;
    }

    public boolean onMouseDown(double mouseX, double mouseY, int button) {
        if (!editorMode) return false;
        HudWidget hit = widgetAt(mouseX, mouseY);
        if (hit != null) {
            for (HudWidget w : widgets.values()) w.setSelected(w == hit);
            if (button == 0) {
                hit.startDrag(mouseX, mouseY);
                return true;
            } else if (button == 1) {
                hit.setVisible(!hit.isVisible());
                return true;
            }
        } else {
            for (HudWidget w : widgets.values()) w.setSelected(false);
        }
        return false;
    }

    public void onMouseDrag(double mouseX, double mouseY) {
        if (!editorMode) return;
        for (HudWidget w : widgets.values()) {
            if (w.isSelected() && w.isDragging()) {
                w.dragTo(mouseX, mouseY, config.hudSnapToGrid, config.hudGridSize);
            }
        }
    }

    public void onMouseUp() {
        if (!editorMode) return;
        boolean changed = false;
        for (HudWidget w : widgets.values()) {
            if (w.isDragging()) {
                w.stopDrag();
                changed = true;
            }
        }
        if (changed) saveAll();
    }

    // ---- rendering ----

    public void render(GuiGraphics g, com.alpoyy.craftlive.stream.StreamMode streamMode, long epochMs) {
        if (!config.hudVisible) return;
        for (HudWidget w : widgets.values()) {
            // Stream Mode filters specific widget kinds
            if (streamMode.isActive()) {
                if (!streamMode.shouldShowChat() && w instanceof ChatFeedWidget) continue;
                if (!streamMode.shouldShowStats() && (w instanceof LiveStatusWidget || w instanceof DashboardSummaryWidget)) continue;
            }
            w.render(g, config.hudGlobalScale, config.hudGlobalOpacity, editorMode, epochMs);
        }
    }

    public void shutdown() {
        saveAll();
    }
}
