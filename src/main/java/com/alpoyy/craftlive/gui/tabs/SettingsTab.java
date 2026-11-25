package com.alpoyy.craftlive.gui.tabs;

import com.alpoyy.craftlive.CraftLiveServices;
import com.alpoyy.craftlive.config.SecureStore;
import com.alpoyy.craftlive.gui.components.ClButton;
import com.alpoyy.craftlive.gui.components.ClTextField;
import com.alpoyy.craftlive.gui.components.ClToggle;
import com.alpoyy.craftlive.live.ConnectionState;
import com.alpoyy.craftlive.theme.CraftLiveTheme;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

/**
 * Settings tab: General / TikTok LIVE (EulerStream) / Live Chat /
 * Stream Mode / Performance / Reset.
 * API key is stored in SecureStore — never in config JSON, never logged.
 */
public class SettingsTab extends TabBase {
    private final CraftLiveServices services;
    private ClTextField apiKeyField;
    private ClTextField usernameField;
    private ClTextField ytDlpPathField;
    private ClTextField highlightField;
    private boolean showApiKey = false;
    private String testResult = null;

    public SettingsTab(CraftLiveServices services) {
        this.services = services;
    }

    @Override
    public String id() {
        return "settings";
    }

    @Override
    public String title() {
        return "Settings";
    }

    @Override
    protected void build() {
        buttons.add(new ClButton(0, 200, 90, 20, "Simpan Key", ClButton.Style.PRIMARY, b -> {
            if (apiKeyField != null) {
                String key = apiKeyField.text().trim();
                if (key.isEmpty()) {
                    testResult = "Key kosong.";
                } else {
                    SecureStore.saveEulerStreamKey(key);
                    apiKeyField.setText("");
                    testResult = "Key tersimpan aman (disamarkan).";
                    services.notifications.push(com.alpoyy.craftlive.notify.NotificationBus.Notification.Level.SUCCESS,
                            "EulerStream", "API Key tersimpan.");
                }
            }
        }));
        buttons.add(new ClButton(96, 200, 90, 20, "Hapus Key", ClButton.Style.DANGER, b -> {
            SecureStore.removeEulerStreamKey();
            if (apiKeyField != null) apiKeyField.setText("");
            testResult = "Key dihapus.";
        }));
        buttons.add(new ClButton(192, 200, 70, 20, showApiKey ? "Sembunyi" : "Tampilkan", ClButton.Style.SECONDARY, b -> {
            showApiKey = !showApiKey;
            if (apiKeyField != null) {
                apiKeyField.setMasked(!showApiKey);
                apiKeyField.setText(showApiKey ? SecureStore.getEulerStreamKey() : "");
            }
            build();
        }));
        buttons.add(new ClButton(268, 200, 90, 20, "Test Koneksi", ClButton.Style.SECONDARY, b -> testConnection()));

        buttons.add(new ClButton(0, 420, 110, 20, "Reset Konfigurasi", ClButton.Style.DANGER, b -> {
            services.configManager.resetToDefaults();
            services.requests.applyConfig();
            services.notifications.push(com.alpoyy.craftlive.notify.NotificationBus.Notification.Level.WARNING,
                    "Settings", "Konfigurasi dikembalikan ke default.");
        }));
    }

    private void testConnection() {
        ConnectionState current = services.live.state();
        if (current == ConnectionState.CONNECTED) {
            testResult = "Saat ini terhubung. Koneksi aktif ✓";
            return;
        }
        testResult = "Menguji… (perlu username TikTok & key tersimpan)";
        services.live.manualReconnect();
        Thread checker = new Thread(() -> {
            long deadline = System.currentTimeMillis() + 20000;
            while (System.currentTimeMillis() < deadline) {
                ConnectionState s = services.live.state();
                if (s == ConnectionState.CONNECTED) {
                    testResult = "Terhubung ✓ (EulerStream siap)";
                    return;
                }
                if (s == ConnectionState.ERROR) {
                    String err = services.live.provider().lastError();
                    testResult = "Gagal: " + (err == null ? "tidak diketahui" : err);
                    return;
                }
                try {
                    Thread.sleep(500);
                } catch (InterruptedException e) {
                    return;
                }
            }
            testResult = "Timeout menunggu koneksi (lihat Live Stats).";
        }, "CraftLive-TestConn");
        checker.setDaemon(true);
        checker.start();
    }

