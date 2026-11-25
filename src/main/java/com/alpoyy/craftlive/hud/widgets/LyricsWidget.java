package com.alpoyy.craftlive.hud.widgets;

import com.alpoyy.craftlive.hud.HudWidget;
import com.alpoyy.craftlive.lyrics.LrcParser;
import com.alpoyy.craftlive.lyrics.LyricsManager;
import com.alpoyy.craftlive.render.ClRender;
import com.alpoyy.craftlive.theme.CraftLiveTheme;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

import java.util.List;

/**
 * Floating Lyrics widget: previous line subdued, current line prominent
 * (highlight + smooth fade/slide), next lines subdued. Controlled visible
 * area with scissor clipping — no overflow.
 */
public class LyricsWidget extends HudWidget {
    private final LyricsManager lyrics;
    private float slideAnim = 0f;
    private int lastActiveIndex = -1;

    public LyricsWidget(float x, float y) {
        super("lyrics", "Lyrics", x, y, 220, 92);
        this.lyrics = com.alpoyy.craftlive.CraftLiveServices.get().lyrics;
    }

    @Override
    protected void drawContent(GuiGraphics g, int w, int h, float alpha, boolean editorMode, long epochMs) {
        drawWidgetBackground(g, w, h, alpha, false);

        Font font = font();
        int pad = 8;
        float a = alpha;

        g.drawString(font, "♪ LYRICS", pad, 4, CraftLiveTheme.withAlpha(CraftLiveTheme.ACCENT_HOVER, a), true);
        if (lyrics.isSynced()) {
            g.drawString(font, "SYNC", w - font.width("SYNC") - pad, 4,
                    CraftLiveTheme.withAlpha(CraftLiveTheme.LIVE_GREEN, a), true);
        } else if (!lyrics.isEmpty()) {
            g.drawString(font, "MANUAL", w - font.width("MANUAL") - pad, 4,
                    CraftLiveTheme.withAlpha(CraftLiveTheme.WARNING, a), true);
        }

        if (editorMode && lyrics.isEmpty()) {
            g.drawString(font, "Lirik sebelumnya", pad, 22, CraftLiveTheme.withAlpha(CraftLiveTheme.TEXT_DIM, a * 0.7f), true);
            g.drawString(font, "LIRIK SAAT INI", pad, 36, CraftLiveTheme.withAlpha(CraftLiveTheme.TEXT, a), true);
            g.drawString(font, "Lirik berikutnya", pad, 54, CraftLiveTheme.withAlpha(CraftLiveTheme.TEXT_DIM, a * 0.7f), true);
            return;
        }

        List<LrcParser.Line> lines = lyrics.lines();
        if (lines.isEmpty()) {
            g.drawString(font, "Belum ada lirik", pad, 30, CraftLiveTheme.withAlpha(CraftLiveTheme.TEXT_DIM, a), true);
            g.drawString(font, "Tempel lirik di tab Lyrics", pad, 44, CraftLiveTheme.withAlpha(CraftLiveTheme.TEXT_DIM, a * 0.8f), true);
            return;
        }

        int active = lyrics.activeLineIndex();
        int lineHeight = 14;
        int visibleRows = (h - 22) / lineHeight;
        int half = visibleRows / 2;
        int start = Math.max(0, active - half);

        // slide animation when the active line changes
        if (active != lastActiveIndex) {
            slideAnim = 0f;
            lastActiveIndex = active;
        }
        slideAnim = Math.min(1f, slideAnim + 0.08f);
        float slideOffset = (1f - slideAnim) * 4f;

        g.enableScissor(pad, 18, w - pad, h - 4);
        int row = 0;
        for (int i = start; i < lines.size() && row < visibleRows; i++, row++) {
            int y = 20 + row * lineHeight;
            boolean isCurrent = i == active;
            float lineAlpha = isCurrent ? a : a * (Math.abs(i - active) == 1 ? 0.55f : 0.35f);
            int color = isCurrent ? CraftLiveTheme.TEXT : CraftLiveTheme.TEXT_DIM;
            int offsetY = isCurrent ? (int) -slideOffset : 0;
            String text = ClRender.clip(font, lines.get(i).text, w - pad * 2);
            g.drawString(font, text, pad, y + offsetY,
                    CraftLiveTheme.withAlpha(color, lineAlpha), !isCurrent);
            if (isCurrent && lyrics.isSynced()) {
                g.fill(pad, y + 11 + offsetY, pad + font.width(text), y + 12 + offsetY,
                        CraftLiveTheme.withAlpha(CraftLiveTheme.ACCENT, a * 0.8f));
            }
        }
        g.disableScissor();
    }
}
