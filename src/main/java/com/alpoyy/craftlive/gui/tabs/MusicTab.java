package com.alpoyy.craftlive.gui.tabs;

import com.alpoyy.craftlive.CraftLiveServices;
import com.alpoyy.craftlive.gui.components.ClButton;
import com.alpoyy.craftlive.gui.components.ClSlider;
import com.alpoyy.craftlive.gui.components.ClTextField;
import com.alpoyy.craftlive.music.MusicPlatform;
import com.alpoyy.craftlive.music.PlayerState;
import com.alpoyy.craftlive.music.Track;
import com.alpoyy.craftlive.render.ClRender;
import com.alpoyy.craftlive.theme.CraftLiveTheme;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

import java.util.List;

/**
 * Music Player tab: platform selection (YouTube Music via yt-dlp with real
 * playback; Spotify via official Web API), search, controls, handoff.
 */
public class MusicTab extends TabBase {
    private final CraftLiveServices services;
    private List<Track> searchResults = List.of();
    private String searchMessage = null;

    public MusicTab(CraftLiveServices services) {
        this.services = services;
    }

    @Override
    public String id() {
        return "music";
    }

    @Override
    public String title() {
        return "Music Player";
    }

    @Override
    protected void build() {
        MusicPlatform platform = services.preferredPlatform();
        PlayerState state = platform == null ? null : platform.state();
        boolean controls = platform != null && state != null && state.controlsSupported
                && state.status != PlayerState.Status.DISCONNECTED;

        int bx = 0;
        buttons.add(new ClButton(bx, 0, 44, 22, "⏮", ClButton.Style.SECONDARY, b -> {
            if (platform != null) platform.previous();
        }).with(b -> b.enabled = controls));
        buttons.add(new ClButton(bx + 50, 0, 60, 22,
                state != null && state.status == PlayerState.Status.PLAYING ? "⏸" : "▶",
                ClButton.Style.PRIMARY, b -> {
            if (platform != null) platform.playPause();
        }).with(b -> b.enabled = controls));
        buttons.add(new ClButton(bx + 116, 0, 44, 22, "⏭", ClButton.Style.SECONDARY, b -> {
            if (platform != null) platform.next();
        }).with(b -> b.enabled = controls));

        // platform connect buttons
        buttons.add(new ClButton(0, 200, 170, 22, "Hubungkan YouTube Music", ClButton.Style.PRIMARY,
                b -> services.youtube.connect()));
        buttons.add(new ClButton(0, 228, 170, 22, "Putuskan YouTube Music", ClButton.Style.DANGER,
                b -> services.youtube.disconnect()));
        buttons.add(new ClButton(190, 200, 150, 22, "Hubungkan Spotify", ClButton.Style.PRIMARY,
                b -> services.spotify.connect()));
        buttons.add(new ClButton(190, 228, 150, 22, "Putuskan Spotify", ClButton.Style.DANGER,
                b -> services.spotify.disconnect()));
    }