    @Override
    public void render(GuiGraphics g, Font font, int x, int y, int w, int h,
                       double mouseX, double mouseY, float alpha, long epochMs) {
        layout(x, y, w, h);
        scroll.render(g, alpha, () -> {
            int cx = scroll.x;
            int cy = scroll.y + (int) scroll.offset() + 4;
            float a = alpha;
            var cfg = services.config;

            // ===== General =====
            section(g, font, "General", cx, cy, scroll.w, a);
            cy += 18;
            toggles.clear();
            toggles.add(new ClToggle(cx, cy, "Mock Data Mode (simulasi)", () -> cfg.mockMode, v -> {
                cfg.mockMode = v;
                // swap provider immediately: mock ⇄ real EulerStream
                if (services.live.state().isBusy() || services.live.state() == ConnectionState.CONNECTED) {
                    services.live.disconnect();
                }
                services.live.setProvider(v
                        ? new com.alpoyy.craftlive.mock.MockLiveProvider()
                        : new com.alpoyy.craftlive.live.EulerStreamProvider());
                services.configManager.saveAsync();
                services.notifications.push(com.alpoyy.craftlive.notify.NotificationBus.Notification.Level.WARNING,
                        "Mock Mode", v ? "AKTIF — semua data simulasi" : "Nonaktif — koneksi nyata digunakan");
            }));
            for (ClToggle t : toggles) t.render(g, font, mouseX, mouseY, a);
            cy += 24;
            toggles.clear();
            toggles.add(new ClToggle(cx, cy, "Notifikasi", () -> cfg.notificationsEnabled, v -> cfg.notificationsEnabled = v));
            toggles.add(new ClToggle(cx + 200, cy, "Animasi", () -> cfg.animationsEnabled, v -> cfg.animationsEnabled = v));
            for (ClToggle t : toggles) t.render(g, font, mouseX, mouseY, a);
            cy += 24;
            toggles.clear();
            toggles.add(new ClToggle(cx, cy, "Reduced motion", () -> cfg.reducedMotion, v -> cfg.reducedMotion = v));
            toggles.add(new ClToggle(cx + 200, cy, "Efek glass GUI", () -> cfg.guiGlassEffect, v -> cfg.guiGlassEffect = v));
            for (ClToggle t : toggles) t.render(g, font, mouseX, mouseY, a);
            cy += 28;

            // ===== TikTok LIVE / EulerStream =====
            section(g, font, "TikTok LIVE — EulerStream", cx, cy, scroll.w, a);
            cy += 18;
            g.drawString(font, "Provider: EulerStream (wss://ws.eulerstream.com) — API Key milikmu.",
                    cx, cy, CraftLiveTheme.withAlpha(CraftLiveTheme.TEXT_DIM, a * 0.85f), true);
            cy += 14;

            g.drawString(font, "EulerStream API Key " + (SecureStore.hasEulerStreamKey() ? "(tersimpan ✓)" : "(belum ada)"),
                    cx, cy, CraftLiveTheme.withAlpha(CraftLiveTheme.TEXT_DIM, a), true);
            cy += 13;
            apiKeyField = ensureApiKeyField(cx, cy, scroll.w - 20);
            apiKeyField.setMasked(!showApiKey);
            apiKeyField.render(g, font, mouseX, mouseY, a, epochMs);
            cy += 26;
            for (int i = 0; i < 4; i++) {
                ClButton b = buttons.get(i);
                b.x = cx + i * 96;
                b.y = cy;
                b.render(g, font, mouseX, mouseY, a, true);
            }
            cy += 28;
            g.drawString(font, "Key disimpan di Secret Storage lokal (bukan file config/git),",
                    cx, cy, CraftLiveTheme.withAlpha(CraftLiveTheme.TEXT_DIM, a * 0.75f), true);
            cy += 11;
            g.drawString(font, "tidak pernah di-log / dikirim ke server lain.", cx, cy,
                    CraftLiveTheme.withAlpha(CraftLiveTheme.TEXT_DIM, a * 0.75f), true);
            cy += 18;

            g.drawString(font, "Username TikTok:", cx, cy, CraftLiveTheme.withAlpha(CraftLiveTheme.TEXT_DIM, a), true);
            cy += 13;
            usernameField = ensureUsernameField(cx, cy, scroll.w - 20);
            usernameField.render(g, font, mouseX, mouseY, a, epochMs);
            cy += 26;

            toggles.clear();
            toggles.add(new ClToggle(cx, cy, "Auto-reconnect", () -> cfg.autoReconnect, v -> cfg.autoReconnect = v));
            for (ClToggle t : toggles) t.render(g, font, mouseX, mouseY, a);
            cy += 26;

            if (testResult != null) {
                g.drawString(font, testResult, cx, cy, CraftLiveTheme.withAlpha(CraftLiveTheme.WARNING, a), true);
                cy += 16;
            }

            // ===== Live Chat =====
            section(g, font, "Live Chat", cx, cy, scroll.w, a);
            cy += 18;
            g.drawString(font, "Highlight kata (pisahkan dengan koma):", cx, cy,
                    CraftLiveTheme.withAlpha(CraftLiveTheme.TEXT_DIM, a), true);
            cy += 13;
            highlightField = ensureHighlightField(cx, cy, scroll.w - 20);
            highlightField.render(g, font, mouseX, mouseY, a, epochMs);
            cy += 26;

            // ===== Music platforms =====
            section(g, font, "Music Platforms", cx, cy, scroll.w, a);
            cy += 18;
            g.drawString(font, "Path yt-dlp (binari terinstal di sistem):", cx, cy,
                    CraftLiveTheme.withAlpha(CraftLiveTheme.TEXT_DIM, a), true);
            cy += 13;
            ytDlpPathField = ensureYtDlpField(cx, cy, scroll.w - 20);
            ytDlpPathField.render(g, font, mouseX, mouseY, a, epochMs);
            cy += 26;

            // ===== Stream Mode =====
            section(g, font, "Stream Mode (tombol L)", cx, cy, scroll.w, a);
            cy += 18;
            toggles.clear();
            toggles.add(new ClToggle(cx, cy, "Sembunyikan chat", () -> cfg.streamModeHideChat, v -> cfg.streamModeHideChat = v));
            toggles.add(new ClToggle(cx + 200, cy, "Sembunyikan statistik", () -> cfg.streamModeHideStats, v -> cfg.streamModeHideStats = v));
            for (ClToggle t : toggles) t.render(g, font, mouseX, mouseY, a);
            cy += 24;
            toggles.clear();
            toggles.add(new ClToggle(cx, cy, "Sembunyikan notifikasi", () -> cfg.streamModeHideNotifications, v -> cfg.streamModeHideNotifications = v));
            toggles.add(new ClToggle(cx + 200, cy, "Sembunyikan info koneksi", () -> cfg.streamModeHideConnectionInfo, v -> cfg.streamModeHideConnectionInfo = v));
            for (ClToggle t : toggles) t.render(g, font, mouseX, mouseY, a);
            cy += 24;
            toggles.clear();
            toggles.add(new ClToggle(cx, cy, "Sederhanakan HUD", () -> cfg.streamModeSimplifyHud, v -> cfg.streamModeSimplifyHud = v));
            for (ClToggle t : toggles) t.render(g, font, mouseX, mouseY, a);
            cy += 28;

            // ===== Performance =====
            section(g, font, "Performa", cx, cy, scroll.w, a);
            cy += 18;
            cy = row(g, font, "Retensi chat", cfg.chatMaxMessages + " pesan", cx, cy, scroll.w, a, CraftLiveTheme.TEXT);
            cy = row(g, font, "Retensi notifikasi", cfg.maxNotifications + " toast, " + cfg.notificationDisplaySeconds + "s", cx, cy, scroll.w, a, CraftLiveTheme.TEXT);
            toggles.clear();
            toggles.add(new ClToggle(cx, cy, "Culling widget tersembunyi", () -> cfg.performanceCullHiddenWidgets, v -> cfg.performanceCullHiddenWidgets = v));
            for (ClToggle t : toggles) t.render(g, font, mouseX, mouseY, a);
            cy += 28;

            // ===== Reset =====
            section(g, font, "Konfigurasi", cx, cy, scroll.w, a);
            cy += 18;
            ClButton reset = buttons.get(4);
            reset.x = cx;
            reset.y = cy;
            reset.render(g, font, mouseX, mouseY, a, true);
            cy += 28;

            contentHeight = cy - scroll.y + 8;
            scroll.setContentHeight(contentHeight);
        });
    }

