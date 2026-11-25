package com.alpoyy.craftlive.hud.widgets;

import com.alpoyy.craftlive.chat.ChatStore;
import com.alpoyy.craftlive.hud.HudWidget;
import com.alpoyy.craftlive.render.ClRender;
import com.alpoyy.craftlive.theme.CraftLiveTheme;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

import java.util.List;

/**
 * Floating Live Chat feed: usernames colored, timestamps, event types,
 * smooth auto-scroll, pause-on-scroll handled by the manager, wrapping,
 * bounded retention.
 */
public class ChatFeedWidget extends HudWidget {
    private final ChatStore chat;
    private int scrollOffset = 0;      // lines from bottom; 0 = latest
    private boolean autoScroll = true;

    public ChatFeedWidget(float x, float y) {
        super("live_chat", "Live Chat", x, y, 220, 150);
        this.chat = com.alpoyy.craftlive.CraftLiveServices.get().chat;
    }

    public void scrollBy(int lines) {
        scrollOffset = Math.max(0, scrollOffset + lines);
        autoScroll = scrollOffset == 0;
    }

    public void setAutoScroll(boolean auto) {
        this.autoScroll = auto;
        if (auto) scrollOffset = 0;
    }

    public boolean isAutoScroll() {
        return autoScroll;
    }

    @Override
    protected void drawContent(GuiGraphics g, int w, int h, float alpha, boolean editorMode, long epochMs) {
        var services = com.alpoyy.craftlive.CraftLiveServices.get();
        drawWidgetBackground(g, w, h, alpha, services.streamMode.shouldSimplify());

        Font font = font();
        int pad = 6;
        float a = alpha;

        g.drawString(font, "LIVE CHAT", pad, 4, CraftLiveTheme.withAlpha(CraftLiveTheme.ACCENT_HOVER, a), true);

        List<ChatStore.Entry> entries = chat.snapshot();
        int lineHeight = 11;
        int visibleRows = (h - 20) / lineHeight;
        if (visibleRows <= 0) return;

        // wrap-free compact rows: clip each line, newest at bottom
        int end = entries.size() - scrollOffset;
        int start = Math.max(0, end - visibleRows);
        int row = 0;
        for (int i = Math.max(0, start); i < end && row < visibleRows; i++, row++) {
            ChatStore.Entry e = entries.get(i);
            int y = 16 + row * lineHeight;

            int nameColor = switch (e.event.type) {
                case GIFT -> CraftLiveTheme.withAlpha(CraftLiveTheme.ACCENT_HOVER, a);
                case FOLLOW -> CraftLiveTheme.withAlpha(CraftLiveTheme.LIVE_GREEN, a);
                case LIKE -> CraftLiveTheme.withAlpha(CraftLiveTheme.WARNING, a);
                default -> CraftLiveTheme.withAlpha(CraftLiveTheme.TEXT_DIM, a);
            };
            String name = "@" + e.event.senderDisplay();
            if (services.streamMode.shouldHideConnectionInfo()) name = "@•••";
            int nameW = ClRender.drawClipped(g, font, name, pad, y, 66, nameColor, true);

            String body = e.event.summary();
            int msgColor = e.highlighted
                    ? CraftLiveTheme.withAlpha(CraftLiveTheme.WARNING, a)
                    : CraftLiveTheme.withAlpha(CraftLiveTheme.TEXT, a);
            if (!body.isEmpty()) {
                ClRender.drawClipped(g, font, body, pad + nameW + 5, y, w - pad * 2 - nameW - 5, msgColor, true);
            }
        }

        if (scrollOffset > 0) {
            String indicator = "▲ jeda (" + scrollOffset + ")";
            g.drawString(font, indicator, w - font.width(indicator) - pad, h - 12,
                    CraftLiveTheme.withAlpha(CraftLiveTheme.WARNING, a), true);
        }
    }
}
