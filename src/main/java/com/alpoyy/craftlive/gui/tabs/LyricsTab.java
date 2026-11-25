package com.alpoyy.craftlive.gui.tabs;

import com.alpoyy.craftlive.CraftLiveServices;
import com.alpoyy.craftlive.gui.components.ClButton;
import com.alpoyy.craftlive.gui.components.ClSlider;
import com.alpoyy.craftlive.gui.components.ClTextField;
import com.alpoyy.craftlive.gui.components.ClToggle;
import com.alpoyy.craftlive.render.ClRender;
import com.alpoyy.craftlive.theme.CraftLiveTheme;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

/**
 * Lyrics tab: user-provided lyrics (manual or LRC sync), prev/next,
 * offset, visibility. Lyrics are NEVER downloaded — user pastes them.
 */
public class LyricsTab extends TabBase {
    private final CraftLiveServices services;
    private ClTextField titleField;
    private ClTextField lrcField;

    public LyricsTab(CraftLiveServices services) {
        this.services = services;
    }

    @Override
    public String id() {
        return "lyrics";
    }

    @Override
    public String title() {
        return "Lyrics";
    }

    @Override
    protected void build() {
        var lyrics = services.lyrics;
        buttons.add(new ClButton(0, 0, 110, 20, "Muat Manual", ClButton.Style.PRIMARY, b -> {
            lyrics.loadManualText(titleField.text(), lrcField.text());
            services.notifications.push(com.alpoyy.craftlive.notify.NotificationBus.Notification.Level.SUCCESS,
                    "Lyrics", "Lirik manual dimuat.");
        }));
        buttons.add(new ClButton(116, 0, 110, 20, "Muat LRC (sync)", ClButton.Style.SUCCESS, b -> {
            lyrics.loadLrc(titleField.text(), lrcField.text());
            services.notifications.push(com.alpoyy.craftlive.notify.NotificationBus.Notification.Level.SUCCESS,
                    "Lyrics", lyrics.isSynced() ? "LRC dimuat (sync)." : "LRC tanpa timestamp — mode manual.");
        }));
        buttons.add(new ClButton(232, 0, 70, 20, "◀ Sebelum", ClButton.Style.SECONDARY, b -> lyrics.manualPrev()));
        buttons.add(new ClButton(308, 0, 70, 20, "Berikut ▶", ClButton.Style.SECONDARY, b -> lyrics.manualNext()));
        buttons.add(new ClButton(384, 0, 70, 20, "Bersihkan", ClButton.Style.DANGER, b -> lyrics.clear()));
    }

    @Override
    public void render(GuiGraphics g, Font font, int x, int y, int w, int h,
                       double mouseX, double mouseY, float alpha, long epochMs) {
        layout(x, y, w, h);
        scroll.render(g, alpha, () -> {
            int cx = scroll.x;
            int cy = scroll.y + 4; // panel applies the -offset translate (was double-applied before)
            float a = alpha;
            var lyrics = services.lyrics;

            section(g, font, "Sumber Lirik (disediakan pengguna)", cx, cy, scroll.w, a);
            cy += 18;
            g.drawString(font, "CraftLive tidak mengunduh lirik — tempel lirik milikmu di sini.", cx, cy,
                    CraftLiveTheme.withAlpha(CraftLiveTheme.TEXT_DIM, a * 0.85f), true);
            cy += 16;

            g.drawString(font, "Judul:", cx, cy, CraftLiveTheme.withAlpha(CraftLiveTheme.TEXT_DIM, a), true);
            cy += 13;
            titleField = ensureTitleField(cx, cy, scroll.w);
            titleField.render(g, font, mouseX, mouseY, a, epochMs);
            cy += 26;

            g.drawString(font, "Lirik / LRC (satu baris per lirik):", cx, cy,
                    CraftLiveTheme.withAlpha(CraftLiveTheme.TEXT_DIM, a), true);
            cy += 13;
            lrcField = ensureLrcField(cx, cy, scroll.w);
            lrcField.render(g, font, mouseX, mouseY, a, epochMs);
            cy += 26;

            for (ClButton b : buttons) b.render(g, font, mouseX, mouseY, a, true);
            cy += 28;

            // mode info + offset
            section(g, font, "Mode", cx, cy, scroll.w, a);
            cy += 18;
            String mode = lyrics.isEmpty() ? "Kosong" : (lyrics.isSynced() ? "SYNC (timestamp LRC)" : "MANUAL");
            int modeColor = lyrics.isSynced() ? CraftLiveTheme.LIVE_GREEN : CraftLiveTheme.WARNING;
            g.drawString(font, "Mode aktif: ", cx, cy, CraftLiveTheme.withAlpha(CraftLiveTheme.TEXT_DIM, a), true);
            g.drawString(font, mode, cx + font.width("Mode aktif: "), cy, CraftLiveTheme.withAlpha(modeColor, a), true);
            cy += 16;

            toggles.clear();
            toggles.add(new ClToggle(cx, cy, "Ikuti playback (sync)", lyrics::isFollowing,
                    v -> lyrics.setFollowPlayback(v)));
            for (ClToggle t : toggles) t.render(g, font, mouseX, mouseY, a);
            cy += 24;

            // offset slider (±10 s) — label built from an int, never %d on a float
            sliders.clear();
            ClSlider offset = new ClSlider(cx, cy, 200, "Offset", () -> {
                long off = lyrics.manualOffsetMs();
                return Math.max(0f, Math.min(1f, 0.5f + off / 10000f));
            }, v -> lyrics.setManualOffsetMs((long) ((v - 0.5f) * 20000f)));
            offset.labelFormatter = v -> String.format("%+d ms", (int) Math.round((v - 0.5) * 20000));
            sliders.add(offset);
            offset.render(g, font, mouseX, mouseY, a);
            cy += 36;

            // preview of current line
            if (!lyrics.isEmpty()) {
                section(g, font, "Pratinjau", cx, cy, scroll.w, a);
                cy += 18;
                var lines = lyrics.lines();
                int active = lyrics.activeLineIndex();
                int shown = 0;
                for (int i = Math.max(0, active - 1); i < lines.size() && shown < 5; i++, shown++) {
                    boolean isCurrent = i == active;
                    g.drawString(font, ClRender.clip(font, lines.get(i).text, scroll.w - 20), cx, cy,
                            CraftLiveTheme.withAlpha(isCurrent ? CraftLiveTheme.TEXT : CraftLiveTheme.TEXT_DIM,
                                    isCurrent ? a : a * 0.6f), !isCurrent);
                    cy += 13;
                }
            }

            contentHeight = cy - scroll.y + 8;
            scroll.setContentHeight(contentHeight);
        });
    }

    private ClTextField ensureTitleField(int x, int y, int w) {
        if (titleField == null) {
            titleField = new ClTextField(x, y, w, 18, "Judul lagu", s -> {});
            fields.add(titleField);
        }
        titleField.x = x;
        titleField.y = y;
        titleField.w = w;
        return titleField;
    }

    private ClTextField ensureLrcField(int x, int y, int w) {
        if (lrcField == null) {
            lrcField = new ClTextField(x, y, w, 18, "[00:12.50] baris lirik  atau  teks polos", s -> {});
            fields.add(lrcField);
        }
        lrcField.x = x;
        lrcField.y = y;
        lrcField.w = w;
        return lrcField;
    }
}
