package com.alpoyy.craftlive.gui.tabs;

import com.alpoyy.craftlive.CraftLiveServices;
import com.alpoyy.craftlive.config.SecureStore;
import com.alpoyy.craftlive.gui.components.ClButton;
import com.alpoyy.craftlive.gui.components.ClSlider;
import com.alpoyy.craftlive.gui.components.ClTextField;
import com.alpoyy.craftlive.gui.components.ClToggle;
import com.alpoyy.craftlive.live.ConnectionState;
import com.alpoyy.craftlive.render.ClRender;
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
    private final com.alpoyy.craftlive.gui.CraftLiveScreen ownerScreen;
    private ClTextField apiKeyField;
    private ClTextField usernameField;
    private ClTextField ytDlpPathField;
    private ClTextField highlightField;
    private boolean showApiKey = false;
    private String testResult = null;

    public SettingsTab(CraftLiveServices services) {
        this(services, null);
    }

    public SettingsTab(CraftLiveServices services, com.alpoyy.craftlive.gui.CraftLiveScreen ownerScreen) {
        this.services = services;
        this.ownerScreen = ownerScreen;
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
            com.alpoyy.craftlive.theme.CraftLiveTheme.resetAccent();
            com.alpoyy.craftlive.sound.UISounds.confirm();
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
            int cy = scroll.y + 4; // panel applies the -offset translate (was double-applied before)
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

            // ===== Appearance: global accent color =====
            section(g, font, "Appearance — Accent Color", cx, cy, scroll.w, a);
            cy += 18;
            g.drawString(font, "Satu warna untuk semua garis & elemen accent (default merah CraftLive).", cx, cy,
                    CraftLiveTheme.withAlpha(CraftLiveTheme.TEXT_DIM, a * 0.85f), true);
            cy += 16;

            // live preview swatch + current hex
            ClRender.roundedRectBordered(g, cx, cy, 16, 16, 4,
                    CraftLiveTheme.withAlpha(CraftLiveTheme.accent(), 1f),
                    CraftLiveTheme.withAlpha(CraftLiveTheme.BORDER, a));
            g.drawString(font, "Saat ini: " + cfg.accentColorHex.toUpperCase(), cx + 24, cy + 4,
                    CraftLiveTheme.withAlpha(CraftLiveTheme.TEXT, a), true);
            cy += 24;

            // preset swatches — click to apply instantly
            String[] presets = {"#C43A3A", "#4287FF", "#9B59F6", "#43C879", "#E08E3A", "#3AC8C8", "#E05B9B", "#A7A7A7"};
            g.drawString(font, "Preset:", cx, cy + 3, CraftLiveTheme.withAlpha(CraftLiveTheme.TEXT_DIM, a), true);
            int swx = cx + 60;
            for (String hex : presets) {
                int rgb = com.alpoyy.craftlive.theme.AccentColor.sanitize(hex);
                ClRender.roundedRect(g, swx, cy - 2, 18, 16, 4, CraftLiveTheme.withAlpha(rgb, a));
                if (hex.equalsIgnoreCase(cfg.accentColorHex)) {
                    g.renderOutline(swx - 1, cy - 3, 20, 18, CraftLiveTheme.withAlpha(CraftLiveTheme.TEXT, a));
                }
                final String chosen = hex;
                swatchButtons.put(chosen, new ClButton(swx, cy - 2, 18, 16, "", ClButton.Style.GHOST, b -> applyAccent(chosen)));
                buttons.add(swatchButtons.get(chosen));
                swx += 26;
            }
            cy += 24;

            // HEX input
            g.drawString(font, "HEX (#RRGGBB):", cx, cy, CraftLiveTheme.withAlpha(CraftLiveTheme.TEXT_DIM, a), true);
            cy += 13;
            hexField = ensureHexField(cx, cy, scroll.w - 100);
            hexField.render(g, font, mouseX, mouseY, a, epochMs);
            ensureApplyHexButton(cx + scroll.w - 92, cy).render(g, font, mouseX, mouseY, a, true);
            cy += 26;

            ensureResetAccentButton(cx, cy).render(g, font, mouseX, mouseY, a, true);
            cy += 24;
            g.drawString(font, "Background, panel, border, teks, status LIVE/WARNING/ERROR tidak berubah.", cx, cy,
                    CraftLiveTheme.withAlpha(CraftLiveTheme.TEXT_DIM, a * 0.7f), true);
            cy += 24;

            // ===== UI & Interaction: sound effects =====
            section(g, font, "UI & Interaction — Sound Effects", cx, cy, scroll.w, a);
            cy += 18;
            toggles.clear();
            toggles.add(new ClToggle(cx, cy, "Aktifkan suara UI", () -> cfg.uiSoundsEnabled, v -> {
                cfg.uiSoundsEnabled = v;
                services.configManager.saveAsync();
            }));
            for (ClToggle t : toggles) t.render(g, font, mouseX, mouseY, a);
            cy += 24;
            sliders.clear();
            sliders.add(new ClSlider(cx, cy, 200, "Volume suara UI", () -> (double) cfg.uiSoundVolume,
                    v -> {
                        cfg.uiSoundVolume = v;
                        services.configManager.saveAsync();
                    }));
            sliders.get(0).format = "%.0f%%";
            sliders.get(0).render(g, font, mouseX, mouseY, a);
            cy += 36;
            toggles.clear();
            toggles.add(new ClToggle(cx, cy, "Suara tombol", () -> cfg.uiSoundClick, v -> cfg.uiSoundClick = v));
            toggles.add(new ClToggle(cx + 200, cy, "Suara toggle", () -> cfg.uiSoundToggle, v -> cfg.uiSoundToggle = v));
            for (ClToggle t : toggles) t.render(g, font, mouseX, mouseY, a);
            cy += 24;
            toggles.clear();
            toggles.add(new ClToggle(cx, cy, "Suara pindah tab", () -> cfg.uiSoundTab, v -> cfg.uiSoundTab = v));
            toggles.add(new ClToggle(cx + 200, cy, "Suara buka menu", () -> cfg.uiSoundMenuOpen, v -> cfg.uiSoundMenuOpen = v));
            for (ClToggle t : toggles) t.render(g, font, mouseX, mouseY, a);
            cy += 24;
            toggles.clear();
            toggles.add(new ClToggle(cx, cy, "Suara tutup menu", () -> cfg.uiSoundMenuClose, v -> cfg.uiSoundMenuClose = v));
            toggles.add(new ClToggle(cx + 200, cy, "Suara widget/HUD editor", () -> cfg.uiSoundWidget, v -> cfg.uiSoundWidget = v));
            for (ClToggle t : toggles) t.render(g, font, mouseX, mouseY, a);
            cy += 24;
            toggles.clear();
            toggles.add(new ClToggle(cx, cy, "Suara hover (opsional)", () -> cfg.uiSoundHover, v -> cfg.uiSoundHover = v));
            for (ClToggle t : toggles) t.render(g, font, mouseX, mouseY, a);
            cy += 24;
            ClButton previewBtn = new ClButton(cx, cy, 110, 18, "🔊 Preview Suara", ClButton.Style.SECONDARY,
                    b -> com.alpoyy.craftlive.sound.UISounds.confirm());
            previewBtn.render(g, font, mouseX, mouseY, a, true);
            ClButton resetSounds = new ClButton(cx + 120, cy, 150, 18, "Reset Sound Settings", ClButton.Style.SECONDARY,
                    b -> {
                        cfg.uiSoundsEnabled = true;
                        cfg.uiSoundVolume = 0.8f;
                        cfg.uiSoundClick = true;
                        cfg.uiSoundToggle = true;
                        cfg.uiSoundTab = true;
                        cfg.uiSoundMenuOpen = true;
                        cfg.uiSoundMenuClose = true;
                        cfg.uiSoundWidget = true;
                        cfg.uiSoundHover = false;
                        com.alpoyy.craftlive.sound.UISounds.resetThrottle();
                        services.configManager.saveAsync();
                        com.alpoyy.craftlive.sound.UISounds.confirm();
                        onShow();
                    });
            resetSounds.render(g, font, mouseX, mouseY, a, true);
            cy += 28;
            g.drawString(font, "Suara UI lewat kanal UI Minecraft — tidak mengubah volume global/music.", cx, cy,
                    CraftLiveTheme.withAlpha(CraftLiveTheme.TEXT_DIM, a * 0.7f), true);
            cy += 26;

            // ===== Stream Mode master toggle =====
            section(g, font, "Stream Mode (tombol L)", cx, cy, scroll.w, a);
            cy += 18;
            toggles.clear();
            toggles.add(new ClToggle(cx, cy, "Stream Mode aktif", services.streamMode::isActive, v -> {
                services.streamMode.setActive(v);
                services.configManager.saveAsync();
                com.alpoyy.craftlive.sound.UISounds.confirm();
            }));
            for (ClToggle t : toggles) t.render(g, font, mouseX, mouseY, a);
            cy += 26;
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

            // ===== Performance =====
            section(g, font, "Performa", cx, cy, scroll.w, a);
            cy += 18;
            cy = row(g, font, "Retensi chat", cfg.chatMaxMessages + " pesan", cx, cy, scroll.w, a, CraftLiveTheme.TEXT);
            cy = row(g, font, "Retensi notifikasi", cfg.maxNotifications + " toast, " + cfg.notificationDisplaySeconds + "s", cx, cy, scroll.w, a, CraftLiveTheme.TEXT);
            toggles.clear();
            toggles.add(new ClToggle(cx, cy, "Culling widget tersembunyi", () -> cfg.performanceCullHiddenWidgets, v -> cfg.performanceCullHiddenWidgets = v));
            for (ClToggle t : toggles) t.render(g, font, mouseX, mouseY, a);
            cy += 28;

            // ===== Keybinds =====
            section(g, font, "Keybinds (Options → Controls → CraftLive)", cx, cy, scroll.w, a);
            cy += 18;
            var binds = com.alpoyy.craftlive.keybind.ModKeybinds.all();
            for (var entry : binds) {
                String name = entry.name();
                String key = entry.mapping().getTranslatedKeyMessage().getString();
                g.drawString(font, name, cx, cy, CraftLiveTheme.withAlpha(CraftLiveTheme.TEXT_DIM, a), true);
                g.drawString(font, key, cx + 160, cy, CraftLiveTheme.withAlpha(CraftLiveTheme.accentHover(), a), true);
                cy += 13;
            }
            ClButton openControls = new ClButton(cx, cy, 190, 18, "Buka Controls Minecraft", ClButton.Style.SECONDARY,
                    b -> {
                        var mc = net.minecraft.client.Minecraft.getInstance();
                        if (mc != null && mc.options != null) {
                            mc.setScreen(new net.minecraft.client.gui.screens.options.controls.KeyBindsScreen(
                                    ownerScreen, mc.options));
                        }
                    });
            openControls.render(g, font, mouseX, mouseY, a, true);
            cy += 26;
            g.drawString(font, "Semua keybind dapat diubah & di-reset di sana.", cx, cy,
                    CraftLiveTheme.withAlpha(CraftLiveTheme.TEXT_DIM, a * 0.8f), true);
            cy += 22;

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

    private final java.util.Map<String, ClButton> swatchButtons = new java.util.LinkedHashMap<>();
    private ClTextField hexField;
    private ClButton applyHexButton;
    private ClButton resetAccentButton;

    /** Apply a hex accent color instantly (validated), persist, notify. */
    private void applyAccent(String hex) {
        int rgb = com.alpoyy.craftlive.theme.AccentColor.parseHex(hex);
        if (rgb < 0) {
            com.alpoyy.craftlive.sound.UISounds.click();
            services.notifications.push(com.alpoyy.craftlive.notify.NotificationBus.Notification.Level.ERROR,
                    "Accent Color", "HEX tidak valid: " + hex);
            return;
        }
        CraftLiveTheme.setAccent(rgb);
        services.config.accentColorHex = com.alpoyy.craftlive.theme.AccentColor.toHex(rgb);
        services.configManager.saveAsync();
        com.alpoyy.craftlive.sound.UISounds.confirm();
        if (hexField != null) hexField.setText(com.alpoyy.craftlive.theme.AccentColor.toHex(rgb));
    }

    private ClTextField ensureHexField(int x, int y, int w) {
        if (hexField == null) {
            hexField = new ClTextField(x, y, w, 18, "#4287FF", s -> {
                // live preview on valid input; invalid input never applies or crashes
                int rgb = com.alpoyy.craftlive.theme.AccentColor.parseHex(s);
                if (rgb >= 0) {
                    CraftLiveTheme.setAccent(rgb);
                }
            });
            hexField.setMaxLength(8);
            hexField.setText(services.config.accentColorHex);
            fields.add(hexField);
        }
        hexField.x = x;
        hexField.y = y;
        hexField.w = w;
        return hexField;
    }

    private ClButton ensureApplyHexButton(int x, int y) {
        if (applyHexButton == null) {
            applyHexButton = new ClButton(x, y, 90, 18, "Terapkan", ClButton.Style.PRIMARY, b -> {
                applyAccent(hexField == null ? "" : hexField.text());
                onShow();
            });
            buttons.add(applyHexButton);
        }
        applyHexButton.x = x;
        applyHexButton.y = y;
        return applyHexButton;
    }

    private ClButton ensureResetAccentButton(int x, int y) {
        if (resetAccentButton == null) {
            resetAccentButton = new ClButton(x, y, 150, 18, "Reset ke Default (Merah)", ClButton.Style.SECONDARY,
                    b -> {
                        CraftLiveTheme.resetAccent();
                        services.config.accentColorHex = com.alpoyy.craftlive.theme.AccentColor.DEFAULT_HEX;
                        services.configManager.saveAsync();
                        if (hexField != null) hexField.setText(com.alpoyy.craftlive.theme.AccentColor.DEFAULT_HEX);
                        com.alpoyy.craftlive.sound.UISounds.confirm();
                        onShow();
                    });
            buttons.add(resetAccentButton);
        }
        resetAccentButton.x = x;
        resetAccentButton.y = y;
        return resetAccentButton;
    }

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
