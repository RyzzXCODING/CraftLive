package com.alpoyy.craftlive.hud.widgets;

import com.alpoyy.craftlive.hud.HudWidget;
import com.alpoyy.craftlive.render.ClRender;
import com.alpoyy.craftlive.request.RequestManager;
import com.alpoyy.craftlive.request.SongRequest;
import com.alpoyy.craftlive.theme.CraftLiveTheme;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

/**
 * PRIORITY widget: the most prominent floating widget.
 * Large song title, requester, artist, queue number, status, next request,
 * queue count and countdown. No text overflow (marquee + clipping).
 */
public class SongRequestWidget extends HudWidget {
    private final RequestManager requests;
    private com.alpoyy.craftlive.util.TextClip.Marquee titleMarquee =
            new com.alpoyy.craftlive.util.TextClip.Marquee(0, 240 - 16, 26f, 900, 900);
    private String marqueeText = null;

    public SongRequestWidget(float x, float y) {
        super("song_request", "Song Request", x, y, 240, 96);
        requests = com.alpoyy.craftlive.CraftLiveServices.get().requests;
    }

    @Override
    protected void drawContent(GuiGraphics g, int w, int h, float alpha, boolean editorMode, long epochMs) {
        drawWidgetBackground(g, w, h, alpha, false);

        Font font = font();
        int pad = 8;
        float a = alpha;

        // header strip
        g.fill(0, 0, w, 18, CraftLiveTheme.withAlpha(CraftLiveTheme.PANEL_2, a));
        g.drawString(font, "♪ REQUEST LAGU", pad, 5, CraftLiveTheme.withAlpha(CraftLiveTheme.ACCENT_HOVER, a), true);
        String count = requests.queue().size() + "/" + requests.queue().maxQueue();
        g.drawString(font, count, w - font.width(count) - pad, 5, CraftLiveTheme.withAlpha(CraftLiveTheme.TEXT_DIM, a), true);

        SongRequest current = requests.currentRequest();

        if (editorMode && current == null) {
            g.drawString(font, "Judul Lagu (preview)", pad, 26, CraftLiveTheme.withAlpha(CraftLiveTheme.TEXT, a), true);
            g.drawString(font, "@requester", pad, 52, CraftLiveTheme.withAlpha(CraftLiveTheme.ACCENT_HOVER, a), true);
            g.drawString(font, "#1 SEDANG", pad, 24, CraftLiveTheme.withAlpha(CraftLiveTheme.ACCENT_HOVER, a), true);
            g.drawString(font, "Berikutnya: judul berikutnya", pad, h - 12, CraftLiveTheme.withAlpha(CraftLiveTheme.TEXT_DIM, a), true);
            return;
        }

        if (current == null) {
            g.drawString(font, "Tidak ada request", pad, 28, CraftLiveTheme.withAlpha(CraftLiveTheme.TEXT_DIM, a), true);
            String hint = "Kirim \"!req <judul>\" di chat";
            g.drawString(font, hint, pad, h - 12, CraftLiveTheme.withAlpha(CraftLiveTheme.TEXT_DIM, a * 0.8f), true);
            return;
        }

        // queue number + status row
        int pos = requests.queue().position(current.id);
        String posText = pos > 0 ? "#" + pos : "SEDANG";
        g.drawString(font, posText, pad, 24, CraftLiveTheme.withAlpha(CraftLiveTheme.ACCENT_HOVER, a), true);
        String status = current.status.display();
        g.drawString(font, status, w - font.width(status) - pad, 24,
                CraftLiveTheme.withAlpha(CraftLiveTheme.LIVE_GREEN, a), true);

        // big title with marquee (paused at start, slow scroll, pause at end)
        int titleY = 38;
        int titleW = w - pad * 2;
        String title = current.track == null || current.track.isBlank() ? current.title : current.track;
        if (!title.equals(marqueeText) || titleMarquee.viewWidth != titleW) {
            marqueeText = title;
            titleMarquee = new com.alpoyy.craftlive.util.TextClip.Marquee(font.width(title), titleW, 26f, 900, 900);
        }
        titleMarquee.advance(16);
        g.enableScissor(pad, titleY - 2, pad + titleW, titleY + 12);
        if (titleMarquee.isActive()) {
            g.drawString(font, title, pad + (int) titleMarquee.offset(), titleY,
                    CraftLiveTheme.withAlpha(CraftLiveTheme.TEXT, a), true);
        } else {
            g.drawString(font, title, pad, titleY, CraftLiveTheme.withAlpha(CraftLiveTheme.TEXT, a), true);
        }
        g.disableScissor();

        // requester
        String requester = "@" + ClRender.clip(font, current.requester, w - pad * 2);
        g.drawString(font, requester, pad, 52, CraftLiveTheme.withAlpha(CraftLiveTheme.ACCENT_HOVER, a * 0.95f), true);

        // artist (when part of the request text)
        if (!current.artist.isBlank()) {
            g.drawString(font, ClRender.clip(font, current.artist, w - pad * 2 - 44), pad, 64,
                    CraftLiveTheme.withAlpha(CraftLiveTheme.TEXT_DIM, a), true);
        }

        // countdown
        long remain = requests.countdownRemainingMs();
        if (remain > 0) {
            String cd = String.format("%.1fs", remain / 1000.0);
            g.drawString(font, cd, w - font.width(cd) - pad, 64,
                    CraftLiveTheme.withAlpha(CraftLiveTheme.WARNING, a), true);
        }

        // next request line
        SongRequest next = requests.queue().peekNext();
        int nextY = h - 12;
        if (next != null) {
            String prefix = "Berikutnya: ";
            String body = ClRender.clip(font, next.track, w - pad * 2 - font.width(prefix));
            g.drawString(font, prefix + body, pad, nextY, CraftLiveTheme.withAlpha(CraftLiveTheme.TEXT_DIM, a), true);
        } else {
            g.drawString(font, "Antrean kosong", pad, nextY, CraftLiveTheme.withAlpha(CraftLiveTheme.TEXT_DIM, a * 0.8f), true);
        }
    }
}
