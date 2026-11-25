package com.alpoyy.craftlive.hud.widgets;

import com.alpoyy.craftlive.hud.HudWidget;
import com.alpoyy.craftlive.render.ClRender;
import com.alpoyy.craftlive.theme.CraftLiveTheme;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

/**
 * Optional compact dashboard summary for the HUD: connection, viewers,
 * queue, music, stream mode — one glance.
 */
public class DashboardSummaryWidget extends HudWidget {
    public DashboardSummaryWidget(float x, float y) {
        super("summary", "Dashboard Summary", x, y, 190, 84);
    }

    @Override
    protected void drawContent(GuiGraphics g, int w, int h, float alpha, boolean editorMode, long epochMs) {
        var services = com.alpoyy.craftlive.CraftLiveServices.get();
        drawWidgetBackground(g, w, h, alpha, services.streamMode.shouldSimplify());

        Font font = font();
        int pad = 8;
        float a = alpha;

        g.drawString(font, "CRAFTLIVE", pad, 4, CraftLiveTheme.withAlpha(CraftLiveTheme.accentHover(), a), true);

        if (editorMode) {
            g.drawString(font, "LIVE ● 1.2K penonton", pad, 18, CraftLiveTheme.withAlpha(CraftLiveTheme.TEXT_DIM, a), true);
            g.drawString(font, "Antrean: 3 lagu", pad, 30, CraftLiveTheme.withAlpha(CraftLiveTheme.TEXT_DIM, a), true);
            g.drawString(font, "Musik: Spotify — Memutar", pad, 42, CraftLiveTheme.withAlpha(CraftLiveTheme.TEXT_DIM, a), true);
            g.drawString(font, "Stream Mode: OFF", pad, 54, CraftLiveTheme.withAlpha(CraftLiveTheme.TEXT_DIM, a), true);
            return;
        }

        // live line
        String liveState = services.live.state().display();
        boolean connected = services.live.state().isConnected();
        int liveColor = connected ? CraftLiveTheme.LIVE_GREEN
                : services.live.state() == com.alpoyy.craftlive.live.ConnectionState.CONNECTING
                || services.live.state() == com.alpoyy.craftlive.live.ConnectionState.RECONNECTING
                ? CraftLiveTheme.WARNING
                : CraftLiveTheme.ERROR;
        String liveLine = connected
                ? "● LIVE  👤 " + services.live.stats().viewers.get()
                : "○ " + liveState;
        if (services.config.mockMode) liveLine += "  [MOCK]";
        g.drawString(font, ClRender.clip(font, liveLine, w - pad * 2), pad, 18,
                CraftLiveTheme.withAlpha(liveColor, a), true);

        // queue line
        String queueLine = "Antrean: " + services.requests.queue().size()
                + "/" + services.requests.queue().maxQueue() + " lagu";
        g.drawString(font, queueLine, pad, 30, CraftLiveTheme.withAlpha(CraftLiveTheme.TEXT_DIM, a), true);

        // music line
        var platform = services.preferredPlatform();
        String musicLine = platform == null ? "Musik: belum terhubung"
                : "Musik: " + platform.displayName() + " — " + platform.state().statusDisplay();
        g.drawString(font, ClRender.clip(font, musicLine, w - pad * 2), pad, 42,
                CraftLiveTheme.withAlpha(CraftLiveTheme.TEXT_DIM, a), true);

        // stream mode
        String smLine = "Stream Mode: " + (services.streamMode.isActive() ? "ON" : "OFF")
                + (services.config.mockMode ? "  •  MOCK MODE" : "");
        g.drawString(font, smLine, pad, 54,
                CraftLiveTheme.withAlpha(services.streamMode.isActive() ? CraftLiveTheme.WARNING : CraftLiveTheme.TEXT_DIM, a), true);
    }
}