    @Override
    public void render(GuiGraphics g, Font font, int x, int y, int w, int h,
                       double mouseX, double mouseY, float alpha, long epochMs) {
        layout(x, y, w, h);
        scroll.render(g, alpha, () -> {
            int cx = scroll.x;
            int cy = scroll.y + (int) scroll.offset() + 4;
            float a = alpha;
            var youtube = services.youtube;

            // ===== now playing =====
            MusicPlatform platform = services.preferredPlatform();
            PlayerState state = platform == null ? null : platform.state();
            String platformName = platform == null ? "Tidak ada platform" : platform.displayName();
            section(g, font, "Sedang Diputar — " + platformName, cx, cy, scroll.w, a);
            cy += 18;

            Track track = state == null ? null : state.current;
            if (track != null) {
                String title = track.primaryText();
                com.alpoyy.craftlive.util.TextClip.Marquee m = marqueeFor(title, font.width(title), scroll.w - 20);
                m.advance(16);
                g.enableScissor(cx, cy - 2, cx + scroll.w - 10, cy + 12);
                g.drawString(font, title, cx + (int) m.offset(), cy, CraftLiveTheme.withAlpha(CraftLiveTheme.TEXT, a), true);
                g.disableScissor();
                cy += 14;
                g.drawString(font, ClRender.clip(font, track.secondaryText(), scroll.w - 20), cx, cy,
                        CraftLiveTheme.withAlpha(CraftLiveTheme.TEXT_DIM, a), true);
                cy += 14;
                long progress = state.displayedProgressMs(epochMs);
                if (progress >= 0 && track.durationMs > 0) {
                    ClRender.progressBar(g, cx, cy, scroll.w - 60, 6, (float) progress / track.durationMs,
                            CraftLiveTheme.withAlpha(CraftLiveTheme.PANEL_2, a),
                            CraftLiveTheme.withAlpha(CraftLiveTheme.accent(), a));
                    String time = fmt(progress) + " / " + fmt(track.durationMs);
                    g.drawString(font, time, cx + scroll.w - 56, cy - 2, CraftLiveTheme.withAlpha(CraftLiveTheme.TEXT_DIM, a), true);
                } else {
                    ClRender.progressBar(g, cx, cy, scroll.w - 60, 6, 0,
                            CraftLiveTheme.withAlpha(CraftLiveTheme.PANEL_2, a),
                            CraftLiveTheme.withAlpha(CraftLiveTheme.accent(), a * 0.4f));
                }
                cy += 14;
            } else {
                g.drawString(font, "Tidak ada musik diputar", cx, cy,
                        CraftLiveTheme.withAlpha(CraftLiveTheme.TEXT_DIM, a), true);
                cy += 16;
            }

            // controls
            for (int i = 0; i < 3; i++) {
                ClButton b = buttons.get(i);
                b.x = cx + i * 60;
                b.y = cy;
                b.render(g, font, mouseX, mouseY, a, true);
            }
            cy += 30;

            // volume
            if (platform != null && state.volumeSupported) {
                sliders.clear();
                ClSlider vol = new ClSlider(cx, cy, 200, "Volume", () -> (double) state.volume, v -> platform.setVolume(v));
                vol.format = "%.0f%%";
                sliders.add(vol);
                vol.render(g, font, mouseX, mouseY, a);
                cy += 34;
            }

            // ===== YouTube Music =====
            section(g, font, "YouTube Music (yt-dlp)", cx, cy + 4, scroll.w, a);
            cy += 22;
            String ytStatus = ytStatusText(youtube);
            g.drawString(font, "Status: " + ytStatus, cx, cy,
                    CraftLiveTheme.withAlpha(youtube.isConfigured() ? CraftLiveTheme.LIVE_GREEN : CraftLiveTheme.WARNING, a), true);
            cy += 14;
            if (youtube.state().errorMessage != null) {
                for (String line : ClRender.wrap(font, youtube.state().errorMessage, scroll.w - 8)) {
                    g.drawString(font, line, cx, cy, CraftLiveTheme.withAlpha(CraftLiveTheme.ERROR, a), true);
                    cy += 11;
                }
            }
            for (ClButton b : buttons.subList(3, 5)) {
                b.render(g, font, mouseX, mouseY, a, true);
            }
            cy += 30;

            // search
            ClTextField searchField = ensureSearchField(cx, cy, scroll.w - 90);
            searchField.render(g, font, mouseX, mouseY, a, epochMs);
            ClButton searchBtn = ensureSearchButton(cx + scroll.w - 84, cy);
            searchBtn.render(g, font, mouseX, mouseY, a, true);
            cy += 30;

            if (searchMessage != null) {
                g.drawString(font, searchMessage, cx, cy, CraftLiveTheme.withAlpha(CraftLiveTheme.TEXT_DIM, a), true);
                cy += 14;
            }
            int idx = 0;
            for (Track t : searchResults) {
                String line = (idx + 1) + ". " + t.primaryText() + " — " + t.secondaryText();
                ClButton playB = ensurePlayButton(cx + scroll.w - 60, cy, t);
                String clipped = ClRender.clip(font, line, scroll.w - 70);
                g.drawString(font, clipped, cx, cy, CraftLiveTheme.withAlpha(CraftLiveTheme.TEXT, a), true);
                playB.render(g, font, mouseX, mouseY, a, true);
                cy += 15;
                idx++;
            }
            cy += 8;

            // ===== Spotify =====
            section(g, font, "Spotify (Web API resmi)", cx, cy + 2, scroll.w, a);
            cy += 20;
            var spotify = services.spotify;
            String spStatus = spotify.state().statusDisplay();
            g.drawString(font, "Status: " + spStatus + (spotify.isConfigured() ? "  ✓" : ""), cx, cy,
                    CraftLiveTheme.withAlpha(spotify.isConfigured() ? CraftLiveTheme.LIVE_GREEN : CraftLiveTheme.TEXT_DIM, a), true);
            cy += 14;
            g.drawString(font, "Client ID (dari dashboard Spotify developer):", cx, cy,
                    CraftLiveTheme.withAlpha(CraftLiveTheme.TEXT_DIM, a), true);
            cy += 13;
            ClTextField clientIdField = ensureClientIdField(cx, cy, scroll.w - 90);
            clientIdField.render(g, font, mouseX, mouseY, a, epochMs);
            ClButton saveBtn = ensureSaveClientIdButton(cx + scroll.w - 84, cy);
            saveBtn.render(g, font, mouseX, mouseY, a, true);
            cy += 30;
            g.drawString(font, "OAuth PKCE: browser terbuka, tidak ada password/cookies.", cx, cy,
                    CraftLiveTheme.withAlpha(CraftLiveTheme.TEXT_DIM, a * 0.8f), true);
            cy += 13;
            g.drawString(font, "Kontrol putar butuh akun Spotify Premium aktif.", cx, cy,
                    CraftLiveTheme.withAlpha(CraftLiveTheme.TEXT_DIM, a * 0.8f), true);
            cy += 18;
            for (ClButton b : buttons.subList(5, 7)) {
                b.render(g, font, mouseX, mouseY, a, true);
            }
            cy += 30;

            contentHeight = cy - scroll.y + 8;
            scroll.setContentHeight(contentHeight);
        });
    }