    // ---- lazy fields ----

    private ClTextField ensureApiKeyField(int x, int y, int w) {
        if (apiKeyField == null) {
            apiKeyField = new ClTextField(x, y, w, 18, "EulerStream API Key (disamarkan)", s -> {});
            apiKeyField.setMasked(true);
            apiKeyField.setMaxLength(200);
            fields.add(apiKeyField);
        }
        apiKeyField.x = x;
        apiKeyField.y = y;
        apiKeyField.w = w;
        return apiKeyField;
    }

    private ClTextField ensureUsernameField(int x, int y, int w) {
        if (usernameField == null) {
            usernameField = new ClTextField(x, y, w, 18, "username tiktok (tanpa @)", s -> {
                services.live.configure(s);
                services.config.tiktokUsername = s;
                services.configManager.saveAsync();
            });
            usernameField.setText(services.config.tiktokUsername);
            fields.add(usernameField);
        }
        usernameField.x = x;
        usernameField.y = y;
        usernameField.w = w;
        return usernameField;
    }

    private ClTextField ensureYtDlpField(int x, int y, int w) {
        if (ytDlpPathField == null) {
            ytDlpPathField = new ClTextField(x, y, w, 18, "yt-dlp (default: yt-dlp di PATH)", s -> {
                services.youtube.setBinaryPath(s.trim());
                services.config.ytdlpPath = s.trim();
                services.configManager.saveAsync();
            });
            ytDlpPathField.setText(services.youtube.client().binaryPath());
            fields.add(ytDlpPathField);
        }
        ytDlpPathField.x = x;
        ytDlpPathField.y = y;
        ytDlpPathField.w = w;
        return ytDlpPathField;
    }

    private ClTextField ensureHighlightField(int x, int y, int w) {
        if (highlightField == null) {
            highlightField = new ClTextField(x, y, w, 18, "kata1, kata2", s -> {
                services.config.chatHighlightWords = s;
                services.chat.configure(services.config.chatMaxMessages, services.config.chatShowGifts,
                        services.config.chatShowFollows, services.config.chatShowJoins,
                        services.config.chatSpamProtection, services.config.chatSpamThreshold, s);
                services.configManager.saveAsync();
            });
            highlightField.setText(services.config.chatHighlightWords);
            fields.add(highlightField);
        }
        highlightField.x = x;
        highlightField.y = y;
        highlightField.w = w;
        return highlightField;
    }
}
