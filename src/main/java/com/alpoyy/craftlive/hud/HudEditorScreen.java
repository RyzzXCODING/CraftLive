package com.alpoyy.craftlive.hud;

import com.alpoyy.craftlive.CraftLiveServices;
import com.alpoyy.craftlive.render.ClRender;
import com.alpoyy.craftlive.theme.CraftLiveTheme;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

/**
 * Dedicated HUD Editor (J key): drag widgets freely, grid snapping,
 * screen-boundary protection, selection, reset. Esc or J closes and saves.
 */
public class HudEditorScreen extends Screen {
    private final CraftLiveServices services;
    private final WidgetManager widgets;
    private boolean saved = false;

    public HudEditorScreen(CraftLiveServices services) {
        super(Component.literal("CraftLive HUD Editor"));
        this.services = services;
        this.widgets = services.widgets;
    }

    @Override
    protected void init() {
        widgets.setEditorMode(true);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float delta) {
        long now = System.currentTimeMillis();

        // dim backdrop
        g.fill(0, 0, width, height, CraftLiveTheme.withAlpha(0x000000, 0.45f));

        // grid (optional)
        if (services.config.hudEditorShowGrid) {
            int gs = services.config.hudGridSize;
            int gridColor = CraftLiveTheme.withAlpha(CraftLiveTheme.BORDER, 0.18f);
            for (int x = 0; x < width; x += gs) {
                g.fill(x, 0, x + 1, height, gridColor);
            }
            for (int y = 0; y < height; y += gs) {
                g.fill(0, y, width, y + 1, gridColor);
            }
        }

        // widgets in editor mode (rendered at global scale)
        widgets.render(g, services.streamMode, now);

        // header bar
        ClRender.roundedRectBordered(g, width / 2 - 220, 6, 440, 24, 6,
                CraftLiveTheme.withAlpha(CraftLiveTheme.PANEL, 0.95f),
                CraftLiveTheme.withAlpha(CraftLiveTheme.ACCENT, 0.8f));
        String title = "HUD EDITOR — seret widget (klik kiri), klik kanan = tampil/sembunyi";
        g.drawString(font, title, width / 2 - font.width(title) / 2, 13,
                CraftLiveTheme.withAlpha(CraftLiveTheme.TEXT, 1f), true);

        // selected widget hint
        HudWidget selected = null;
        for (HudWidget w : widgets.all()) {
            if (w.isSelected()) {
                selected = w;
                break;
            }
        }
        if (selected != null) {
            String info = selected.title + "  @ " + (int) selected.getX() + "," + (int) selected.getY()
                    + "  skala " + String.format("%.2fx", selected.effectiveScale());
            g.drawString(font, info, 8, height - 14,
                    CraftLiveTheme.withAlpha(CraftLiveTheme.ACCENT_HOVER, 1f), true);
        }
        String hint = "Esc/J: simpan & keluar";
        g.drawString(font, hint, width - font.width(hint) - 8, height - 14,
                CraftLiveTheme.withAlpha(CraftLiveTheme.TEXT_DIM, 0.9f), true);

        super.render(g, mouseX, mouseY, delta);
    }

    @Override
    public boolean mouseClicked(net.minecraft.client.input.MouseButtonEvent event, boolean doubled) {
        if (widgets.onMouseDown(event.x(), event.y(), event.button())) return true;
        return super.mouseClicked(event, doubled);
    }

    @Override
    public boolean mouseDragged(net.minecraft.client.input.MouseButtonEvent event, double dragX, double dragY) {
        widgets.onMouseDrag(event.x(), event.y());
        return true;
    }

    @Override
    public boolean mouseReleased(net.minecraft.client.input.MouseButtonEvent event) {
        widgets.onMouseUp();
        return super.mouseReleased(event);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double xAmount, double yAmount) {
        // scroll on a selected widget adjusts its scale
        HudWidget selected = null;
        for (HudWidget w : widgets.all()) {
            if (w.isSelected()) {
                selected = w;
                break;
            }
        }
        if (selected != null) {
            float s = selected.effectiveScale() + (float) yAmount * 0.05f;
            selected.setScale(s);
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, xAmount, yAmount);
    }

    @Override
    public boolean keyPressed(net.minecraft.client.input.KeyEvent event) {
        if (event.key() == GLFW.GLFW_KEY_ESCAPE || event.key() == GLFW.GLFW_KEY_J) {
            closeAndSave();
            return true;
        }
        // arrow keys nudge the selected widget (with snap)
        HudWidget selected = null;
        for (HudWidget w : widgets.all()) {
            if (w.isSelected()) {
                selected = w;
                break;
            }
        }
        if (selected != null) {
            int step = services.config.hudSnapToGrid ? services.config.hudGridSize : 1;
            switch (event.key()) {
                case GLFW.GLFW_KEY_LEFT -> {
                    selected.setPosition(selected.getX() - step, selected.getY());
                    return true;
                }
                case GLFW.GLFW_KEY_RIGHT -> {
                    selected.setPosition(selected.getX() + step, selected.getY());
                    return true;
                }
                case GLFW.GLFW_KEY_UP -> {
                    selected.setPosition(selected.getX(), selected.getY() - step);
                    return true;
                }
                case GLFW.GLFW_KEY_DOWN -> {
                    selected.setPosition(selected.getX(), selected.getY() + step);
                    return true;
                }
            }
        }
        return super.keyPressed(event);
    }

    private void closeAndSave() {
        if (!saved) {
            widgets.setEditorMode(false);
            services.configManager.saveAsync();
            saved = true;
        }
        minecraft.setScreen(null);
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return false; // handled above with save
    }
}
