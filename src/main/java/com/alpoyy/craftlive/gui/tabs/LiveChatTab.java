package com.alpoyy.craftlive.gui.tabs;

import com.alpoyy.craftlive.CraftLiveServices;
import com.alpoyy.craftlive.chat.ChatStore;
import com.alpoyy.craftlive.gui.components.ClButton;
import com.alpoyy.craftlive.gui.components.ClToggle;
import com.alpoyy.craftlive.render.ClRender;
import com.alpoyy.craftlive.theme.CraftLiveTheme;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

import java.util.List;

/**
 * Live Chat tab: full feed with wrapped messages, filters, pause/resume
 * auto-scroll, highlight, spam protection, retention setting.
 */
public class LiveChatTab extends TabBase {
    private final CraftLiveServices services;
    private boolean autoScroll = true;

    public LiveChatTab(CraftLiveServices services) {
        this.services = services;
    }

    @Override
    public String id() {
        return "live_chat";
    }

    @Override
    public String title() {
        return "Live Chat";
    }

    @Override
    protected void build() {
        buttons.add(new ClButton(0, 0, 90, 20, autoScroll ? "Jeda Scroll" : "Lanjut Scroll",
                autoScroll ? ClButton.Style.SECONDARY : ClButton.Style.SUCCESS,
                b -> {
                    autoScroll = !autoScroll;
                    onShow();
                }));
        buttons.add(new ClButton(100, 0, 80, 20, "Bersihkan", ClButton.Style.DANGER,
                b -> services.chat.clear()));
    }

    @Override
    public void render(GuiGraphics g, Font font, int x, int y, int w, int h,
                       double mouseX, double mouseY, float alpha, long epochMs) {
        layout(x, y, w, h);
        scroll.render(g, alpha, () -> {
            int cx = scroll.x;
            int cy = scroll.y + (int) scroll.offset() + 4;
            float a = alpha;

            if (services.config.mockMode) {
                g.fill(cx, cy, cx + scroll.w, cy + 16, CraftLiveTheme.withAlpha(CraftLiveTheme.WARNING, a * 0.25f));
                g.drawString(font, "⚠ MOCK MODE — chat simulasi", cx + 8, cy + 4,
                        CraftLiveTheme.withAlpha(CraftLiveTheme.WARNING, a), true);
                cy += 22;
            }

            for (ClButton b : buttons) b.render(g, font, mouseX, mouseY, a, true);
            cy += 28;

            // filters
            section(g, font, "Filter Tampilan", cx, cy, scroll.w, a);
            cy += 18;
            var cfg = services.config;
            toggles.clear();
            toggles.add(new ClToggle(cx, cy, "Tampilkan gift", () -> cfg.chatShowGifts, v -> cfg.chatShowGifts = v));
            toggles.add(new ClToggle(cx + 200, cy, "Follow", () -> cfg.chatShowFollows, v -> cfg.chatShowFollows = v));
            toggles.add(new ClToggle(cx + 320, cy, "Join", () -> cfg.chatShowJoins, v -> cfg.chatShowJoins = v));
            cy += 18;
            toggles.add(new ClToggle(cx, cy, "Spam protection", () -> cfg.chatSpamProtection, v -> {
                cfg.chatSpamProtection = v;
                services.chat.configure(cfg.chatMaxMessages, cfg.chatShowGifts, cfg.chatShowFollows,
                        cfg.chatShowJoins, cfg.chatSpamProtection, cfg.chatSpamThreshold, cfg.chatHighlightWords);
            }));
            toggles.add(new ClToggle(cx + 200, cy, "Timestamp", () -> cfg.chatShowTimestamps, v -> cfg.chatShowTimestamps = v));
            for (ClToggle t : toggles) t.render(g, font, mouseX, mouseY, a);
            cy += 26;

            // chat feed
            section(g, font, "Pesan (" + services.chat.size() + ")", cx, cy, scroll.w, a);
            cy += 18;

            List<ChatStore.Entry> entries = services.chat.snapshot();
            // newest last; auto-scroll pins to bottom (content grows upward naturally in scroll panel)
            int feedTop = cy;
            int feedH = Math.max(120, contentHeight - (feedTop - scroll.y) - 12);
            g.enableScissor(cx, feedTop, cx + scroll.w, feedTop + feedH);
            int lineY = feedTop + feedH;
            for (int i = entries.size() - 1; i >= 0 && lineY > feedTop - 14; i--) {
                ChatStore.Entry e = entries.get(i);
                var lines = ClRender.wrap(font, formatEntry(font, e, cfg), scroll.w - 20);
                lineY -= lines.size() * 11;
                if (lineY < feedTop - 14) break;
                int yy = lineY;
                int nameColor = switch (e.event.type) {
                    case GIFT -> CraftLiveTheme.accentHover();
                    case FOLLOW -> CraftLiveTheme.LIVE_GREEN;
                    case JOIN -> CraftLiveTheme.TEXT_DIM;
                    default -> CraftLiveTheme.TEXT_DIM;
                };
                // name prefix drawn inline for the first line
                String prefix = "@" + e.event.senderDisplay() + ": ";
                String firstLine = lines.isEmpty() ? "" : lines.get(0);
                int prefixW = e.event.type == com.alpoyy.craftlive.live.LiveEvent.Type.CHAT ? font.width(prefix) : 0;
                if (e.event.type == com.alpoyy.craftlive.live.LiveEvent.Type.CHAT) {
                    g.drawString(font, prefix, cx + 4, yy,
                            CraftLiveTheme.withAlpha(nameColor, e.highlighted ? a : a * 0.9f), true);
                    g.drawString(font, firstLine, cx + 4 + prefixW, yy,
                            CraftLiveTheme.withAlpha(e.highlighted ? CraftLiveTheme.WARNING : CraftLiveTheme.TEXT, a), true);
                } else {
                    g.drawString(font, "• " + firstLine, cx + 4, yy,
                            CraftLiveTheme.withAlpha(nameColor, a), true);
                }
                for (int li = 1; li < lines.size(); li++) {
                    g.drawString(font, lines.get(li), cx + 4 + (e.event.type == com.alpoyy.craftlive.live.LiveEvent.Type.CHAT ? prefixW : 0),
                            yy + li * 11, CraftLiveTheme.withAlpha(CraftLiveTheme.TEXT, a * 0.95f), true);
                }
            }
            g.disableScissor();

            contentHeight = (feedTop - scroll.y) + feedH + 16;
            scroll.setContentHeight(contentHeight);
        });
    }

    private String formatEntry(Font font, ChatStore.Entry e, com.alpoyy.craftlive.config.CraftLiveConfig cfg) {
        String body = e.event.summary();
        if (cfg.chatShowTimestamps) {
            String time = java.time.LocalTime.ofInstant(java.time.Instant.ofEpochMilli(e.receivedMs),
                    java.time.ZoneId.systemDefault()).format(java.time.format.DateTimeFormatter.ofPattern("HH:mm"));
            body = "[" + time + "] " + body;
        }
        return body;
    }
}
