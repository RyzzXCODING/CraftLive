package com.alpoyy.craftlive.gui.tabs;

import com.alpoyy.craftlive.CraftLiveServices;
import com.alpoyy.craftlive.live.ConnectionState;
import com.alpoyy.craftlive.render.ClRender;
import com.alpoyy.craftlive.theme.CraftLiveTheme;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

import java.time.ZoneId;

/**
 * Dashboard: concise summary of everything, no overcrowding.
 */
public class DashboardTab extends TabBase {
    private final CraftLiveServices services;

    public DashboardTab(CraftLiveServices services) {
        this.services = services;
    }

    @Override
    public String id() {
        return "dashboard";
    }

    @Override
    public String title() {
        return "Dashboard";
    }

    @Override
    protected void build() {
        // dashboard is read-only; no controls
    }

    private int card(GuiGraphics g, Font font, String label, String value, int valueColor,
                     int x, int y, int w, float alpha) {
        ClRender.roundedRectBordered(g, x, y, w, 34, 6,
                CraftLiveTheme.withAlpha(CraftLiveTheme.PANEL, alpha * 0.9f),
                CraftLiveTheme.withAlpha(CraftLiveTheme.BORDER, alpha * 0.8f));
        g.drawString(font, label, x + 8, y + 6, CraftLiveTheme.withAlpha(CraftLiveTheme.TEXT_DIM, alpha), true);
        String clipped = ClRender.clip(font, value, w - 16);
        g.drawString(font, clipped, x + 8, y + 19, CraftLiveTheme.withAlpha(valueColor, alpha), true);
        return y + 42;
    }

    @Override
    public void render(GuiGraphics g, Font font, int x, int y, int w, int h,
                       double mouseX, double mouseY, float alpha, long epochMs) {
        layout(x, y, w, h);
        scroll.render(g, alpha, () -> {
            int cx = scroll.x;
            int cy = scroll.y + (int) scroll.offset() + 4;
            int colW = (scroll.w - 12) / 2;

            var live = services.live;
            var stats = live.stats();
            ConnectionState state = live.state();
            int stateColor = CraftLiveTheme.connectionStateColor(state.name());

            // MOCK banner
            if (services.config.mockMode) {
                g.fill(cx, cy, cx + scroll.w, cy + 16, CraftLiveTheme.withAlpha(CraftLiveTheme.WARNING, alpha * 0.25f));
                g.drawString(font, "⚠ MOCK MODE — data simulasi, bukan data live", cx + 8, cy + 4,
                        CraftLiveTheme.withAlpha(CraftLiveTheme.WARNING, alpha), true);
                cy += 22;
            }

            cy = card(g, font, "TikTok LIVE", state.display() + (state == ConnectionState.CONNECTED ? "  ● LIVE" : ""),
                    stateColor, cx, cy, colW, alpha);
            cy = card(g, font, "Penonton", state.isConnected() ? String.valueOf(stats.viewers.get()) : "—",
                    CraftLiveTheme.TEXT, cx, cy, colW, alpha);
            cy = card(g, font, "Suka", state.isConnected() ? String.valueOf(stats.totalLikes.get()) : "—",
                    CraftLiveTheme.TEXT, cx, cy, colW, alpha);
            cy = card(g, font, "Follow / Gift", state.isConnected()
                            ? stats.follows.get() + " / " + stats.gifts.get() : "—",
                    CraftLiveTheme.TEXT, cx, cy, colW, alpha);
            cy = card(g, font, "EulerStream", state == ConnectionState.CONNECTED ? "Terhubung" : state.display(),
                    stateColor, cx, cy, colW, alpha);

            // music
            var platform = services.preferredPlatform();
            String musicValue = platform == null ? "Tidak terhubung"
                    : platform.displayName() + " — " + platform.state().statusDisplay();
            int musicColor = platform != null && platform.state().status == com.alpoyy.craftlive.music.PlayerState.Status.PLAYING
                    ? CraftLiveTheme.LIVE_GREEN : CraftLiveTheme.TEXT;
            cy = card(g, font, "Musik", musicValue, musicColor, cx, cy, colW, alpha);

            // request
            var current = services.requests.currentRequest();
            String reqValue = current == null ? "Tidak ada"
                    : ClRender.clip(font, current.track, colW - 16) + " — @" + current.requester;
            cy = card(g, font, "Request aktif", reqValue, CraftLiveTheme.accentHover(), cx, cy, colW, alpha);
            cy = card(g, font, "Antrean", services.requests.queue().size() + "/" + services.requests.queue().maxQueue()
                            + (services.requests.queue().isPaused() ? " (dijeda)" : ""),
                    CraftLiveTheme.TEXT, cx, cy, colW, alpha);

            // clock
            var zone = com.alpoyy.craftlive.clock.ClockEngine.resolveZone(services.config.clockTimezone);
            String clockValue = com.alpoyy.craftlive.clock.ClockEngine.formatReal(zone,
                    services.config.clock24Hour, true, epochMs) + "  " + com.alpoyy.craftlive.clock.ClockEngine.zoneLabel(zone);
            cy = card(g, font, "Jam", clockValue, CraftLiveTheme.TEXT, cx, cy, colW, alpha);

            cy = card(g, font, "HUD", services.config.hudVisible ? "Tampil" : "Disembunyikan",
                    services.config.hudVisible ? CraftLiveTheme.LIVE_GREEN : CraftLiveTheme.TEXT_DIM, cx, cy, colW, alpha);
            cy = card(g, font, "Stream Mode", services.streamMode.isActive() ? "AKTIF" : "Nonaktif",
                    services.streamMode.isActive() ? CraftLiveTheme.WARNING : CraftLiveTheme.TEXT_DIM, cx, cy, colW, alpha);

            // notifications
            section(g, font, "Notifikasi Terbaru", cx, cy + 2, scroll.w, alpha);
            cy += 20;
            var notes = services.notifications.active();
            if (notes.isEmpty()) {
                g.drawString(font, "Tidak ada notifikasi", cx + 4, cy,
                        CraftLiveTheme.withAlpha(CraftLiveTheme.TEXT_DIM, alpha * 0.7f), true);
                cy += 16;
            } else {
                for (var n : notes) {
                    int nc = switch (n.level) {
                        case SUCCESS -> CraftLiveTheme.LIVE_GREEN;
                        case WARNING -> CraftLiveTheme.WARNING;
                        case ERROR -> CraftLiveTheme.ERROR;
                        default -> CraftLiveTheme.TEXT_DIM;
                    };
                    String line = ClRender.clip(font, n.title + ": " + n.body, scroll.w - 20);
                    g.drawString(font, line, cx + 4, cy, CraftLiveTheme.withAlpha(nc, alpha), true);
                    cy += 14;
                }
            }
            contentHeight = cy - scroll.y + 8;
            scroll.setContentHeight(contentHeight);
        });
    }
}
