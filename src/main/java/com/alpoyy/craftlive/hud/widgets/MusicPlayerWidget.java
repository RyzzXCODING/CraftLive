package com.alpoyy.craftlive.hud.widgets;

import com.alpoyy.craftlive.hud.HudWidget;
import com.alpoyy.craftlive.music.MusicPlatform;
import com.alpoyy.craftlive.music.PlayerState;
import com.alpoyy.craftlive.music.Track;
import com.alpoyy.craftlive.render.ClRender;
import com.alpoyy.craftlive.theme.CraftLiveTheme;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

/**
 * Music Player mini-widget: platform + status, marquee title, artist,
 * progress bar, compact controls when the platform supports them.
 * Unknown data shows placeholders — never fabricated.
 */
public class MusicPlayerWidget extends HudWidget {
    public MusicPlayerWidget(float x, float y) {
        super("music_player", "Music Player", x, y, 210, 64);
    }

    @Override
    protected void drawContent(GuiGraphics g, int w, int h, float alpha, boolean editorMode, long epochMs) {
        var services = com.alpoyy.craftlive.CraftLiveServices.get();
        drawWidgetBackground(g, w, h, alpha, services.streamMode.shouldSimplify());

        Font font = font();
        int pad = 8;
        float a = alpha;

        MusicPlatform platform = services.preferredPlatform();
        PlayerState state = platform == null ? null : platform.state();

        // header: platform + status
        String platformName = platform == null ? "Music" : platform.displayName();
        g.drawString(font, platformName, pad, 5, CraftLiveTheme.withAlpha(CraftLiveTheme.TEXT_DIM, a), true);
        String status = state == null ? "Tidak terhubung" : state.statusDisplay();
        int statusColor = state == null ? CraftLiveTheme.TEXT_DIM
                : switch (state.status) {
                    case PLAYING, CONNECTED -> CraftLiveTheme.LIVE_GREEN;
                    case PAUSED, CONNECTING -> CraftLiveTheme.WARNING;
                    case ERROR -> CraftLiveTheme.ERROR;
                    default -> CraftLiveTheme.TEXT_DIM;
                };
        g.drawString(font, status, w - font.width(status) - pad, 5,
                CraftLiveTheme.withAlpha(statusColor, a), true);

        if (editorMode) {
            g.drawString(font, "Judul lagu (preview)", pad, 19, CraftLiveTheme.withAlpha(CraftLiveTheme.TEXT, a), true);
            g.drawString(font, "Artis", pad, 31, CraftLiveTheme.withAlpha(CraftLiveTheme.TEXT_DIM, a), true);
            ClRender.progressBar(g, pad, 46, w - pad * 2, 5, 0.4f,
                    CraftLiveTheme.withAlpha(CraftLiveTheme.PANEL_2, a),
                    CraftLiveTheme.withAlpha(CraftLiveTheme.accent(), a));
            return;
        }

        Track track = state == null ? null : state.current;
        if (track == null) {
            g.drawString(font, "Tidak ada musik", pad, 21, CraftLiveTheme.withAlpha(CraftLiveTheme.TEXT_DIM, a), true);
            return;
        }

        // title marquee
        int titleW = w - pad * 2;
        String title = track.primaryText();
        com.alpoyy.craftlive.util.TextClip.Marquee marquee = marqueeFor(title, font.width(title), titleW);
        marquee.advance(16);
        g.enableScissor(pad, 17, pad + titleW, 31);
        if (marquee.isActive()) {
            g.drawString(font, title, pad + (int) marquee.offset(), 19, CraftLiveTheme.withAlpha(CraftLiveTheme.TEXT, a), true);
        } else {
            g.drawString(font, title, pad, 19, CraftLiveTheme.withAlpha(CraftLiveTheme.TEXT, a), true);
        }
        g.disableScissor();

        g.drawString(font, ClRender.clip(font, track.secondaryText(), titleW), pad, 31,
                CraftLiveTheme.withAlpha(CraftLiveTheme.TEXT_DIM, a), true);

        // progress
        long now = System.currentTimeMillis();
        long progress = state.displayedProgressMs(now);
        if (progress >= 0 && track.durationMs > 0) {
            ClRender.progressBar(g, pad, 46, w - pad * 2 - 34, 5, (float) progress / track.durationMs,
                    CraftLiveTheme.withAlpha(CraftLiveTheme.PANEL_2, a),
                    CraftLiveTheme.withAlpha(CraftLiveTheme.accent(), a));
            String time = formatMs(progress) + "/" + formatMs(track.durationMs);
            g.drawString(font, time, w - font.width(time) - pad, 43, CraftLiveTheme.withAlpha(CraftLiveTheme.TEXT_DIM, a), true);
        } else {
            ClRender.progressBar(g, pad, 46, w - pad * 2, 5, 0f,
                    CraftLiveTheme.withAlpha(CraftLiveTheme.PANEL_2, a),
                    CraftLiveTheme.withAlpha(CraftLiveTheme.accent(), a * 0.5f));
        }
    }

    private com.alpoyy.craftlive.util.TextClip.Marquee cached;
    private String cachedText;
    private int cachedWidth = -1;

    private com.alpoyy.craftlive.util.TextClip.Marquee marqueeFor(String text, int textWidth, int viewWidth) {
        if (!text.equals(cachedText) || cachedWidth != viewWidth) {
            cachedText = text;
            cachedWidth = viewWidth;
            cached = new com.alpoyy.craftlive.util.TextClip.Marquee(textWidth, viewWidth, 24f, 900, 900);
        }
        return cached;
    }

    private static String formatMs(long ms) {
        long total = ms / 1000;
        return String.format("%d:%02d", total / 60, total % 60);
    }
}
