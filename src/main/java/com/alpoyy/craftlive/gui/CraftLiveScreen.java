package com.alpoyy.craftlive.gui;

import com.alpoyy.craftlive.CraftLiveServices;
import com.alpoyy.craftlive.gui.tabs.ClockTab;
import com.alpoyy.craftlive.gui.tabs.DashboardTab;
import com.alpoyy.craftlive.gui.tabs.HudEditorTab;
import com.alpoyy.craftlive.gui.tabs.LiveChatTab;
import com.alpoyy.craftlive.gui.tabs.LiveStatsTab;
import com.alpoyy.craftlive.gui.tabs.LyricsTab;
import com.alpoyy.craftlive.gui.tabs.MusicTab;
import com.alpoyy.craftlive.gui.tabs.SettingsTab;
import com.alpoyy.craftlive.gui.tabs.SongRequestTab;
import com.alpoyy.craftlive.gui.tabs.Tab;
import com.alpoyy.craftlive.render.ClRender;
import com.alpoyy.craftlive.theme.CraftLiveTheme;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * CraftLive main GUI: one clean window with a sidebar and 9 dedicated tabs.
 * Opening: fade + subtle scale-in. Closing: fade-out + subtle shrink.
 * Tab switches fade content in.
 */
public class CraftLiveScreen extends Screen {
    private static final int SIDEBAR_W = 118;
    private static final int MIN_W = 460;
    private static final int MIN_H = 300;

    private final CraftLiveServices services;
    private final List<Tab> tabs = new ArrayList<>();
    private int activeTab = 0;
    private int pendingTab = -1;

    // open/close animation state
    private float openAnim = 0f;         // 0..1
    private boolean closing = false;
    private float closeAnim = 0f;
    private float tabFade = 1f;
    private long lastFrameMs = System.currentTimeMillis();

    public CraftLiveScreen(CraftLiveServices services) {
        super(Component.literal("CraftLive"));
        this.services = services;
    }

    @Override
    protected void init() {
        tabs.clear();
        tabs.add(new DashboardTab(services));
        tabs.add(new LiveStatsTab(services));
        tabs.add(new LiveChatTab(services));
        tabs.add(new MusicTab(services));
        tabs.add(new SongRequestTab(services));
        tabs.add(new LyricsTab(services));
        tabs.add(new ClockTab(services));
        tabs.add(new HudEditorTab(services));
        tabs.add(new SettingsTab(services, this));
        activeTab = Math.min(activeTab, tabs.size() - 1);
        tabs.get(activeTab).onShow();
        openAnim = 0f;
        closing = false;
        closeAnim = 0f;
        com.alpoyy.craftlive.sound.UISounds.open();
    }

    // ---- layout ----

    private int panelX() {
        return Math.max(10, (width - panelW()) / 2);
    }

    private int panelY() {
        return Math.max(10, (height - panelH()) / 2);
    }

    private int panelW() {
        return Math.min(width - 20, 560);
    }

