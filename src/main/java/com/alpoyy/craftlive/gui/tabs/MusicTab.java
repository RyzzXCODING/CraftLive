package com.alpoyy.craftlive.gui.tabs;

import com.alpoyy.craftlive.CraftLiveServices;
import com.alpoyy.craftlive.gui.components.ClButton;
import com.alpoyy.craftlive.gui.components.ClSlider;
import com.alpoyy.craftlive.gui.components.ClTextField;
import com.alpoyy.craftlive.music.MusicPlatform;
import com.alpoyy.craftlive.music.PlayerState;
import com.alpoyy.craftlive.music.Track;
import com.alpoyy.craftlive.music.YtDlpClient;
import com.alpoyy.craftlive.render.ClRender;
import com.alpoyy.craftlive.theme.CraftLiveTheme;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

import java.util.List;

/**
 * Music Player tab.
 *
 * Layout contract: EVERY control gets its coordinates assigned during the
 * render pass (in content space) and is rendered immediately — so rendered
 * position and hitbox always agree (fixes "controls cannot be clicked").
 *
 * yt-dlp availability is read from the async cached state machine — the
 * render path never spawns a process (fixes the 5-FPS bug).
 */
public class MusicTab extends TabBase {
    private final CraftLiveServices services;

    private enum SearchState { IDLE, SEARCHING, RESULTS, NO_RESULTS, FAILED }

    private SearchState searchState = SearchState.IDLE;
    private List<Track> searchResults = List.of();
    private String searchMessage = null;