    private String ytStatusText(com.alpoyy.craftlive.music.youtube.YoutubeMusicConnector yt) {
        var st = yt.state().status;
        return switch (st) {
            case CONNECTED -> "Siap" + (yt.client().checkAvailable() != null ? " (yt-dlp " + yt.client().checkAvailable() + ")" : "");
            case PLAYING -> "Memutar";
            case PAUSED -> "Jeda";
            case ERROR -> "Error";
            case CONNECTING -> "Menyiapkan…";
            default -> "Tidak terhubung";
        };
    }

    private String fmt(long ms) {
        long s = ms / 1000;
        return String.format("%d:%02d", s / 60, s % 60);
    }

    // ---- lazy controls ----

    private ClTextField searchField;
    private ClButton searchButton;
    private ClTextField clientIdField;
    private ClButton saveClientIdButton;
    private final java.util.Map<Track, ClButton> playButtons = new java.util.LinkedHashMap<>();

    private ClTextField ensureSearchField(int x, int y, int w) {
        if (searchField == null) {
            searchField = new ClTextField(x, y, w, 18, "Cari lagu (yt-dlp search)…", s -> {});
            fields.add(searchField);
        }
        searchField.x = x;
        searchField.y = y;
        searchField.w = w;
        return searchField;
    }

    private ClButton ensureSearchButton(int x, int y) {
        if (searchButton == null) {
            searchButton = new ClButton(x, y, 84, 18, "Cari", ClButton.Style.PRIMARY, b -> doSearch());
            buttons.add(searchButton);
        }
        searchButton.x = x;
        searchButton.y = y;
        return searchButton;
    }

    private void doSearch() {
        String q = searchField == null ? "" : searchField.text().trim();
        if (q.isEmpty()) return;
        searchMessage = "Mencari…";
        searchResults = List.of();
        services.youtube.client().search(q, 10).thenAccept(result -> {
            if (result.error != null) {
                searchMessage = result.error;
            } else {
                searchMessage = result.tracks.isEmpty() ? "Tidak ada hasil." : result.tracks.size() + " hasil:";
                searchResults = result.tracks;
            }
        });
    }

    private ClTextField ensureClientIdField(int x, int y, int w) {
        if (clientIdField == null) {
            clientIdField = new ClTextField(x, y, w, 18, "Client ID", s -> {});
            fields.add(clientIdField);
            clientIdField.setText(services.config.spotifyClientId);
        }
        clientIdField.x = x;
        clientIdField.y = y;
        clientIdField.w = w;
        return clientIdField;
    }

    private ClButton ensureSaveClientIdButton(int x, int y) {
        if (saveClientIdButton == null) {
            saveClientIdButton = new ClButton(x, y, 84, 18, "Simpan", ClButton.Style.SECONDARY, b -> {
                services.spotify.setClientId(clientIdField.text().trim());
                services.config.spotifyClientId = clientIdField.text().trim();
                services.configManager.saveAsync();
                services.notifications.push(com.alpoyy.craftlive.notify.NotificationBus.Notification.Level.SUCCESS,
                        "Spotify", "Client ID tersimpan.");
            });
            buttons.add(saveClientIdButton);
        }
        saveClientIdButton.x = x;
        saveClientIdButton.y = y;
        return saveClientIdButton;
    }

    private ClButton ensurePlayButton(int x, int y, Track track) {
        ClButton b = playButtons.get(track);
        if (b == null) {
            b = new ClButton(x, y, 58, 14, "▶ Putar", ClButton.Style.SUCCESS, btn ->
                    services.youtube.play(track, err ->
                            services.notifications.push(com.alpoyy.craftlive.notify.NotificationBus.Notification.Level.ERROR,
                                    "YouTube Music", err)));
            playButtons.put(track, b);
            buttons.add(b);
        }
        b.x = x;
        b.y = y;
        return b;
    }

    private com.alpoyy.craftlive.util.TextClip.Marquee cachedM;
    private String cachedText;
    private int cachedW = -1;

    private com.alpoyy.craftlive.util.TextClip.Marquee marqueeFor(String t, int tw, int vw) {
        if (!t.equals(cachedText) || cachedW != vw) {
            cachedText = t;
            cachedW = vw;
            cachedM = new com.alpoyy.craftlive.util.TextClip.Marquee(tw, vw, 24f, 900, 900);
        }
        return cachedM;
    }
}