    private int panelH() {
        return Math.min(height - 20, 360);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float delta) {
        long now = System.currentTimeMillis();
        long dt = Math.min(64, now - lastFrameMs);
        lastFrameMs = now;

        boolean animations = services.config.animationsEnabled && !services.config.reducedMotion;

        // open animation
        if (openAnim < 1f) openAnim = Math.min(1f, openAnim + dt / 260f);
        float openEased = animations ? easeOut(openAnim) : 1f;

        // close animation → actually close when done
        if (closing) {
            closeAnim = Math.min(1f, closeAnim + dt / 180f);
            if (closeAnim >= 1f) {
                closing = false;
                onCloseImmediate();
                return;
            }
        }
        float alpha = animations ? (closing ? 1f - closeAnim : openEased) : 1f;

        // tab switch fade
        if (pendingTab >= 0) {
            tabFade -= dt / 120f;
            if (tabFade <= 0f) {
                activeTab = pendingTab;
                pendingTab = -1;
                tabs.get(activeTab).onShow();
                tabFade = 0f;
            }
        } else if (tabFade < 1f) {
            tabFade = Math.min(1f, tabFade + dt / 140f);
        }

        // dim backdrop
        g.fill(0, 0, width, height, CraftLiveTheme.withAlpha(0x000000, 0.55f * alpha));

        // panel geometry with subtle scale-in
        int pw = panelW();
        int ph = panelH();
        int px = panelX();
        int py = panelY();
        if (animations) {
            float scale = 0.96f + 0.04f * openEased - (closing ? 0.05f * closeAnim : 0);
            int cx = px + pw / 2;
            int cy = py + ph / 2;
            pw = (int) (pw * scale);
            ph = (int) (ph * scale);
            px = cx - pw / 2;
            py = cy - ph / 2;
        }

        // main panel
        ClRender.panel(g, px, py, pw, ph, 8,
                CraftLiveTheme.withAlpha(CraftLiveTheme.BG, alpha * 0.96f),
                CraftLiveTheme.withAlpha(CraftLiveTheme.BORDER, alpha));

        // ===== sidebar =====
        g.fill(px + 1, py + 1, px + SIDEBAR_W, py + ph - 1,
                CraftLiveTheme.withAlpha(CraftLiveTheme.PANEL, alpha * 0.7f));
        // logo header
        g.drawString(font, "CRAFT", px + 12, py + 10, CraftLiveTheme.withAlpha(CraftLiveTheme.TEXT, alpha), true);
        g.drawString(font, "LIVE", px + 12 + font.width("CRAFT") + 1, py + 10, CraftLiveTheme.withAlpha(CraftLiveTheme.accentHover(), alpha), true);
        g.drawString(font, services.config.mockMode ? "MOCK MODE" : "v" + com.alpoyy.craftlive.CraftLive.VERSION,
                px + 12, py + 22, CraftLiveTheme.withAlpha(services.config.mockMode ? CraftLiveTheme.WARNING : CraftLiveTheme.TEXT_DIM, alpha), true);
        g.fill(px + 8, py + 34, px + SIDEBAR_W - 8, py + 35,
                CraftLiveTheme.withAlpha(CraftLiveTheme.BORDER, alpha * 0.8f));

        // tab buttons
        int ty = py + 42;
        for (int i = 0; i < tabs.size(); i++) {
            boolean selected = i == activeTab;
            boolean hovered = mouseX >= px + 6 && mouseX <= px + SIDEBAR_W - 6
                    && mouseY >= ty && mouseY <= ty + 18;
            if (selected) {
                ClRender.roundedRect(g, px + 6, ty, SIDEBAR_W - 12, 17, 5,
                        CraftLiveTheme.withAlpha(CraftLiveTheme.accent(), alpha * 0.85f));
            } else if (hovered) {
                ClRender.roundedRect(g, px + 6, ty, SIDEBAR_W - 12, 17, 5,
                        CraftLiveTheme.withAlpha(CraftLiveTheme.PANEL_2, alpha * 0.8f));
            }
            String label = tabShort(i);
            g.drawString(font, label, px + 12, ty + 5,
                    CraftLiveTheme.withAlpha(selected ? CraftLiveTheme.TEXT : CraftLiveTheme.TEXT_DIM, alpha), true);
            ty += 21;
        }

        // footer
        g.drawString(font, "M=GUI T=HUD", px + 10, py + ph - 22,
                CraftLiveTheme.withAlpha(CraftLiveTheme.TEXT_DIM, alpha * 0.6f), true);
        g.drawString(font, "J=Edit  L=Stream", px + 10, py + ph - 12,
                CraftLiveTheme.withAlpha(CraftLiveTheme.TEXT_DIM, alpha * 0.6f), true);

        // ===== content =====
        int cx = px + SIDEBAR_W + 4;
        int cy = py + 4;
        int cw = pw - SIDEBAR_W - 8;
        int ch = ph - 8;

        String header = tabs.get(activeTab).title();
        g.drawString(font, header, cx + 6, cy + 2, CraftLiveTheme.withAlpha(CraftLiveTheme.TEXT, alpha), true);
        g.fill(cx + 6, cy + 14, cx + cw - 6, cy + 15, CraftLiveTheme.withAlpha(CraftLiveTheme.BORDER, alpha * 0.6f));

        float contentAlpha = alpha * (animations ? (closing ? 1f - closeAnim : tabFade) : 1f);
        try {
            tabs.get(activeTab).render(g, font, cx + 2, cy + 20, cw - 4, ch - 24, mouseX, mouseY, contentAlpha, now);
        } catch (Exception e) {
            // a GUI bug must never crash the game — show it inline instead
            com.alpoyy.craftlive.CraftLive.LOGGER.error("Tab {} failed to render", tabs.get(activeTab).id(), e);
            g.drawString(font, "⚠ Kesalahan menampilkan tab ini: " + e.getClass().getSimpleName(),
                    cx + 8, cy + 30, CraftLiveTheme.withAlpha(CraftLiveTheme.ERROR, contentAlpha), true);
            g.drawString(font, "Lihat log terbaru untuk detail. Fitur lain tetap bisa dipakai.",
                    cx + 8, cy + 44, CraftLiveTheme.withAlpha(CraftLiveTheme.TEXT_DIM, contentAlpha), true);
        }

        // tooltips
        renderTooltips(g, mouseX, mouseY, alpha);
    }

