package com.alpoyy.craftlive.gui.tabs;

import com.alpoyy.craftlive.CraftLiveServices;
import com.alpoyy.craftlive.gui.components.ClButton;
import com.alpoyy.craftlive.gui.components.ClToggle;
import com.alpoyy.craftlive.request.RequestManager;
import com.alpoyy.craftlive.request.SongRequest;
import com.alpoyy.craftlive.render.ClRender;
import com.alpoyy.craftlive.theme.CraftLiveTheme;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

import java.util.ArrayList;
import java.util.List;

/**
 * Song Requests tab: full queue management (FIFO, remove, skip, clear,
 * pause/resume), approval section, live countdown, quick settings.
 */
public class SongRequestTab extends TabBase {
    private final CraftLiveServices services;

    public SongRequestTab(CraftLiveServices services) {
        this.services = services;
    }

    @Override
    public String id() {
        return "requests";
    }

    @Override
    public String title() {
        return "Song Requests";
    }

    @Override
    protected void build() {
        buttons.add(new ClButton(0, 0, 90, 20, "Lewati", ClButton.Style.SECONDARY,
                b -> services.requests.skipCurrent()));
        buttons.add(new ClButton(96, 0, 90, 20,
                services.requests.queue().isPaused() ? "Lanjutkan" : "Jeda Antrean",
                services.requests.queue().isPaused() ? ClButton.Style.SUCCESS : ClButton.Style.SECONDARY,
                b -> {
                    services.requests.setPaused(!services.requests.queue().isPaused());
                    onShow();
                }));
        buttons.add(new ClButton(192, 0, 100, 20, "Kosongkan Antrean", ClButton.Style.DANGER,
                b -> services.requests.clearQueue()));
        buttons.add(new ClButton(298, 0, 110, 20, "Putar Manual (YT)", ClButton.Style.SUCCESS,
                b -> {
                    var cur = services.requests.currentRequest();
                    if (cur != null) {
                        String q = cur.artist.isBlank() ? cur.track : cur.artist + " " + cur.track;
                        services.youtube.searchAndPlay(q, err ->
                                services.notifications.push(com.alpoyy.craftlive.notify.NotificationBus.Notification.Level.ERROR,
                                        "YouTube Music", err));
                    }
                }).with(b -> b.enabled = services.requests.currentRequest() != null));
    }

