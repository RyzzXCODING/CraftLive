package com.alpoyy.craftlive.hud.widgets;

import com.alpoyy.craftlive.hud.HudWidget;
import com.alpoyy.craftlive.live.ConnectionState;
import com.alpoyy.craftlive.live.LiveStats;
import com.alpoyy.craftlive.render.ClRender;
import com.alpoyy.craftlive.theme.CraftLiveTheme;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

/**
 * LIVE status widget: LIVE/OFFLINE indicator with subtle pulse, viewer
 * count, likes, follows, gifts, connection state, last update time.
 * Shows only real data; offline shows no numbers.
 */
public class LiveStatusWidget extends HudWidget {
    private final com.alpoyy.craftlive.live.LiveConnectionManager live;

    public LiveStatusWidget(float x, float y) {
        super("live_status", "Live Status", x, y, 170, 74);
        this.live = com.alpoyy.craftlive.CraftLiveServices.get().live;
    }

    @Override
    protected void drawContent(GuiGraphics g, int w, int h, float alpha, boolean editorMode, long epochMs) {
        var services = com.alpoyy.craftlive.CraftLiveServices.get();
        boolean hideInfo = services.streamMode.shouldHideConnectionInfo();
        drawWidgetBackground(g, w, h, alpha, services.streamMode.shouldSimplify());

        Font font = font();
        int pad = 8;
        float a = alpha;
        LiveStats stats = live.stats();
        ConnectionState state = live.state();

        // LIVE badge with pulse
        boolean isLive = stats.live || state.isConnected() && stats.viewers.get() > 0;
        String badge;
        int badgeColor;
        if (isLive && state.isConnected()) {
            badge = "● LIVE";
            float pulse = ClRender.livePulse(epochMs);
            badgeColor = CraftLiveTheme.withAlpha(CraftLiveTheme.LIVE_GREEN, a * pulse);
        } else if (state == ConnectionState.CONNECTING || state == ConnectionState.RECONNECTING) {
            badge = "○ " + state.display().toUpperCase();
            badgeColor = CraftLiveTheme.withAlpha(CraftLiveTheme.WARNING, a * (0.6f + 0.4f * ClRender.livePulse(epochMs)));
        } else if (state == ConnectionState.ERROR) {
            badge = "✕ ERROR";
            badgeColor = CraftLiveTheme.withAlpha(CraftLiveTheme.ERROR, a);
        } else {
            badge = "OFFLINE";
            badgeColor = CraftLiveTheme.withAlpha(CraftLiveTheme.ERROR, a * 0.85f);
        }
        g.drawString(font, badge, pad, 6, badgeColor, true);

        // username
        String user = "@" + live.username();
        if (hideInfo) user = "@•••••";
        g.drawString(font, ClRender.clip(font, user, w - pad * 2), pad, 20,
                CraftLiveTheme.withAlpha(CraftLiveTheme.TEXT, a), true);

        if (editorMode) {
            g.drawString(font, "👤 1.2K   ❤ 3.4K", pad, 34, CraftLiveTheme.withAlpha(CraftLiveTheme.TEXT_DIM, a), true);
            g.drawString(font, "EulerStream: Connected", pad, 48, CraftLiveTheme.withAlpha(CraftLiveTheme.LIVE_GREEN, a), true);
            return;
        }

        if (state.isConnected()) {
            String viewers = "👤 " + format(stats.viewers.get());
            String likes = "❤ " + format(stats.totalLikes.get());
            g.drawString(font, viewers, pad, 34, CraftLiveTheme.withAlpha(CraftLiveTheme.TEXT, a), true);
            g.drawString(font, likes, pad + 64, 34, CraftLiveTheme.withAlpha(CraftLiveTheme.TEXT, a), true);

            String follows = "➕ " + format(stats.follows.get());
            String gifts = "🎁 " + format(stats.gifts.get());
            g.drawString(font, follows, pad, 48, CraftLiveTheme.withAlpha(CraftLiveTheme.TEXT_DIM, a), true);
            g.drawString(font, gifts, pad + 64, 48, CraftLiveTheme.withAlpha(CraftLiveTheme.TEXT_DIM, a), true);
        } else {
            String msg = switch (state) {
                case NOT_CONFIGURED -> "Belum dikonfigurasi";
                case CONNECTING -> "Menghubungkan…";
                case RECONNECTING -> "Menyambung ulang…";
                case ERROR -> "Koneksi error";
                default -> "Tidak terhubung";
            };
            g.drawString(font, msg, pad, 34, CraftLiveTheme.withAlpha(CraftLiveTheme.TEXT_DIM, a), true);
            if (state == ConnectionState.ERROR && live.provider().lastError() != null) {
                g.drawString(font, ClRender.clip(font, live.provider().lastError(), w - pad * 2), pad, 48,
                        CraftLiveTheme.withAlpha(CraftLiveTheme.ERROR, a * 0.9f), true);
            }
        }

        // last update
        long last = stats.lastEventMs;
        if (last > 0 && state.isConnected()) {
            long ago = (epochMs - last) / 1000;
            String agoText = ago < 60 ? ago + "s lalu" : (ago / 60) + "m lalu";
            g.drawString(font, agoText, w - font.width(agoText) - pad, h - 12,
                    CraftLiveTheme.withAlpha(CraftLiveTheme.TEXT_DIM, a * 0.7f), true);
        }
    }

    private static String format(long n) {
        if (n >= 1_000_000) return String.format("%.1fM", n / 1_000_000.0);
        if (n >= 1_000) return String.format("%.1fK", n / 1_000.0);
        return String.valueOf(n);
    }
}