    private String tabShort(int i) {
        return switch (i) {
            case 0 -> "Dashboard";
            case 1 -> "Live Stats";
            case 2 -> "Live Chat";
            case 3 -> "Music Player";
            case 4 -> "Song Requests";
            case 5 -> "Lyrics";
            case 6 -> "Clock";
            case 7 -> "HUD Editor";
            case 8 -> "Settings";
            default -> "?";
        };
    }

    private void renderTooltips(GuiGraphics g, int mouseX, int mouseY, float alpha) {
        // per-widget tooltips could be added here; buttons with tooltip != null
        for (var tab : tabs) {
            if (tab instanceof com.alpoyy.craftlive.gui.tabs.TabBase base) {
                for (var b : base.buttons) {
                    if (b.tooltip != null && mouseX >= b.x && mouseX <= b.x + b.w
                            && mouseY >= b.y && mouseY <= b.y + b.h) {
                        int tw = font.width(b.tooltip);
                        g.fill(mouseX + 6, mouseY - 14, mouseX + tw + 12, mouseY - 4,
                                CraftLiveTheme.withAlpha(CraftLiveTheme.PANEL_2, 0.95f));
                        g.drawString(font, b.tooltip, mouseX + 9, mouseY - 12,
                                CraftLiveTheme.withAlpha(CraftLiveTheme.TEXT, alpha), true);
                    }
                }
            }
        }
    }

    private static float easeOut(float t) {
        return 1f - (1f - t) * (1f - t);
    }

    // ---- input ----

    @Override
    public boolean mouseClicked(net.minecraft.client.input.MouseButtonEvent event, boolean doubled) {
        double mouseX = event.x();
        double mouseY = event.y();
        int px = panelX();
        int py = panelY();
        // sidebar tab clicks
        if (mouseX >= px + 6 && mouseX <= px + SIDEBAR_W - 6) {
            int ty = py + 42;
            for (int i = 0; i < tabs.size(); i++) {
                if (mouseY >= ty && mouseY <= ty + 18) {
                    if (i != activeTab && pendingTab < 0) {
                        pendingTab = i;
                        com.alpoyy.craftlive.sound.UISounds.tab();
                    }
                    return true;
                }
                ty += 21;
            }
        }
        if (tabs.get(activeTab).mouseClicked(mouseX, mouseY, event.button())) return true;
        return super.mouseClicked(event, doubled);
    }

    @Override
    public boolean mouseDragged(net.minecraft.client.input.MouseButtonEvent event, double dragX, double dragY) {
        return tabs.get(activeTab).mouseDragged(event.x(), event.y())
                || super.mouseDragged(event, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(net.minecraft.client.input.MouseButtonEvent event) {
        tabs.get(activeTab).mouseReleased();
        return super.mouseReleased(event);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double xAmount, double yAmount) {
        if (tabs.get(activeTab).mouseScrolled(mouseX, mouseY, yAmount)) return true;
        return super.mouseScrolled(mouseX, mouseY, xAmount, yAmount);
    }

    @Override
    public boolean keyPressed(net.minecraft.client.input.KeyEvent event) {
        if (event.key() == org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE) {
            closing = true;
            return true;
        }
        if (tabs.get(activeTab).keyPressed(event.key(), event.modifiers())) return true;
        return super.keyPressed(event);
    }

    @Override
    public boolean charTyped(net.minecraft.client.input.CharacterEvent event) {
        String s = event.codepointAsString();
        if (!s.isEmpty() && tabs.get(activeTab).charTyped(s.charAt(0))) return true;
        return super.charTyped(event);
    }

    @Override
    public void tick() {
        super.tick();
        tabs.get(activeTab).tick();
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return false; // handled with animation in keyPressed
    }

    /** Animated close entry. */
    public void requestClose() {
        closing = true;
    }

    @Override
    public void onClose() {
        requestClose();
    }

    private void onCloseImmediate() {
        services.configManager.saveAsync();
        com.alpoyy.craftlive.sound.UISounds.close();
        minecraft.setScreen(null);
    }
}