    @Override
    public void render(GuiGraphics g, Font font, int x, int y, int w, int h,
                       double mouseX, double mouseY, float alpha, long epochMs) {
        layout(x, y, w, h);
        scroll.render(g, alpha, () -> {
            int cx = scroll.x;
            int cy = scroll.y + (int) scroll.offset() + 4;
            float a = alpha;
            RequestManager rm = services.requests;

            if (services.config.mockMode) {
                g.fill(cx, cy, cx + scroll.w, cy + 16, CraftLiveTheme.withAlpha(CraftLiveTheme.WARNING, a * 0.25f));
                g.drawString(font, "⚠ MOCK MODE — antrean simulasi", cx + 8, cy + 4,
                        CraftLiveTheme.withAlpha(CraftLiveTheme.WARNING, a), true);
                cy += 22;
            }

            // current request card (priority)
            section(g, font, "Request Aktif", cx, cy, scroll.w, a);
            cy += 18;
            var current = rm.currentRequest();
            ClRender.roundedRectBordered(g, cx, cy, scroll.w, 40, 6,
                    CraftLiveTheme.withAlpha(CraftLiveTheme.PANEL, a * 0.95f),
                    CraftLiveTheme.withAlpha(CraftLiveTheme.accent(), a * 0.8f));
            if (current != null) {
                String title = current.track == null ? current.title : current.track;
                g.drawString(font, ClRender.clip(font, title, scroll.w - 90), cx + 8, cy + 6,
                        CraftLiveTheme.withAlpha(CraftLiveTheme.TEXT, a), true);
                String by = "@" + current.requester + (current.artist.isBlank() ? "" : "  •  " + current.artist);
                g.drawString(font, ClRender.clip(font, by, scroll.w - 90), cx + 8, cy + 19,
                        CraftLiveTheme.withAlpha(CraftLiveTheme.TEXT_DIM, a), true);
                long remain = rm.countdownRemainingMs();
                String right = current.status.display() + (remain > 0 ? String.format("  %.1fs", remain / 1000.0) : "");
                g.drawString(font, right, cx + scroll.w - font.width(right) - 8, cy + 6,
                        CraftLiveTheme.withAlpha(CraftLiveTheme.LIVE_GREEN, a), true);
            } else {
                g.drawString(font, "Tidak ada request aktif", cx + 8, cy + 14,
                        CraftLiveTheme.withAlpha(CraftLiveTheme.TEXT_DIM, a), true);
            }
            cy += 48;

            for (ClButton b : buttons) b.render(g, font, mouseX, mouseY, a, true);
            cy += 28;

            // quick settings
            toggles.clear();
            var cfg = services.config;
            toggles.add(new ClToggle(cx, cy, "Auto-approve", () -> cfg.requestAutoApprove, v -> cfg.requestAutoApprove = v));
            toggles.add(new ClToggle(cx + 200, cy, "Duplikat ditolak", () -> cfg.requestDuplicateProtection, v -> cfg.requestDuplicateProtection = v));
            toggles.add(new ClToggle(cx + 380, cy, "Spam protection", () -> cfg.requestSpamProtection, v -> cfg.requestSpamProtection = v));
            for (ClToggle t : toggles) t.render(g, font, mouseX, mouseY, a);
            cy += 26;

            // pending approval
            if (!cfg.requestAutoApprove && !rm.pendingApproval().isEmpty()) {
                section(g, font, "Menunggu Persetujuan (" + rm.pendingApproval().size() + ")", cx, cy, scroll.w, a);
                cy += 18;
                List<SongRequest> pending = new ArrayList<>(rm.pendingApproval());
                for (SongRequest r : pending) {
                    String line = "@" + r.requester + ": " + r.track;
                    g.drawString(font, ClRender.clip(font, line, scroll.w - 160), cx, cy,
                            CraftLiveTheme.withAlpha(CraftLiveTheme.TEXT, a), true);
                    ensureApproveBtn(cx + scroll.w - 130, cy - 2, r).render(g, font, mouseX, mouseY, a, true);
                    ensureRejectBtn(cx + scroll.w - 64, cy - 2, r).render(g, font, mouseX, mouseY, a, true);
                    cy += 18;
                }
                cy += 4;
            }

            // queue
            section(g, font, "Antrean (" + rm.queue().size() + "/" + rm.queue().maxQueue() + ")", cx, cy, scroll.w, a);
            cy += 18;
            List<SongRequest> queue = rm.queueSnapshot();
            if (queue.isEmpty()) {
                g.drawString(font, "Antrean kosong — kirim \"" + cfg.requestCommand + " <judul lagu>\" di chat",
                        cx, cy, CraftLiveTheme.withAlpha(CraftLiveTheme.TEXT_DIM, a), true);
                cy += 16;
            } else {
                int i = 1;
                for (SongRequest r : queue) {
                    String line = i + ". " + r.track + "  —  @" + r.requester;
                    g.drawString(font, ClRender.clip(font, line, scroll.w - 110), cx, cy,
                            CraftLiveTheme.withAlpha(CraftLiveTheme.TEXT, a), true);
                    String status = r.status.display();
                    g.drawString(font, status, cx + scroll.w - 100, cy,
                            CraftLiveTheme.withAlpha(CraftLiveTheme.TEXT_DIM, a), true);
                    ensureRemoveBtn(cx + scroll.w - 30, cy - 2, r).render(g, font, mouseX, mouseY, a, true);
                    cy += 16;
                    i++;
                }
            }

            section(g, font, "Aturan Antrean", cx, cy + 6, scroll.w, a);
            cy += 26;
            cy = row(g, font, "Kapasitas", cfg.requestMaxQueue + " lagu", cx, cy, scroll.w, a, CraftLiveTheme.TEXT);
            cy = row(g, font, "Urutan", "FIFO (pertama masuk, pertama keluar)", cx, cy, scroll.w, a, CraftLiveTheme.TEXT);
            cy = row(g, font, "Jeda proses", cfg.requestDelayMs + " ms", cx, cy, scroll.w, a, CraftLiveTheme.TEXT);
            cy = row(g, font, "Cooldown", cfg.requestUserCooldownSeconds + " detik per user", cx, cy, scroll.w, a, CraftLiveTheme.TEXT);
            cy = row(g, font, "Maks per user", cfg.requestMaxPerUser + " request aktif", cx, cy, scroll.w, a, CraftLiveTheme.TEXT);

            contentHeight = cy - scroll.y + 8;
            scroll.setContentHeight(contentHeight);
        });
    }

    private final java.util.Map<Long, ClButton> removeButtons = new java.util.HashMap<>();
    private final java.util.Map<Long, ClButton> approveButtons = new java.util.HashMap<>();
    private final java.util.Map<Long, ClButton> rejectButtons = new java.util.HashMap<>();

    private ClButton ensureRemoveBtn(int x, int y, SongRequest r) {
        ClButton b = removeButtons.get(r.id);
        if (b == null) {
            b = new ClButton(x, y, 28, 14, "✕", ClButton.Style.DANGER, btn -> {
                services.requests.removeById(r.id);
            });
            removeButtons.put(r.id, b);
            buttons.add(b);
        }
        b.x = x;
        b.y = y;
        return b;
    }

    private ClButton ensureApproveBtn(int x, int y, SongRequest r) {
        ClButton b = approveButtons.get(r.id);
        if (b == null) {
            b = new ClButton(x, y, 60, 14, "Setujui", ClButton.Style.SUCCESS, btn ->
                    services.requests.approve(r));
            approveButtons.put(r.id, b);
            buttons.add(b);
        }
        b.x = x;
        b.y = y;
        return b;
    }

    private ClButton ensureRejectBtn(int x, int y, SongRequest r) {
        ClButton b = rejectButtons.get(r.id);
        if (b == null) {
            b = new ClButton(x, y, 60, 14, "Tolak", ClButton.Style.DANGER, btn ->
                    services.requests.reject(r));
            rejectButtons.put(r.id, b);
            buttons.add(b);
        }
        b.x = x;
        b.y = y;
        return b;
    }
}
