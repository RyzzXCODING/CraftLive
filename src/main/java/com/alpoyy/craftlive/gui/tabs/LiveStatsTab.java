package com.alpoyy.craftlive.gui.tabs;

import com.alpoyy.craftlive.CraftLiveServices;
import com.alpoyy.craftlive.gui.components.ClButton;
import com.alpoyy.craftlive.live.ConnectionState;
import com.alpoyy.craftlive.render.ClRender;
import com.alpoyy.craftlive.theme.CraftLiveTheme;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

/**
 * Live Stats tab: full statistics + connection controls.
 */
public class LiveStatsTab extends TabBase {
    private final CraftLiveServices services;

    public LiveStatsTab(CraftLiveServices services) {
        this.services = services;
    }

    @Override
    public String id() {
        return "live_stats";
    }

    @Override
    public String title() {
        return "Live Stats";
    }

    @Override
    protected void build() {
        boolean connected = services.live.state() == ConnectionState.CONNECTED;
        boolean busy = services.live.state().isBusy();
        buttons.add(new ClButton(0, 0, 110, 20, connected ? "Putuskan" : "Hubungkan",
                connected ? ClButton.Style.DANGER : ClButton.Style.PRIMARY,
                b -> {
                    if (services.live.state() == ConnectionState.CONNECTED) {
                        services.live.disconnect();
                    } else {
                        services.live.connect();
                    }
                    onShow(); // rebuild
                }).with(b -> b.enabled = !busy));
        buttons.add(new ClButton(120, 0, 110, 20, "Sambung Ulang", ClButton.Style.SECONDARY,
                b -> services.live.manualReconnect()));
    }

    @Override
    public void render(GuiGraphics g, Font font, int x, int y, int w, int h,
                       double mouseX, double mouseY, float alpha, long epochMs) {
        layout(x, y, w, h);
        scroll.render(g, alpha, () -> {
            int cx = scroll.x;
            int cy = scroll.y + (int) scroll.offset() + 4;
            float a = alpha;
            var live = services.live;
            var stats = live.stats();
            ConnectionState state = live.state();
            int stateColor = CraftLiveTheme.connectionStateColor(state.name());

            if (services.config.mockMode) {
                g.fill(cx, cy, cx + scroll.w, cy + 16, CraftLiveTheme.withAlpha(CraftLiveTheme.WARNING, a * 0.25f));
                g.drawString(font, "⚠ MOCK MODE — data simulasi", cx + 8, cy + 4,
                        CraftLiveTheme.withAlpha(CraftLiveTheme.WARNING, a), true);
                cy += 22;
            }

            // status card
            ClRender.roundedRectBordered(g, cx, cy, scroll.w, 44, 6,
                    CraftLiveTheme.withAlpha(CraftLiveTheme.PANEL, a * 0.9f),
                    CraftLiveTheme.withAlpha(stateColor, a * 0.7f));
            boolean isLive = stats.live && state.isConnected();
            String badge = isLive ? "● LIVE" : state.display().toUpperCase();
            float pulse = isLive ? ClRender.livePulse(epochMs) : 1f;
            g.drawString(font, badge, cx + 10, cy + 7, CraftLiveTheme.withAlpha(isLive ? CraftLiveTheme.LIVE_GREEN : stateColor, a * pulse), true);
            g.drawString(font, "@" + live.username(), cx + 10, cy + 26,
                    CraftLiveTheme.withAlpha(CraftLiveTheme.TEXT, a), true);
            cy += 52;

            // connection controls
            for (ClButton b : buttons) {
                b.render(g, font, mouseX, mouseY, a, true);
            }
            cy += 28;

            if (state == ConnectionState.ERROR && live.provider().lastError() != null) {
                g.drawString(font, "Error:", cx, cy, CraftLiveTheme.withAlpha(CraftLiveTheme.ERROR, a), true);
                cy += 12;
                for (String line : ClRender.wrap(font, live.provider().lastError(), scroll.w - 8)) {
                    g.drawString(font, line, cx + 6, cy, CraftLiveTheme.withAlpha(CraftLiveTheme.ERROR, a * 0.9f), true);
                    cy += 11;
                }
                cy += 6;
            }

            section(g, font, "Statistik Siaran", cx, cy, scroll.w, a);
            cy += 18;
            boolean show = state.isConnected();
            cy = row(g, font, "Status siaran", show ? (stats.live ? "LIVE" : "Koneksi OK, siaran belum mulai") : "—",
                    cx, cy, scroll.w, a, show && stats.live ? CraftLiveTheme.LIVE_GREEN : CraftLiveTheme.TEXT_DIM);
            cy = row(g, font, "Penonton", show ? String.valueOf(stats.viewers.get()) : "—", cx, cy, scroll.w, a, CraftLiveTheme.TEXT);
            cy = row(g, font, "Total suka", show ? String.valueOf(stats.totalLikes.get()) : "—", cx, cy, scroll.w, a, CraftLiveTheme.TEXT);
            cy = row(g, font, "Follow", show ? String.valueOf(stats.follows.get()) : "—", cx, cy, scroll.w, a, CraftLiveTheme.TEXT);
            cy = row(g, font, "Gift", show ? String.valueOf(stats.gifts.get()) : "—", cx, cy, scroll.w, a, CraftLiveTheme.TEXT);
            cy = row(g, font, "Share", show ? String.valueOf(stats.shares.get()) : "—", cx, cy, scroll.w, a, CraftLiveTheme.TEXT);
            cy = row(g, font, "Join", show ? String.valueOf(stats.joins.get()) : "—", cx, cy, scroll.w, a, CraftLiveTheme.TEXT);

            section(g, font, "Koneksi", cx, cy + 4, scroll.w, a);
            cy += 24;
            cy = row(g, font, "Provider", live.provider().name() + (services.config.mockMode ? " (MOCK)" : ""),
                    cx, cy, scroll.w, a, CraftLiveTheme.TEXT);
            cy = row(g, font, "Status", state.display(), cx, cy, scroll.w, a, stateColor);
            long last = stats.lastEventMs;
            String ago = last == 0 ? "—" : ((epochMs - last) / 1000) + " detik lalu";
            cy = row(g, font, "Event terakhir", ago, cx, cy, scroll.w, a, CraftLiveTheme.TEXT_DIM);

            contentHeight = cy - scroll.y + 8;
            scroll.setContentHeight(contentHeight);
        });
    }

}