    // flow-positioned controls (coords assigned during render)
    private ClButton searchButton;
    private ClTextField searchField;
    private ClTextField clientIdField;
    private ClButton saveClientIdButton;
    private ClButton recheckButton;
    private final java.util.Map<Track, ClButton> playButtons = new java.util.LinkedHashMap<>();

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
        // transport controls — coords assigned in render
        buttons.add(new ClButton(0, 0, 44, 22, "⏮", ClButton.Style.SECONDARY, b -> {
            MusicPlatform p = services.preferredPlatform();
            if (p != null) p.previous();
        }));
        buttons.add(new ClButton(0, 0, 60, 22, "▶", ClButton.Style.PRIMARY, b -> {
            MusicPlatform p = services.preferredPlatform();
            if (p != null) p.playPause();
        }));
        buttons.add(new ClButton(0, 0, 44, 22, "⏭", ClButton.Style.SECONDARY, b -> {
            MusicPlatform p = services.preferredPlatform();
            if (p != null) p.next();
        }));
        // platform connect/disconnect — coords assigned in render
        buttons.add(new ClButton(0, 0, 170, 22, "Hubungkan YouTube Music", ClButton.Style.PRIMARY,
                b -> {
                    services.youtube.recheck();
                    services.youtube.connect();
                    onShow();
                }));
        buttons.add(new ClButton(0, 0, 170, 22, "Putuskan YouTube Music", ClButton.Style.DANGER,
                b -> services.youtube.disconnect()));
        buttons.add(new ClButton(0, 0, 150, 22, "Hubungkan Spotify", ClButton.Style.PRIMARY,
                b -> services.spotify.connect()));
        buttons.add(new ClButton(0, 0, 150, 22, "Putuskan Spotify", ClButton.Style.DANGER,
                b -> services.spotify.disconnect()));
    }

    @Override
    public void onShow() {
        super.onShow();
        // throttled background probe — never on the render thread
        services.youtube.recheck();
    }

    @Override
    public void render(GuiGraphics g, Font font, int x, int y, int w, int h,
                       double mouseX, double mouseY, float alpha, long epochMs) {
        layout(x, y, w, h);
        scroll.render(g, alpha, () -> {
            int cx = scroll.x;
            int cy = scroll.y + 4;
            float a = alpha;

            // ===== now playing =====
            MusicPlatform platform = services.preferredPlatform();
            PlayerState state = platform == null ? null : platform.state();
            section(g, font, "Sedang Diputar — " + (platform == null ? "Tidak ada platform" : platform.displayName()),
                    cx, cy, scroll.w, a);
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

            // transport controls — positioned in flow, honest enable state
            boolean controls = platform != null && state != null && state.controlsSupported
                    && state.status != PlayerState.Status.DISCONNECTED;
            for (int i = 0; i < 3; i++) {
                ClButton b = buttons.get(i);
                b.x = cx + i * 60;
                b.y = cy;
                b.enabled = controls;
                b.render(g, font, mouseX, mouseY, a, true);
            }
            cy += 30;
            if (!controls) {
                g.drawString(font, "Kontrol aktif setelah platform terhubung.", cx, cy,
                        CraftLiveTheme.withAlpha(CraftLiveTheme.TEXT_DIM, a * 0.7f), true);
                cy += 14;
            }

            // volume
            if (platform != null && state.volumeSupported && state.status != PlayerState.Status.DISCONNECTED) {
                sliders.clear();
                ClSlider vol = new ClSlider(cx, cy, 200, "Volume", () -> (double) state.volume, platform::setVolume);
                vol.format = "%.0f%%";
                sliders.add(vol);
                vol.render(g, font, mouseX, mouseY, a);
                cy += 34;
            }

            // ===== YouTube Music =====
            section(g, font, "YouTube Music (yt-dlp)", cx, cy + 4, scroll.w, a);
            cy += 22;
            var yt = services.youtube;
            YtDlpClient.Availability avail = yt.client().availability();
            String ytStatus;
            int ytColor;
            switch (avail) {
                case AVAILABLE -> {
                    ytStatus = "Siap (yt-dlp " + yt.client().availableVersion() + ")";
                    ytColor = CraftLiveTheme.LIVE_GREEN;
                }
                case CHECKING -> {
                    ytStatus = "Memeriksa yt-dlp…";
                    ytColor = CraftLiveTheme.WARNING;
                }
                case UNKNOWN -> {
                    ytStatus = "Belum diperiksa — klik Periksa Ulang";
                    ytColor = CraftLiveTheme.WARNING;
                }
                default -> {
                    ytStatus = "yt-dlp tidak ditemukan";
                    ytColor = CraftLiveTheme.ERROR;
                }
            }
            g.drawString(font, "Status: " + ytStatus, cx, cy, CraftLiveTheme.withAlpha(ytColor, a), true);
            cy += 14;
            if (yt.state().errorMessage != null) {
                for (String line : ClRender.wrap(font, yt.state().errorMessage, scroll.w - 8)) {
                    g.drawString(font, line, cx, cy, CraftLiveTheme.withAlpha(CraftLiveTheme.ERROR, a), true);
                    cy += 11;
                }
            }
            if (avail == YtDlpClient.Availability.UNAVAILABLE) {
                g.drawString(font, "Cara: instal yt-dlp di sistem (mis. 'pip install yt-dlp') atau", cx, cy,
                        CraftLiveTheme.withAlpha(CraftLiveTheme.TEXT_DIM, a * 0.8f), true);
                cy += 11;
                g.drawString(font, "atur path binary di bagian bawah tab ini. Python + yt_dlp juga didukung.", cx, cy,
                        CraftLiveTheme.withAlpha(CraftLiveTheme.TEXT_DIM, a * 0.8f), true);
                cy += 13;
            }

            // connect / disconnect / recheck — positioned in flow
            ensureYtButtons();
            connectYt.x = cx;
            connectYt.y = cy;
            connectYt.render(g, font, mouseX, mouseY, a, true);
            disconnectYt.x = cx + 176;
            disconnectYt.y = cy;
            disconnectYt.render(g, font, mouseX, mouseY, a, true);
            if (recheckButton == null) {
                recheckButton = new ClButton(0, 0, 120, 22, "Periksa Ulang yt-dlp", ClButton.Style.SECONDARY,
                        b -> {
                            services.youtube.recheck();
                            com.alpoyy.craftlive.sound.UISounds.click();
                        });
                buttons.add(recheckButton);
            }
            recheckButton.x = cx + 352;
            recheckButton.y = cy;
            recheckButton.enabled = avail != YtDlpClient.Availability.CHECKING;
            recheckButton.render(g, font, mouseX, mouseY, a, true);
            cy += 30;

            // search
            ClTextField sf = ensureSearchField(cx, cy, scroll.w - 90);
            sf.render(g, font, mouseX, mouseY, a, epochMs);
            ClButton sb = ensureSearchButton(cx + scroll.w - 84, cy);
            sb.enabled = yt.isAvailable() && searchState != SearchState.SEARCHING;
            sb.render(g, font, mouseX, mouseY, a, true);
            cy += 30;

            if (searchState == SearchState.SEARCHING) {
                g.drawString(font, "Mencari…", cx, cy, CraftLiveTheme.withAlpha(CraftLiveTheme.WARNING, a), true);
                cy += 14;
            } else if (searchMessage != null) {
                g.drawString(font, searchMessage, cx, cy, CraftLiveTheme.withAlpha(CraftLiveTheme.TEXT_DIM, a), true);
                cy += 14;
            }
            int idx = 1;
            for (Track t : searchResults) {
                String line = idx + ". " + t.primaryText() + " — " + t.secondaryText();
                ClButton playB = ensurePlayButton(cx + scroll.w - 60, cy, t);
                playB.enabled = yt.isAvailable();
                g.drawString(font, ClRender.clip(font, line, scroll.w - 70), cx, cy,
                        CraftLiveTheme.withAlpha(CraftLiveTheme.TEXT, a), true);
                playB.render(g, font, mouseX, mouseY, a, true);
                cy += 15;
                idx++;
            }
            if (!searchResults.isEmpty()) cy += 8;

            // ===== Spotify =====
            section(g, font, "Spotify (Web API resmi)", cx, cy + 2, scroll.w, a);
            cy += 20;
            var spotify = services.spotify;
            boolean clientIdSet = !services.config.spotifyClientId.isBlank();
            String spStatus;
            int spColor;
            if (!clientIdSet) {
                spStatus = "Belum dikonfigurasi — isi Client ID lalu hubungkan";
                spColor = CraftLiveTheme.TEXT_DIM;
            } else {
                spStatus = switch (spotify.state().status) {
                    case CONNECTED -> spotify.isConfigured() ? "Terhubung ✓" : "Autorisasi diperlukan";
                    case PLAYING -> "Terhubung — memutar";
                    case PAUSED -> "Terhubung — jeda";
                    case CONNECTING -> "Menunggu otorisasi di browser…";
                    case ERROR -> "Error" + (spotify.state().errorMessage == null ? "" : " — " + spotify.state().errorMessage);
                    default -> "Tidak terhubung";
                };
                spColor = switch (spotify.state().status) {
                    case CONNECTED, PLAYING -> CraftLiveTheme.LIVE_GREEN;
                    case PAUSED, CONNECTING -> CraftLiveTheme.WARNING;
                    case ERROR -> CraftLiveTheme.ERROR;
                    default -> CraftLiveTheme.TEXT_DIM;
                };
            }
            g.drawString(font, "Status: " + spStatus, cx, cy, CraftLiveTheme.withAlpha(spColor, a), true);
            cy += 14;
            if (spotify.state().errorMessage != null && clientIdSet
                    && spotify.state().status != PlayerState.Status.ERROR) {
                g.drawString(font, ClRender.clip(font, spotify.state().errorMessage, scroll.w - 8), cx, cy,
                        CraftLiveTheme.withAlpha(CraftLiveTheme.ERROR, a), true);
                cy += 13;
            }
            g.drawString(font, "Client ID (dari dashboard Spotify developer):", cx, cy,
                    CraftLiveTheme.withAlpha(CraftLiveTheme.TEXT_DIM, a), true);
            cy += 13;
            ClTextField cField = ensureClientIdField(cx, cy, scroll.w - 90);
            cField.render(g, font, mouseX, mouseY, a, epochMs);
            ensureSaveClientIdButton(cx + scroll.w - 84, cy).render(g, font, mouseX, mouseY, a, true);
            cy += 30;
            g.drawString(font, "OAuth PKCE: browser terbuka, tidak ada password/cookies.", cx, cy,
                    CraftLiveTheme.withAlpha(CraftLiveTheme.TEXT_DIM, a * 0.8f), true);
            cy += 13;
            g.drawString(font, "Kontrol putar butuh akun Spotify Premium aktif.", cx, cy,
                    CraftLiveTheme.withAlpha(CraftLiveTheme.TEXT_DIM, a * 0.8f), true);
            cy += 18;
            ensureConnectSpotify().x = cx;
            ensureConnectSpotify().y = cy;
            ensureConnectSpotify().enabled = clientIdSet;
            ensureConnectSpotify().render(g, font, mouseX, mouseY, a, true);
            ensureDisconnectSpotify().x = cx + 156;
            ensureDisconnectSpotify().y = cy;
            ensureDisconnectSpotify().render(g, font, mouseX, mouseY, a, true);
            cy += 30;

            // yt-dlp path
            section(g, font, "Path yt-dlp", cx, cy, scroll.w, a);
            cy += 18;
            ClTextField yField = ensureYtDlpField(cx, cy, scroll.w - 90);
            yField.render(g, font, mouseX, mouseY, a, epochMs);
            ensureApplyPathButton(cx + scroll.w - 84, cy).render(g, font, mouseX, mouseY, a, true);
            cy += 28;

            contentHeight = cy - scroll.y + 8;
            scroll.setContentHeight(contentHeight);
        });
    }

    private String fmt(long ms) {
        long s = ms / 1000;
        return String.format("%d:%02d", s / 60, s % 60);
    }

    // ---- lazy controls ----

    private ClButton connectYt;
    private ClButton disconnectYt;
    private ClButton connectSpotify;
    private ClButton disconnectSpotify;
    private ClButton applyPathButton;

    private void ensureYtButtons() {
        if (connectYt == null) {
            connectYt = buttons.get(3);
            disconnectYt = buttons.get(4);
            connectSpotify = buttons.get(5);
            disconnectSpotify = buttons.get(6);
        }
    }

    private ClButton ensureConnectSpotify() {
        ensureYtButtons();
        return connectSpotify;
    }

    private ClButton ensureDisconnectSpotify() {
        ensureYtButtons();
        return disconnectSpotify;
    }

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
        if (q.isEmpty()) {
            searchState = SearchState.IDLE;
            searchMessage = "Ketik judul lagu lebih dulu.";
            return;
        }
        if (!services.youtube.isAvailable()) {
            searchState = SearchState.FAILED;
            searchMessage = services.youtube.client().unavailableMessage();
            return;
        }
        searchState = SearchState.SEARCHING;
        searchMessage = null;
        searchResults = List.of();
        services.youtube.client().search(q, 10).thenAccept(result -> {
            if (result.error != null) {
                searchState = SearchState.FAILED;
                searchMessage = result.error;
            } else if (result.tracks.isEmpty()) {
                searchState = SearchState.NO_RESULTS;
                searchMessage = "Tidak ada hasil untuk: " + q;
            } else {
                searchState = SearchState.RESULTS;
                searchMessage = result.tracks.size() + " hasil:";
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
                com.alpoyy.craftlive.sound.UISounds.confirm();
                services.notifications.push(com.alpoyy.craftlive.notify.NotificationBus.Notification.Level.SUCCESS,
                        "Spotify", "Client ID tersimpan.");
            });
            buttons.add(saveClientIdButton);
        }
        saveClientIdButton.x = x;
        saveClientIdButton.y = y;
        return saveClientIdButton;
    }

    private ClTextField ensureYtDlpField(int x, int y, int w) {
        if (ytDlpPathField == null) {
            ytDlpPathField = new ClTextField(x, y, w, 18, "kosong = deteksi otomatis (yt-dlp di PATH / python3 -m yt_dlp)", s -> {});
            ytDlpPathField.setText(services.youtube.client().binaryPath());
            fields.add(ytDlpPathField);
        }
        ytDlpPathField.x = x;
        ytDlpPathField.y = y;
        ytDlpPathField.w = w;
        return ytDlpPathField;
    }

    private ClTextField ytDlpPathField;

    private ClButton ensureApplyPathButton(int x, int y) {
        if (applyPathButton == null) {
            applyPathButton = new ClButton(x, y, 84, 18, "Terapkan", ClButton.Style.SECONDARY, b -> {
                services.youtube.setBinaryPath(ytDlpPathField.text().trim());
                services.config.ytdlpPath = ytDlpPathField.text().trim();
                services.configManager.saveAsync();
                services.youtube.recheck();
                com.alpoyy.craftlive.sound.UISounds.confirm();
                services.notifications.push(com.alpoyy.craftlive.notify.NotificationBus.Notification.Level.INFO,
                        "YouTube Music", "Path diterapkan — memeriksa ulang yt-dlp…");
            });
            buttons.add(applyPathButton);
        }
        applyPathButton.x = x;
        applyPathButton.y = y;
        return applyPathButton;
    }

    private ClButton ensurePlayButton(int x, int y, Track track) {
        ClButton b = playButtons.get(track);
        if (b == null) {
            b = new ClButton(x, y, 58, 14, "▶ Putar", ClButton.Style.SUCCESS, btn ->
                    services.youtube.play(track, err -> {
                        com.alpoyy.craftlive.sound.UISounds.click();
                        services.notifications.push(com.alpoyy.craftlive.notify.NotificationBus.Notification.Level.ERROR,
                                "YouTube Music", err);
                    }));
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
